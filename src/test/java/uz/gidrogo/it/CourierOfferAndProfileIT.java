package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Band 21 va 22: kuryer profilida soxta qiymat yo'q, taklifda offerId/status bor,
 * PREPARING sababi to'g'ri va qayta taklifdan keyin qabul qilish ishlaydi.
 */
class CourierOfferAndProfileIT extends ItBase {

    @Autowired private StringRedisTemplate redis;

    private long createCourier(long farmId, String phone) {
        return createUser("COURIER", phone, farmId, null);
    }

    private void loadVehicle(long courierId, long productId, int qty) {
        jdbc.update("INSERT INTO vehicle_stock (courier_id, product_id, quantity) VALUES (?, ?, ?)", courierId, productId, qty);
    }

    private ResponseEntity<JsonNode> checkout(String clientToken, long addressId, long productId) {
        return call(HttpMethod.POST, "/api/client/cart/checkout", clientToken, Map.of(
                "addressId", addressId,
                "paymentMethod", "CASH",
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
    }

    private long checkoutOrderId(String clientToken, long addressId, long productId) {
        ResponseEntity<JsonNode> r = checkout(clientToken, addressId, productId);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("checkout: %s", r.getBody()).isTrue();
        return r.getBody().path("data").path(0).path("id").asLong();
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    @Test
    @DisplayName("Yangi kuryer profili va dashboardida soxta qiymat yo'q, ratingCount va shiftType bor")
    void newCourierHasNoStubValues() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        String phone = uniquePhone();
        long courierId = createCourier(farmId, phone);
        // Xom INSERT V5 dagi DEFAULT 40 ni oladi; Hibernate orqali yaratilgan kuryerda ustun NULL
        jdbc.update("UPDATE users SET max_capacity = NULL WHERE id = ?", courierId);
        String token = loginToken(phone, PASSWORD);

        JsonNode profile = get("/api/courier/profile", token).getBody().path("data");
        assertThat(profile.path("rating").isNull()).isTrue();
        assertThat(profile.path("ratingCount").asInt()).isZero();
        assertThat(profile.path("vehicleModel").isNull()).isTrue();
        assertThat(profile.path("vehiclePlateNumber").isNull()).isTrue();
        assertThat(profile.path("maxCapacity").isNull()).isTrue();
        assertThat(profile.path("driverLicenseNumber").isNull()).isTrue();
        assertThat(profile.path("passportSerial").isNull()).isTrue();
        assertThat(profile.path("vehicle").path("model").isNull()).isTrue();
        assertThat(profile.path("shiftType").asText()).isEqualTo("DAY");
        assertThat(profile.path("shiftTypeLabel").asText()).isEqualTo("Kunduzgi");

        JsonNode dashboard = get("/api/courier/dashboard", token).getBody().path("data");
        assertThat(dashboard.path("targetOrdersCount").isNull()).isTrue();
        assertThat(dashboard.path("targetRevenue").isNull()).isTrue();
    }

    @Test
    @DisplayName("Kuryer telefonini o'zgartira olmaydi, bo'sh satr mashina maydonini tozalaydi")
    void phoneIsImmutableAndBlankClearsField() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        String phone = uniquePhone();
        long courierId = createCourier(farmId, phone);
        String token = loginToken(phone, PASSWORD);

        ResponseEntity<JsonNode> set = call(HttpMethod.PUT, "/api/courier/profile", token,
                Map.of("phone", uniquePhone(), "vehiclePlateNumber", "01 B 123 CD"));
        assertThat(set.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, courierId)).isEqualTo(phone);
        assertThat(get("/api/courier/profile", token).getBody().path("data").path("vehiclePlateNumber").asText())
                .isEqualTo("01 B 123 CD");

        call(HttpMethod.PUT, "/api/courier/profile", token, Map.of("vehiclePlateNumber", ""));
        assertThat(get("/api/courier/profile", token).getBody().path("data").path("vehiclePlateNumber").isNull()).isTrue();
    }

