package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-04 (BUG-006), BE-05 (CHK-01, CHK-02), BE-16. */
class BossActivationIT extends ItBase {

    private record Pending(Map<String, Object> farm, long requestId) {}

    /** Yangi ferma, boss birinchi marta kiradi → 403 ACTIVATION_PENDING va so'rov yaratiladi. */
    private Pending newBossFirstLogin() {
        Map<String, Object> farm = createFarmViaApi();
        ResponseEntity<JsonNode> r = login((String) farm.get("bossPhone"), PASSWORD);
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(403);
        assertThat(r.getBody().path("code").asText()).isEqualTo("ACTIVATION_PENDING");
        assertThat(r.getBody().path("data").has("accessToken")).isFalse();
        return new Pending(farm, r.getBody().path("data").path("requestId").asLong());
    }

    private ResponseEntity<JsonNode> decide(long requestId, Map<String, Object> body) {
        return call(HttpMethod.POST, "/api/superadmin/activation-requests/" + requestId + "/decision", superAdminToken(), body);
    }

    @Test
    @DisplayName("BE-05: yangi boss → 403 ACTIVATION_PENDING, so'rov ro'yxatda; tasdiqdan keyin login 200; arxivda APPROVED")
    void pendingThenApproved() {
        Pending p = newBossFirstLogin();

        // Takroriy login yangi so'rov yaratmaydi — mavjudi qaytariladi
        ResponseEntity<JsonNode> again = login((String) p.farm().get("bossPhone"), PASSWORD);
        assertThat(again.getBody().path("data").path("requestId").asLong()).isEqualTo(p.requestId());

        JsonNode pending = get("/api/superadmin/activation-requests?status=PENDING&size=100", superAdminToken()).getBody().path("data");
        assertThat(pending.path("content").findValuesAsText("id")).contains(String.valueOf(p.requestId()));
        assertThat(pending.path("summary").path("pending").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode bosses = get("/api/superadmin/bosses?activationStatus=PENDING&size=100", superAdminToken()).getBody().path("data");
        assertThat(bosses.path("content").findValuesAsText("id")).contains(String.valueOf(p.farm().get("bossId")));

        ResponseEntity<JsonNode> ok = decide(p.requestId(), Map.of("status", "APPROVED"));
        assertThat(ok.getStatusCode().value()).as("%s", ok.getBody()).isEqualTo(200);
        assertThat(ok.getBody().path("data").path("decidedBy").isNull()).isFalse();
        assertThat(ok.getBody().path("data").path("decidedAt").isNull()).isFalse();

        ResponseEntity<JsonNode> loginOk = login((String) p.farm().get("bossPhone"), PASSWORD);
        assertThat(loginOk.getStatusCode().value()).isEqualTo(200);
        assertThat(loginOk.getBody().path("data").path("farmId").asLong()).isEqualTo(p.farm().get("farmId"));

        JsonNode archive = get("/api/superadmin/activation-requests?status=APPROVED&size=100", superAdminToken()).getBody().path("data");
        assertThat(archive.path("content").findValuesAsText("id")).contains(String.valueOf(p.requestId()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM superadmin_audit_log WHERE action = 'BOSS_REQUEST_APPROVED' AND entity_id = ?",
                Long.class, p.requestId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("BE-04: sababsiz rad etish → 422; status=PENDING → 400")
    void rejectValidation() {
        Pending p = newBossFirstLogin();
        ResponseEntity<JsonNode> noReason = decide(p.requestId(), Map.of("status", "REJECTED"));
        assertThat(noReason.getStatusCode().value()).isEqualTo(422);
        assertThat(noReason.getBody().path("fieldErrors").get(0).path("field").asText()).isEqualTo("rejectReason");

        ResponseEntity<JsonNode> shortReason = decide(p.requestId(), Map.of("status", "REJECTED", "rejectReason", "   qisqa   "));
        assertThat(shortReason.getStatusCode().value()).isEqualTo(422);

        ResponseEntity<JsonNode> pendingStatus = decide(p.requestId(), Map.of("status", "PENDING"));
        assertThat(pendingStatus.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("BE-05: rad etilgan → 403 ACTIVATION_REJECTED + sabab; keyingi kirish yangi PENDING; takroriy qaror → 409")
    void rejectedThenReRequest() {
        Pending p = newBossFirstLogin();
        String reason = "Hujjatlar to'liq emas, qayta yuboring";
        assertThat(decide(p.requestId(), Map.of("status", "REJECTED", "rejectReason", reason)).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<JsonNode> again = decide(p.requestId(), Map.of("status", "APPROVED"));
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(again.getBody().path("code").asText()).isEqualTo("REQUEST_ALREADY_DECIDED");

        ResponseEntity<JsonNode> rejected = login((String) p.farm().get("bossPhone"), PASSWORD);
        assertThat(rejected.getStatusCode().value()).isEqualTo(403);
        assertThat(rejected.getBody().path("code").asText()).isEqualTo("ACTIVATION_REJECTED");
        assertThat(rejected.getBody().path("data").path("rejectReason").asText()).isEqualTo(reason);

        ResponseEntity<JsonNode> reRequest = login((String) p.farm().get("bossPhone"), PASSWORD);
        assertThat(reRequest.getBody().path("code").asText()).isEqualTo("ACTIVATION_PENDING");
        assertThat(reRequest.getBody().path("data").path("requestId").asLong()).isNotEqualTo(p.requestId());

        String meta = jdbc.queryForObject("SELECT metadata FROM superadmin_audit_log WHERE action = 'BOSS_REQUEST_REJECTED' AND entity_id = ?",
                String.class, p.requestId());
        assertThat(meta).contains(reason);
    }

    @Test
    @DisplayName("BE-04: ikki parallel qaror → biri 200, biri 409")
    void parallelDecisions() throws Exception {
        Pending p = newBossFirstLogin();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("status", "APPROVED"),
                Map.of("status", "REJECTED", "rejectReason", "Parallel rad etish sababi"))) {
            futures.add(pool.submit(() -> {
                start.await();
                return decide(p.requestId(), body).getStatusCode().value();
            }));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : futures) codes.add(f.get());
        pool.shutdown();
        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM superadmin_audit_log WHERE entity_type = 'BOSS_ACTIVATION_REQUEST' AND entity_id = ?",
                Long.class, p.requestId())).isEqualTo(1L);
    }
}
