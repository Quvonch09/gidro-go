package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Band 24 qabul mezonlari: manager bildirishnomasi, dashboard soni, kuryer tarixi va natijasi, bekor qilish, boshqa ferma. */
class CashHandoverNotificationsIT extends ItBase {

    private record Farm(long farmId, String bossToken, String managerToken, String courierToken, long courierId) {}

    private Farm farm() {
        Map<String, Object> f = createFarmViaApi();
        long farmId = (Long) f.get("farmId");
        jdbc.update("UPDATE users SET activation_status = 'APPROVED' WHERE id = ?", f.get("bossId"));
        String managerPhone = uniquePhone();
        createUser("MANAGER", managerPhone, farmId, null);
        String courierPhone = uniquePhone();
        long courierId = createUser("COURIER", courierPhone, farmId, null);
        jdbc.update("INSERT INTO cash_ledger (courier_id, farm_id, type, amount, created_at) VALUES (?, ?, 'COLLECTED', 50000, now())", courierId, farmId);
        return new Farm(farmId, loginToken((String) f.get("bossPhone"), PASSWORD), loginToken(managerPhone, PASSWORD),
                loginToken(courierPhone, PASSWORD), courierId);
    }

    private long request(Farm f, int amount) {
        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/courier/cash/handovers", f.courierToken(), Map.of("declaredAmount", amount));
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(200);
        return r.getBody().path("data").path("id").asLong();
    }

    private long unread(String token, String base) {
        return get(base + "/notifications/unread-count", token).getBody().path("data").path("unreadCount").asLong();
    }

    @Test
    @DisplayName("1-2: so'rov → Manager va Boss bildirishnomasi, dashboard pendingHandoversCount; 3: rad → kuryer tarixi va bildirishnomasi")
    void requestNotifiesStaffAndRejectReachesCourier() {
        Farm f = farm();
        long before = unread(f.managerToken(), "/api/manager");

        long id = request(f, 19999);

        assertThat(unread(f.managerToken(), "/api/manager")).isEqualTo(before + 1);
        JsonNode first = get("/api/manager/notifications", f.managerToken()).getBody().path("data").path("content").path(0);
        assertThat(first.path("type").asText()).isEqualTo("CASH_HANDOVER_REQUESTED");
        assertThat(first.path("referenceId").asLong()).isEqualTo(id);
        assertThat(first.path("message").asText()).contains("19 999");
        assertThat(unread(f.bossToken(), "/api/boss")).isEqualTo(1);
        assertThat(get("/api/manager/dashboard", f.managerToken()).getBody().path("data").path("pendingHandoversCount").asLong()).isEqualTo(1);
        assertThat(get("/api/courier/cash", f.courierToken()).getBody().path("data").path("pendingHandover").path("status").asText()).isEqualTo("PENDING");

        ResponseEntity<JsonNode> rejected = call(HttpMethod.POST, "/api/manager/cash/handovers/" + id + "/reject", f.managerToken(),
                Map.of("reason", "Summa noto'g'ri"));
        assertThat(rejected.getStatusCode().value()).as("%s", rejected.getBody()).isEqualTo(200);

        JsonNode history = get("/api/courier/cash/handovers", f.courierToken()).getBody().path("data").path("content").path(0);
        assertThat(history.path("status").asText()).isEqualTo("REJECTED");
        assertThat(history.path("rejectReason").asText()).isEqualTo("Summa noto'g'ri");
        JsonNode note = get("/api/courier/notifications", f.courierToken()).getBody().path("data").path("content").path(0);
        assertThat(note.path("type").asText()).isEqualTo("CASH_HANDOVER_REJECTED");
        assertThat(note.path("referenceId").asLong()).isEqualTo(id);
        assertThat(note.path("message").asText()).contains("Summa noto'g'ri");

        // O'qilgan deb belgilash
        long notificationId = first.path("id").asLong();
        assertThat(call(HttpMethod.PATCH, "/api/manager/notifications/" + notificationId + "/read", f.managerToken(), null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.PATCH, "/api/manager/notifications/" + notificationId + "/read", f.bossToken(), null).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.POST, "/api/manager/notifications/read-all", f.managerToken(), null).getStatusCode().value()).isEqualTo(200);
        assertThat(unread(f.managerToken(), "/api/manager")).isZero();
    }

    @Test
    @DisplayName("4: boshqa summa bilan tasdiq → CONFIRMED, note, balans; kamomad bo'lsa Boss'ga xabar; 6: boshqa ferma 404")
    void confirmWithDifferentAmount() {
        Farm f = farm();
        long id = request(f, 19999);

        assertThat(call(HttpMethod.POST, "/api/manager/cash/handovers/" + id + "/confirm", farm().managerToken(),
                Map.of("confirmedAmount", 20000, "note", "x")).getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> confirmed = call(HttpMethod.POST, "/api/manager/cash/handovers/" + id + "/confirm", f.managerToken(),
                Map.of("confirmedAmount", 20000, "note", "Aslida 20 000 berdi"));
        assertThat(confirmed.getStatusCode().value()).as("%s", confirmed.getBody()).isEqualTo(200);

        JsonNode history = get("/api/courier/cash/handovers?status=CONFIRMED", f.courierToken()).getBody().path("data").path("content").path(0);
        assertThat(history.path("confirmedAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(history.path("note").asText()).isEqualTo("Aslida 20 000 berdi");
        assertThat(get("/api/courier/cash", f.courierToken()).getBody().path("data").path("balance").decimalValue()).isEqualByComparingTo("30000");
        JsonNode note = get("/api/courier/notifications", f.courierToken()).getBody().path("data").path("content").path(0);
        assertThat(note.path("type").asText()).isEqualTo("CASH_HANDOVER_CONFIRMED");
        assertThat(note.path("message").asText()).contains("20 000", "Aslida");

        // Kamomad: 10 000 e'lon, 9 000 qabul → Boss'ga CASH_HANDOVER_SHORTAGE
        long shortId = request(f, 10000);
        call(HttpMethod.POST, "/api/boss/cash/handovers/" + shortId + "/confirm", f.bossToken(), Map.of("confirmedAmount", 9000, "note", "1000 kam"));
        JsonNode bossNotes = get("/api/boss/notifications", f.bossToken()).getBody().path("data").path("content");
        assertThat(bossNotes.findValuesAsText("type")).contains("CASH_HANDOVER_SHORTAGE");
    }

    @Test
    @DisplayName("5: kuryer PENDING so'rovni bekor qiladi → CANCELLED_BY_COURIER, keyin yangi so'rov 409 bermaydi")
    void courierCancelsPending() {
        Farm f = farm();
        long id = request(f, 19999);

        ResponseEntity<JsonNode> cancelled = call(HttpMethod.DELETE, "/api/courier/cash/handovers/" + id, f.courierToken(), null);
        assertThat(cancelled.getStatusCode().value()).as("%s", cancelled.getBody()).isEqualTo(200);
        assertThat(cancelled.getBody().path("data").path("status").asText()).isEqualTo("CANCELLED_BY_COURIER");

        ResponseEntity<JsonNode> again = call(HttpMethod.DELETE, "/api/courier/cash/handovers/" + id, f.courierToken(), null);
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(again.getBody().path("code").asText()).isEqualTo("HANDOVER_NOT_PENDING");

        long next = request(f, 20000);
        Farm other = farm();
        assertThat(call(HttpMethod.DELETE, "/api/courier/cash/handovers/" + next, other.courierToken(), null).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/manager/dashboard", f.managerToken()).getBody().path("data").path("pendingHandoversCount").asLong()).isEqualTo(1);
    }
}
