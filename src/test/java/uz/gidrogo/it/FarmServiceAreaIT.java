package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Band 23: ferma ro'yxati xizmat hududi bo'yicha filtrlanadi, server tomonda 409 FARM_NOT_SERVING_ADDRESS. */
class FarmServiceAreaIT extends ItBase {

    @Autowired private StringRedisTemplate redis;

    // Qarshi atrofida, boshqa fermalardan uzoq nuqta (IT bazasidagi boshqa fermalar Toshkentda)
    private static final double LAT = 38.86, LON = 65.79;

    private long farmAt(Double lat, Double lon) {
        long farmId = (Long) createFarmViaApi().get("farmId");
        jdbc.update("UPDATE farms SET latitude = ?, longitude = ? WHERE id = ?", lat, lon, farmId);
        return farmId;
    }

    private void polygon(long farmId, String json, boolean active) {
        jdbc.update("INSERT INTO service_regions (farm_id, region_name, polygon_json, is_active, created_at) VALUES (?, 'Test', ?, ?, now())",
                farmId, json, active);
    }

    private List<Long> ids(JsonNode data, String idPath) {
        List<Long> out = new ArrayList<>();
        data.forEach(n -> out.add(idPath == null ? n.path("id").asLong() : n.path(idPath).path("id").asLong()));
        return out;
    }

    @Test
    @DisplayName("Ro'yxat: radius ichidagi va poligon ichidagi ferma bor, uzoq va koordinatasiz ferma yo'q")
    void farmsAreFilteredByServiceArea() {
        long near = farmAt(38.87, 65.80);                 // ~1.4 km, poligon yo'q → radius
        long far = farmAt(39.654, 66.975);                // Samarqand, ~140 km
        long noCoords = farmAt(null, null);               // koordinata ham, poligon ham yo'q
        long polygonFar = farmAt(41.31, 69.28);           // Toshkentda, lekin poligoni nuqtani qamraydi (GeoJSON [lon, lat])
        polygon(polygonFar, "{\"type\":\"Polygon\",\"coordinates\":[[[65.7,38.8],[65.9,38.8],[65.9,38.95],[65.7,38.95],[65.7,38.8]]]}", true);
        long inactivePolygon = farmAt(38.865, 65.795);    // yaqin, lekin yagona poligoni nofaol → radius ishlaydi
        polygon(inactivePolygon, "[[41.0,69.0],[41.0,69.5],[41.5,69.5]]", false);
        long polygonExcludes = farmAt(38.861, 65.791);    // yaqin, lekin faol poligoni nuqtani qamramaydi
        polygon(polygonExcludes, "[[41.0,69.0],[41.0,69.5],[41.5,69.5]]", true);

        JsonNode farms = get("/api/client/farms?status=ACTIVE&size=100&lat=" + LAT + "&lon=" + LON, null).getBody().path("data");
        assertThat(ids(farms, null)).contains(near, polygonFar, inactivePolygon)
                .doesNotContain(far, noCoords, polygonExcludes);
        farms.forEach(f -> assertThat(f.path("distanceKm").isNumber()).isTrue());

        JsonNode nearby = get("/api/client/farms/nearby?lat=" + LAT + "&lon=" + LON, null).getBody().path("data");
        assertThat(ids(nearby, "farm")).contains(near).doesNotContain(far, noCoords, polygonExcludes);
        nearby.forEach(n -> assertThat(n.path("distanceMeters").asDouble()).isPositive());
        assertThat(nearby.path(0).path("farm").has("city")).isTrue();

        ResponseEntity<JsonNode> nukus = get("/api/client/farms?lat=43.0&lon=59.0", null);
        assertThat(nukus.getStatusCode().value()).isEqualTo(200);
        assertThat(nukus.getBody().path("data")).isEmpty();
    }

    @Test
    @DisplayName("Telegram ro'yxatdan o'tish: xizmat qilmaydigan ferma → 409 FARM_NOT_SERVING_ADDRESS")
    void registerRejectsFarmNotServingAddress() {
        long far = farmAt(39.654, 66.975);
        String tempToken = UUID.randomUUID().toString();
        redis.opsForValue().set("tg:session:" + tempToken,
                "{\"telegramId\":" + (System.nanoTime() % 1_000_000_000L) + ",\"phone\":\"" + uniquePhone() + "\"}");

        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/client/telegram/register", null, Map.of(
                "farmId", far, "address", "Qarshi", "latitude", LAT, "longitude", LON, "tempToken", tempToken));
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(409);
        assertThat(r.getBody().path("code").asText()).isEqualTo("FARM_NOT_SERVING_ADDRESS");
    }

    @Test
    @DisplayName("Manzil qo'shish va buyurtma: mijoz fermasi manzilga xizmat qilmasa → 409")
    void addressAndCheckoutAreProtected() {
        long farmId = farmAt(41.30, 69.24);               // createClient manzili 41.31, 69.25 — xizmat qiladi
        Map<String, Object> client = createClient(farmId);
        String token = loginToken((String) client.get("phone"), PASSWORD);

        ResponseEntity<JsonNode> farAddress = call(HttpMethod.POST, "/api/client/addresses", token,
                Map.of("address", "Samarqand", "latitude", 39.654, "longitude", 66.975));
        assertThat(farAddress.getStatusCode().value()).isEqualTo(409);
        assertThat(farAddress.getBody().path("code").asText()).isEqualTo("FARM_NOT_SERVING_ADDRESS");

        long productId = createProduct(farmId, new BigDecimal("10000"));
        long outside = jdbc.queryForObject("""
                INSERT INTO client_addresses (client_id, label, address, latitude, longitude, is_default)
                VALUES (?, 'Uzoq', 'Nukus', 43.0, 59.0, false) RETURNING id
                """, Long.class, client.get("clientId"));
        ResponseEntity<JsonNode> order = call(HttpMethod.POST, "/api/client/cart/checkout", token, Map.of(
                "addressId", outside, "paymentMethod", "CASH",
                "items", List.of(Map.of("productId", productId, "quantity", 1))));
        assertThat(order.getStatusCode().value()).as("%s", order.getBody()).isEqualTo(409);
        assertThat(order.getBody().path("code").asText()).isEqualTo("FARM_NOT_SERVING_ADDRESS");
    }
}