    @Test
    @DisplayName("Buyurtmasiz lokatsiya qabul qilinadi va lastSeenAt Z bilan qaytadi")
    void locationWithoutActiveOrderUpdatesLastSeenAt() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        String phone = uniquePhone();
        createCourier(farmId, phone);
        String token = loginToken(phone, PASSWORD);

        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/courier/location", token,
                Map.of("latitude", 41.30, "longitude", 69.24, "bearing", 87.5));
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(200);

        String lastSeenAt = get("/api/courier/profile", token).getBody().path("data").path("lastSeenAt").asText();
        assertThat(lastSeenAt).endsWith("Z");
        assertThat(Instant.parse(lastSeenAt)).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    @DisplayName("GPS'siz online kuryerga taklif boradi, offerId va status doim bor, qayta taklif qabul qilinadi")
    void offerReachesCourierWithoutGpsAndReofferCanBeAccepted() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);
        long productId = createProduct(farmId, new BigDecimal("10000"));
        String courierPhone = uniquePhone();
        long courierId = createCourier(farmId, courierPhone);
        loadVehicle(courierId, productId, 10);
        String courierToken = loginToken(courierPhone, PASSWORD);
        call(HttpMethod.PATCH, "/api/courier/status", courierToken, Map.of("online", true));

        long orderId = checkoutOrderId(loginToken((String) client.get("phone"), PASSWORD),
                (Long) client.get("addressId"), productId);
        assertThat(orderStatus(orderId)).isEqualTo("SEARCHING");

        JsonNode offers = get("/api/courier/orders/offers", courierToken).getBody().path("data");
        assertThat(offers).hasSize(1);
        assertThat(offers.path(0).path("offerId").asLong()).isPositive();
        assertThat(offers.path(0).path("status").asText()).isEqualTo("PENDING");

        // Cooldown'dan keyingi qayta taklif: bitta buyurtma+kuryer uchun ikkinchi yozuv
        jdbc.update("UPDATE courier_offers SET status = 'EXPIRED' WHERE order_id = ?", orderId);
        jdbc.update("""
                INSERT INTO courier_offers (order_id, courier_id, status, expires_at, created_at)
                VALUES (?, ?, 'PENDING', now() + interval '30 seconds', now())
                """, orderId, courierId);

        ResponseEntity<JsonNode> accepted = call(HttpMethod.POST, "/api/courier/orders/" + orderId + "/accept", courierToken, null);
        assertThat(accepted.getStatusCode().value()).as("%s", accepted.getBody()).isEqualTo(200);
        assertThat(orderStatus(orderId)).isEqualTo("ASSIGNED");

        redis.delete("courier:online:" + courierId);
    }

    @Test
    @DisplayName("Kuryer biriktirilmagan buyurtma bekor qilinsa ochiq taklif yopiladi")
    void cancellingOrderClosesPendingOffer() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);
        long productId = createProduct(farmId, new BigDecimal("10000"));
        String courierPhone = uniquePhone();
        long courierId = createCourier(farmId, courierPhone);
        loadVehicle(courierId, productId, 10);
        String courierToken = loginToken(courierPhone, PASSWORD);
        call(HttpMethod.PATCH, "/api/courier/status", courierToken, Map.of("online", true));

        String clientToken = loginToken((String) client.get("phone"), PASSWORD);
        long orderId = checkoutOrderId(clientToken, (Long) client.get("addressId"), productId);

        ResponseEntity<JsonNode> cancelled = call(HttpMethod.POST, "/api/client/orders/" + orderId + "/cancel", clientToken, null);
        assertThat(cancelled.getStatusCode().value()).as("%s", cancelled.getBody()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM courier_offers WHERE order_id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");

        redis.delete("courier:online:" + courierId);
    }

    @Test
    @DisplayName("PREPARING sababi aniq, qayta urinish o'zgarmas tarixga yozilmaydi")
    void preparingReasonIsAccurate() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);
        long productId = createProduct(farmId, new BigDecimal("10000"));
        String clientToken = loginToken((String) client.get("phone"), PASSWORD);

        // Online kuryer yo'q
        long noCourier = checkoutOrderId(clientToken, (Long) client.get("addressId"), productId);
        assertThat(orderStatus(noCourier)).isEqualTo("PREPARING");
        assertThat(jdbc.queryForObject("SELECT preparing_reason FROM orders WHERE id = ?", String.class, noCourier))
                .isEqualTo("NO_FREE_COURIER");

        // Online kuryer bor, lekin mashinada zaxira yo'q
        String courierPhone = uniquePhone();
        long courierId = createCourier(farmId, courierPhone);
        call(HttpMethod.PATCH, "/api/courier/status", loginToken(courierPhone, PASSWORD), Map.of("online", true));
        assertThat(jdbc.queryForObject("SELECT preparing_reason FROM orders WHERE id = ?", String.class, noCourier))
                .isEqualTo("NO_STOCK");

        Integer preparingRows = jdbc.queryForObject(
                "SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'PREPARING'", Integer.class, noCourier);
        assertThat(preparingRows).isEqualTo(1);

        redis.delete("courier:online:" + courierId);
    }
}
