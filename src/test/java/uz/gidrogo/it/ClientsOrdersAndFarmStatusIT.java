package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import uz.gidrogo.common.TashkentTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-08 (mijozlar), BE-09 (buyurtmalar), BE-14 (ferma holati), BE-17 (ferma yaratish validatsiyasi). */
class ClientsOrdersAndFarmStatusIT extends ItBase {

    private Map<String, Object> checkoutBody(long addressId, long productId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("paymentMethod", "CASH");
        body.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        return body;
    }

    @Test
    @DisplayName("BE-14: faol buyurtma bor fermani bloklash → yangi checkout 409 FARM_BLOCKED, ochiq ro'yxatda yo'q, mavjud buyurtma saqlanadi")
    void blockedFarmRejectsNewOrders() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);
        long productId = createProduct(farmId, new BigDecimal("12000"));
        long activeOrder = createOrder(farmId, (Long) client.get("clientId"), "ON_THE_WAY", new BigDecimal("12000"), null, Instant.now(), null);
        String clientToken = loginToken((String) client.get("phone"), PASSWORD);

        ResponseEntity<JsonNode> block = call(HttpMethod.PATCH, "/api/superadmin/farms/" + farmId + "/status", superAdminToken(),
                Map.of("status", "BLOCKED", "reason", "Test bloklash"));
        assertThat(block.getStatusCode().value()).as("%s", block.getBody()).isEqualTo(200);

        ResponseEntity<JsonNode> checkout = call(HttpMethod.POST, "/api/client/cart/checkout", clientToken,
                checkoutBody((Long) client.get("addressId"), productId));
        assertThat(checkout.getStatusCode().value()).isEqualTo(409);
        assertThat(checkout.getBody().path("code").asText()).isEqualTo("FARM_BLOCKED");

        for (String q : new String[]{"", "?status=BLOCKED", "?status=ALL"}) {
            JsonNode list = get("/api/client/farms" + q, null).getBody().path("data");
            assertThat(list.findValuesAsText("id")).doesNotContain(String.valueOf(farmId));
        }

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, activeOrder)).isEqualTo("ON_THE_WAY");
        String meta = jdbc.queryForObject("SELECT metadata FROM superadmin_audit_log WHERE action = 'FARM_BLOCKED' AND entity_id = ?",
                String.class, farmId);
        assertThat(meta).contains("\"old\":{\"status\":\"ACTIVE\"").contains("BLOCKED");

        call(HttpMethod.PATCH, "/api/superadmin/farms/" + farmId + "/status", superAdminToken(), Map.of("status", "INACTIVE"));
        ResponseEntity<JsonNode> inactive = call(HttpMethod.POST, "/api/client/cart/checkout", clientToken,
                checkoutBody((Long) client.get("addressId"), productId));
        assertThat(inactive.getBody().path("code").asText()).isEqualTo("FARM_INACTIVE");
    }

    @Test
    @DisplayName("BE-08: bloklangan mijoz checkout → 403 CLIENT_BLOCKED, login → 403; audit CLIENT_BLOCKED")
    void blockedClient() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);
        long productId = createProduct(farmId, new BigDecimal("12000"));
        String clientToken = loginToken((String) client.get("phone"), PASSWORD);

        ResponseEntity<JsonNode> block = call(HttpMethod.PATCH, "/api/superadmin/clients/" + client.get("clientId") + "/status",
                superAdminToken(), Map.of("status", "BLOCKED", "reason", "Test"));
        assertThat(block.getStatusCode().value()).as("%s", block.getBody()).isEqualTo(200);

        ResponseEntity<JsonNode> checkout = call(HttpMethod.POST, "/api/client/cart/checkout", clientToken,
                checkoutBody((Long) client.get("addressId"), productId));
        assertThat(checkout.getStatusCode().value()).isEqualTo(403);
        assertThat(checkout.getBody().path("code").asText()).isEqualTo("CLIENT_BLOCKED");

        ResponseEntity<JsonNode> loginBlocked = login((String) client.get("phone"), PASSWORD);
        assertThat(loginBlocked.getStatusCode().value()).isEqualTo(403);
        assertThat(loginBlocked.getBody().path("code").asText()).isEqualTo("CLIENT_BLOCKED");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM superadmin_audit_log WHERE action = 'CLIENT_BLOCKED' AND entity_id = ?",
                Long.class, client.get("clientId"))).isEqualTo(1L);
    }

    @Test
    @DisplayName("BE-08: o'chirish — faol buyurtma → 409, idish balansi → 409, aks holda soft delete; ro'yxat va qidiruv")
    void deleteRulesAndList() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> c1 = createClient(farmId);
        long orderId = createOrder(farmId, (Long) c1.get("clientId"), "SEARCHING", new BigDecimal("1000"), null, Instant.now(), null);

        ResponseEntity<JsonNode> active = call(HttpMethod.DELETE, "/api/superadmin/clients/" + c1.get("clientId"), superAdminToken(), null);
        assertThat(active.getStatusCode().value()).isEqualTo(409);
        assertThat(active.getBody().path("code").asText()).isEqualTo("CLIENT_HAS_ACTIVE_ORDERS");

        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", orderId);
        long productId = createProduct(farmId, new BigDecimal("1000"));
        jdbc.update("INSERT INTO client_bottle_balances (client_id, farm_id, product_id, balance) VALUES (?, ?, ?, 2)",
                c1.get("clientId"), farmId, productId);
        ResponseEntity<JsonNode> bottles = call(HttpMethod.DELETE, "/api/superadmin/clients/" + c1.get("clientId"), superAdminToken(), null);
        assertThat(bottles.getBody().path("code").asText()).isEqualTo("CLIENT_HAS_BOTTLE_BALANCE");

        JsonNode detail = get("/api/superadmin/clients/" + c1.get("clientId"), superAdminToken()).getBody().path("data");
        assertThat(detail.path("totalBottleBalance").asInt()).isEqualTo(2);
        assertThat(detail.path("ordersCount").asLong()).isEqualTo(1);

        Map<String, Object> c2 = createClient(farmId);
        String phoneDigits = ((String) c2.get("phone")).substring(4); // probelsiz qidiruv
        JsonNode found = get("/api/superadmin/clients?search=" + phoneDigits, superAdminToken()).getBody().path("data");
        assertThat(found.path("totalElements").asLong()).isEqualTo(1);

        assertThat(call(HttpMethod.DELETE, "/api/superadmin/clients/" + c2.get("clientId"), superAdminToken(), null)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, c2.get("userId"))).isEqualTo("DELETED");
        assertThat(get("/api/superadmin/clients/" + c2.get("clientId"), superAdminToken()).getStatusCode().value()).isEqualTo(404);

        JsonNode byFarm = get("/api/superadmin/clients?farmId=" + farmId, superAdminToken()).getBody().path("data");
        assertThat(byFarm.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("BE-09: farmId + status=CANCELLED + oxirgi 30 kun = daily-dynamics cancelledOrders; tafsilotda status tarixi")
    void ordersFilterMatchesDynamics() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        long clientId = (Long) createClient(farmId).get("clientId");
        Instant now = Instant.now();
        createOrder(farmId, clientId, "CANCELLED", new BigDecimal("1000"), null, now.minus(2, ChronoUnit.DAYS), null);
        createOrder(farmId, clientId, "CANCELLED", new BigDecimal("1000"), null, now.minus(10, ChronoUnit.DAYS), null);
        createOrder(farmId, clientId, "CANCELLED", new BigDecimal("1000"), null, now.minus(45, ChronoUnit.DAYS), null);
        long completed = createOrder(farmId, clientId, "COMPLETED", new BigDecimal("1000"), null, now, now);
        jdbc.update("INSERT INTO order_status_history (order_id, from_status, to_status, changed_at) VALUES (?, NULL, 'NEW', now())", completed);

        String from = TashkentTime.today().minusDays(29).toString();
        JsonNode list = get("/api/superadmin/orders?farmId=" + farmId + "&status=CANCELLED&from=" + from, superAdminToken())
                .getBody().path("data");
        JsonNode dyn = get("/api/superadmin/orders/daily-dynamics?days=30&farmId=" + farmId, superAdminToken()).getBody().path("data");
        assertThat(list.path("totalElements").asLong()).isEqualTo(2).isEqualTo(dyn.path("cancelledOrders").asLong());

        JsonNode multi = get("/api/superadmin/orders?farmId=" + farmId + "&status=CANCELLED&status=COMPLETED", superAdminToken())
                .getBody().path("data");
        assertThat(multi.path("totalElements").asLong()).isEqualTo(4);

        JsonNode d = get("/api/superadmin/orders/" + completed, superAdminToken()).getBody().path("data");
        assertThat(d.path("statusHistory").get(0).path("status").asText()).isEqualTo("NEW");
        assertThat(d.path("statusHistory").get(0).path("actorRole").asText()).isEqualTo("SYSTEM");
        assertThat(d.path("farm").path("id").asLong()).isEqualTo(farmId);

        // status tarixi o'zgarmaydi (V12 trigger)
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("UPDATE order_status_history SET to_status = 'CANCELLED' WHERE order_id = ?", completed))
                .hasMessageContaining("taqiqlangan");
    }

    @Test
    @DisplayName("BE-17: majburiy maydonlar va O'zbekiston koordinatalari; boss telefoni noyob → 409; zaif parol → 422")
    void farmCreateValidation() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", uniqueName("Farm"));
        body.put("phone", uniquePhone());
        body.put("address", "Toshkent");
        body.put("latitude", 50.0);
        body.put("longitude", 69.0);
        body.put("bossFullName", "TEST Boss");
        body.put("bossPhone", uniquePhone());
        body.put("bossPassword", PASSWORD);
        ResponseEntity<JsonNode> badLat = call(HttpMethod.POST, "/api/superadmin/farms", superAdminToken(), body);
        assertThat(badLat.getStatusCode().value()).isEqualTo(400);
        assertThat(badLat.getBody().path("fieldErrors").findValuesAsText("field")).contains("latitude");

        body.put("latitude", 41.3);
        body.remove("address");
        assertThat(call(HttpMethod.POST, "/api/superadmin/farms", superAdminToken(), body).getStatusCode().value()).isEqualTo(400);

        body.put("address", "Toshkent");
        body.put("bossPassword", "short");
        ResponseEntity<JsonNode> weak = call(HttpMethod.POST, "/api/superadmin/farms", superAdminToken(), body);
        assertThat(weak.getStatusCode().value()).isEqualTo(422);

        String existing = (String) createFarmViaApi().get("bossPhone");
        body.put("bossPassword", PASSWORD);
        body.put("bossPhone", existing);
        ResponseEntity<JsonNode> dup = call(HttpMethod.POST, "/api/superadmin/farms", superAdminToken(), body);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(dup.getBody().path("code").asText()).isEqualTo("PHONE_ALREADY_EXISTS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM farms WHERE name = ?", Long.class, body.get("name"))).isZero();
    }

    @Test
    @DisplayName("BE-10: dashboard — active + completed + cancelled + returned = totalOrders; growth null mumkin")
    void dashboardInvariant() {
        JsonNode d = get("/api/superadmin/dashboard", superAdminToken()).getBody().path("data");
        assertThat(d.path("activeOrders").asLong() + d.path("completedOrders").asLong()
                + d.path("cancelledOrders").asLong() + d.path("returnedOrders").asLong())
                .isEqualTo(d.path("totalOrders").asLong());
        assertThat(d.path("totalFarms").asLong()).isEqualTo(
                d.path("activeFarms").asLong() + d.path("blockedFarms").asLong() + d.path("inactiveFarms").asLong());
        assertThat(d.has("growth")).isTrue();
        assertThat(d.toString()).doesNotContain("totalRevenue");

        JsonNode growth = get("/api/superadmin/statistics/growth?granularity=MONTH", superAdminToken()).getBody().path("data");
        assertThat(growth.size()).isEqualTo(12);
        JsonNode byStatus = get("/api/superadmin/statistics/orders-by-status", superAdminToken()).getBody().path("data");
        assertThat(byStatus.path("statuses").size()).isEqualTo(uz.gidrogo.modules.order.OrderStatus.values().length);
        JsonNode regions = get("/api/superadmin/statistics/regions", superAdminToken()).getBody().path("data");
        long sum = 0;
        for (JsonNode reg : regions.path("regions")) sum += reg.path("totalFarms").asLong();
        assertThat(sum).isEqualTo(regions.path("totalFarms").asLong());
    }
}
