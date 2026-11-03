package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Feedback (mijoz/kuryer → manager), kassa topshirish (Boss yoki Manager tasdig'i) va manager kuryer biriktirishi. */
class FeedbackCashAssignIT extends ItBase {

    private record Farm(long farmId, String bossToken, String managerToken) {}

    private Farm farm() {
        Map<String, Object> f = createFarmViaApi();
        long farmId = (Long) f.get("farmId");
        jdbc.update("UPDATE users SET activation_status = 'APPROVED' WHERE id = ?", f.get("bossId"));
        String managerPhone = uniquePhone();
        createUser("MANAGER", managerPhone, farmId, null);
        return new Farm(farmId, loginToken((String) f.get("bossPhone"), PASSWORD), loginToken(managerPhone, PASSWORD));
    }

    private String courierToken(long farmId, long[] idOut) {
        String phone = uniquePhone();
        idOut[0] = createUser("COURIER", phone, farmId, null);
        return loginToken(phone, PASSWORD);
    }

    @Test
    @DisplayName("Feedback: mijoz va kuryer yuboradi, manager ro'yxatda ko'radi va holatini o'zgartiradi; boshqa ferma ko'rmaydi")
    void feedbackFlow() {
        Farm farm = farm();
        Map<String, Object> client = createClient(farm.farmId());
        String clientToken = loginToken((String) client.get("phone"), PASSWORD);
        long[] courierId = new long[1];
        String courierToken = courierToken(farm.farmId(), courierId);

        ResponseEntity<JsonNode> fromClient = call(HttpMethod.POST, "/api/client/feedback", clientToken,
                Map.of("category", "delivery", "message", "Suv kech keldi"));
        assertThat(fromClient.getStatusCode().value()).as("%s", fromClient.getBody()).isEqualTo(200);
        long clientFeedbackId = fromClient.getBody().path("data").path("id").asLong();
        assertThat(fromClient.getBody().path("data").path("category").asText()).isEqualTo("DELIVERY");

        ResponseEntity<JsonNode> fromCourier = call(HttpMethod.POST, "/api/courier/feedback", courierToken,
                Map.of("message", "Manzil xaritada noto'g'ri"));
        assertThat(fromCourier.getStatusCode().value()).as("%s", fromCourier.getBody()).isEqualTo(200);

        JsonNode list = get("/api/manager/feedback?status=NEW", farm.managerToken()).getBody().path("data");
        assertThat(list.path("totalElements").asLong()).isEqualTo(2);
        assertThat(list.path("content").findValuesAsText("source")).containsExactlyInAnyOrder("CLIENT", "COURIER");
        assertThat(list.path("content").findValuesAsText("authorPhone")).contains((String) client.get("phone"));

        assertThat(get("/api/manager/feedback?source=COURIER", farm.bossToken()).getBody().path("data").path("totalElements").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> resolved = call(HttpMethod.PATCH, "/api/manager/feedback/" + clientFeedbackId, farm.managerToken(),
                Map.of("status", "RESOLVED", "managerNote", "Kechirasiz, kuryer bilan gaplashdik"));
        assertThat(resolved.getStatusCode().value()).isEqualTo(200);
        assertThat(resolved.getBody().path("data").path("handledByName").isNull()).isFalse();

        JsonNode summary = get("/api/manager/feedback/summary", farm.managerToken()).getBody().path("data");
        assertThat(summary.path("newCount").asLong()).isEqualTo(1);
        assertThat(summary.path("resolvedCount").asLong()).isEqualTo(1);

        JsonNode mine = get("/api/client/feedback", clientToken).getBody().path("data").path("content");
        assertThat(mine.path(0).path("managerNote").asText()).contains("Kechirasiz");

        Farm other = farm();
        assertThat(get("/api/manager/feedback/" + clientFeedbackId, other.managerToken()).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.PATCH, "/api/manager/feedback/" + clientFeedbackId, other.managerToken(),
                Map.of("status", "SEEN")).getStatusCode().value()).isEqualTo(404);

        // Boshqa mijozning buyurtmasiga feedback bog'lab bo'lmaydi
        Map<String, Object> stranger = createClient(farm.farmId());
        long strangerOrder = createOrder(farm.farmId(), (Long) stranger.get("clientId"), "COMPLETED",
                new BigDecimal("10000"), null, Instant.now(), Instant.now());
        assertThat(call(HttpMethod.POST, "/api/client/feedback", clientToken,
                Map.of("message", "x", "orderId", strangerOrder)).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.POST, "/api/client/feedback", clientToken,
                Map.of("message", "x", "category", "NOPE")).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("Kassa: kuryer so'rovi Boss ro'yxatida, Boss tasdiqlaydi; boshqa ferma manageri tasdiqlay olmaydi")
    void cashHandoverGoesToBoss() {
        Farm farm = farm();
        long[] courierId = new long[1];
        String courierToken = courierToken(farm.farmId(), courierId);
        jdbc.update("INSERT INTO cash_ledger (courier_id, farm_id, type, amount, created_at) VALUES (?, ?, 'COLLECTED', 50000, now())",
                courierId[0], farm.farmId());

        ResponseEntity<JsonNode> request = call(HttpMethod.POST, "/api/courier/cash/handovers", courierToken,
                Map.of("declaredAmount", 50000));
        assertThat(request.getStatusCode().value()).as("%s", request.getBody()).isEqualTo(200);
        long handoverId = request.getBody().path("data").path("id").asLong();

        JsonNode pending = get("/api/boss/cash/handovers?status=PENDING", farm.bossToken()).getBody().path("data");
        assertThat(pending.findValuesAsText("id")).contains(String.valueOf(handoverId));
        JsonNode summary = get("/api/boss/cash/summary", farm.bossToken()).getBody().path("data");
        assertThat(summary.path("pendingHandoversCount").asInt()).isEqualTo(1);
        assertThat(summary.path("pendingHandoversAmount").decimalValue()).isEqualByComparingTo("50000");

        Farm other = farm();
        assertThat(call(HttpMethod.POST, "/api/manager/cash/handovers/" + handoverId + "/confirm", other.managerToken(),
                Map.of("confirmedAmount", 50000)).getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> confirmed = call(HttpMethod.POST, "/api/boss/cash/handovers/" + handoverId + "/confirm", farm.bossToken(),
                Map.of("confirmedAmount", 50000));
        assertThat(confirmed.getStatusCode().value()).as("%s", confirmed.getBody()).isEqualTo(200);
        assertThat(confirmed.getBody().path("data").path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getBody().path("data").path("resolvedByRole").asText()).isEqualTo("BOSS");
        assertThat(get("/api/courier/cash", courierToken).getBody().path("data").path("balance").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Kassa: Manager ham tasdiqlay oladi (resolvedByRole=MANAGER)")
    void managerCanStillConfirm() {
        Farm farm = farm();
        long[] courierId = new long[1];
        String courierToken = courierToken(farm.farmId(), courierId);
        jdbc.update("INSERT INTO cash_ledger (courier_id, farm_id, type, amount, created_at) VALUES (?, ?, 'COLLECTED', 20000, now())",
                courierId[0], farm.farmId());
        long handoverId = call(HttpMethod.POST, "/api/courier/cash/handovers", courierToken, Map.of("declaredAmount", 20000))
                .getBody().path("data").path("id").asLong();

        ResponseEntity<JsonNode> confirmed = call(HttpMethod.POST, "/api/manager/cash/handovers/" + handoverId + "/confirm",
                farm.managerToken(), Map.of("confirmedAmount", 20000));
        assertThat(confirmed.getStatusCode().value()).as("%s", confirmed.getBody()).isEqualTo(200);
        assertThat(confirmed.getBody().path("data").path("resolvedByRole").asText()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("Manager kuryer biriktiradi: o'z fermasi, faqat kuryer, yopilgan buyurtmaga emas; ochiq taklif yopiladi")
    void managerAssignsCourier() {
        Farm farm = farm();
        Map<String, Object> client = createClient(farm.farmId());
        long[] courierId = new long[1];
        courierToken(farm.farmId(), courierId);
        long[] rivalId = new long[1];
        courierToken(farm.farmId(), rivalId);

        long orderId = createOrder(farm.farmId(), (Long) client.get("clientId"), "SEARCHING", new BigDecimal("10000"), null, Instant.now(), null);
        jdbc.update("INSERT INTO courier_offers (order_id, courier_id, status, expires_at, created_at) VALUES (?, ?, 'PENDING', now() + interval '30 seconds', now())",
                orderId, rivalId[0]);

        Farm other = farm();
        assertThat(call(HttpMethod.POST, "/api/manager/orders/" + orderId + "/assign", other.managerToken(),
                Map.of("courierId", courierId[0])).getStatusCode().value()).isEqualTo(404);

        long clientUserId = (Long) client.get("userId");
        assertThat(call(HttpMethod.POST, "/api/manager/orders/" + orderId + "/assign", farm.managerToken(),
                Map.of("courierId", clientUserId)).getStatusCode().value()).isEqualTo(400);

        ResponseEntity<JsonNode> assigned = call(HttpMethod.POST, "/api/manager/orders/" + orderId + "/assign", farm.managerToken(),
                Map.of("courierId", courierId[0]));
        assertThat(assigned.getStatusCode().value()).as("%s", assigned.getBody()).isEqualTo(200);
        assertThat(assigned.getBody().path("data").path("status").asText()).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT status FROM courier_offers WHERE order_id = ? AND courier_id = ?", String.class, orderId, rivalId[0]))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_notifications WHERE courier_id = ? AND type = 'ORDER_ASSIGNED'", Integer.class, courierId[0]))
                .isEqualTo(1);

        long completed = createOrder(farm.farmId(), (Long) client.get("clientId"), "COMPLETED", new BigDecimal("10000"), null, Instant.now(), Instant.now());
        ResponseEntity<JsonNode> closed = call(HttpMethod.POST, "/api/manager/orders/" + completed + "/assign", farm.managerToken(),
                Map.of("courierId", courierId[0]));
        assertThat(closed.getStatusCode().value()).isEqualTo(409);
        assertThat(closed.getBody().path("code").asText()).isEqualTo("ORDER_NOT_ASSIGNABLE");
    }
}
