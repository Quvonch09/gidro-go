package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-03 (BUG-016, CHK-04): boss–ferma yaxlitligi va summary. */
class BossFarmIntegrityIT extends ItBase {

    /** Bossi yo'q ferma (to'g'ridan-to'g'ri SQL). */
    private long emptyFarm() {
        return jdbc.queryForObject("""
                INSERT INTO farms (name, phone, address, latitude, longitude, status, created_at)
                VALUES (?, '+998901112233', 'Toshkent', 41.3, 69.2, 'ACTIVE', now()) RETURNING id
                """, Long.class, uniqueName("EmptyFarm"));
    }

    @Test
    @DisplayName("Bossi bor fermaga ikkinchi bossni biriktirish → 409 FARM_ALREADY_HAS_BOSS; yo'q ferma → 404")
    void secondBossConflict() {
        Map<String, Object> a = createFarmViaApi();
        Map<String, Object> b = createFarmViaApi();

        ResponseEntity<JsonNode> r = call(HttpMethod.PUT, "/api/superadmin/bosses/" + b.get("bossId"), superAdminToken(),
                Map.of("farmId", a.get("farmId")));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(r.getBody().path("code").asText()).isEqualTo("FARM_ALREADY_HAS_BOSS");

        ResponseEntity<JsonNode> nf = call(HttpMethod.PUT, "/api/superadmin/bosses/" + b.get("bossId"), superAdminToken(),
                Map.of("farmId", 987654321L));
        assertThat(nf.getStatusCode().value()).isEqualTo(404);
        assertThat(nf.getBody().path("code").asText()).isEqualTo("FARM_NOT_FOUND");

        // Ma'lumot o'zgarmagan: farms.boss_user_id — yagona manba
        assertThat(jdbc.queryForObject("SELECT boss_user_id FROM farms WHERE id = ?", Long.class, a.get("farmId")))
                .isEqualTo(a.get("bossId"));
    }

    @Test
    @DisplayName("Boss bo'sh fermaga ko'chiriladi: ikkala manba mos, javobdagi farmId = farms.boss_user_id")
    void moveBossKeepsBothSidesConsistent() {
        Map<String, Object> a = createFarmViaApi();
        long target = emptyFarm();
        ResponseEntity<JsonNode> r = call(HttpMethod.PUT, "/api/superadmin/bosses/" + a.get("bossId"), superAdminToken(),
                Map.of("farmId", target));
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(200);
        assertThat(r.getBody().path("data").path("farmId").asLong()).isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT boss_user_id FROM farms WHERE id = ?", Long.class, target)).isEqualTo(a.get("bossId"));
        assertThat(jdbc.queryForObject("SELECT boss_user_id FROM farms WHERE id = ?", Long.class, a.get("farmId"))).isNull();
        assertThat(jdbc.queryForObject("SELECT farm_id FROM users WHERE id = ?", Long.class, a.get("bossId"))).isEqualTo(target);

        // Audit BOSS_UPDATED old/new bilan, parolsiz
        String meta = jdbc.queryForObject(
                "SELECT metadata FROM superadmin_audit_log WHERE action = 'BOSS_UPDATED' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                String.class, a.get("bossId"));
        assertThat(meta).contains("\"old\"").contains("\"new\"").doesNotContainIgnoringCase("password\":\"");
    }

    @Test
    @DisplayName("Ikki parallel so'rov bitta bo'sh fermaga → biri 200, biri 409")
    void parallelAssignment() throws Exception {
        Map<String, Object> a = createFarmViaApi();
        Map<String, Object> b = createFarmViaApi();
        long target = emptyFarm();
        String token = superAdminToken();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (Object bossId : List.of(a.get("bossId"), b.get("bossId"))) {
            tasks.add(() -> {
                start.await();
                return call(HttpMethod.PUT, "/api/superadmin/bosses/" + bossId, token, Map.of("farmId", target))
                        .getStatusCode().value();
            });
        }
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> t : tasks) futures.add(pool.submit(t));
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : futures) codes.add(f.get());
        pool.shutdown();

        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM farms WHERE boss_user_id IN (?, ?)", Long.class,
                a.get("bossId"), b.get("bossId"))).isEqualTo(2L); // har bir boss — bitta fermada
    }

    @Test
    @DisplayName("summary.total = ro'yxatdagi yozuvlar soni (bir xil filtr); o'chirilganlar sanalmaydi")
    void summaryMatchesList() {
        Map<String, Object> a = createFarmViaApi();
        createFarmViaApi();
        call(HttpMethod.DELETE, "/api/superadmin/bosses/" + a.get("bossId"), superAdminToken(), null);

        JsonNode all = get("/api/superadmin/bosses?size=100", superAdminToken()).getBody().path("data");
        assertThat(all.path("summary").path("total").asLong()).isEqualTo(all.path("totalElements").asLong());
        long dbCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'BOSS' AND status <> 'DELETED'", Long.class);
        assertThat(all.path("totalElements").asLong()).isEqualTo(dbCount);
        assertThat(all.path("summary").path("active").asLong() + all.path("summary").path("blocked").asLong())
                .isEqualTo(all.path("summary").path("total").asLong());

        JsonNode blocked = get("/api/superadmin/bosses?status=BLOCKED&size=100", superAdminToken()).getBody().path("data");
        assertThat(blocked.path("summary").path("total").asLong()).isEqualTo(blocked.path("totalElements").asLong());
        assertThat(all.toString()).doesNotContain("Aliyev Bobur").doesNotContain("ui-avatars");
    }
}
