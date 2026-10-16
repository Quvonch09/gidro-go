package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BE-12 (BUG-013, BUG-014, CHK-06). */
class AuditLogIT extends ItBase {

    @Test
    @DisplayName("Boss holati, so'rovni rad etish → har biri uchun bitta yozuv, old/new, IP va User-Agent bilan")
    void eachActionWritesOneRecord() {
        Map<String, Object> farm = createFarmViaApi();
        long bossId = (Long) farm.get("bossId");
        call(HttpMethod.PATCH, "/api/superadmin/bosses/" + bossId + "/toggle-status", superAdminToken(), null, "10.20.30.40");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT metadata, ip_address, user_agent FROM superadmin_audit_log WHERE action = 'BOSS_STATUS_CHANGED' AND entity_id = ?", bossId);
        assertThat((String) row.get("metadata")).contains("\"old\":{\"status\":\"ACTIVE\"").contains("\"new\":{\"status\":\"BLOCKED\"");
        assertThat(row.get("ip_address")).isEqualTo("10.20.30.40");
        assertThat(row.get("user_agent")).isNotNull();
    }

    @Test
    @DisplayName("Filtrlar server tomonda: action=FARM_BLOCKED faqat shu tur; entityType; sahifalash totalElements bilan")
    void filtersAndPaging() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        call(HttpMethod.PATCH, "/api/superadmin/farms/" + farmId + "/status", superAdminToken(), Map.of("status", "BLOCKED"));

        JsonNode page = get("/api/superadmin/audit-log?action=FARM_BLOCKED&size=100", superAdminToken()).getBody().path("data");
        assertThat(page.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : page.path("content")) {
            assertThat(item.path("action").asText()).isEqualTo("FARM_BLOCKED");
        }

        JsonNode multi = get("/api/superadmin/audit-log?action=FARM_BLOCKED,FARM_CREATED&entityType=FARM&size=5", superAdminToken())
                .getBody().path("data");
        assertThat(multi.path("content").size()).isLessThanOrEqualTo(5);
        assertThat(multi.path("totalPages").asInt()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : multi.path("content")) {
            assertThat(item.path("action").asText()).isIn("FARM_BLOCKED", "FARM_CREATED");
            assertThat(item.path("metadata").isObject()).isTrue();
        }

        JsonNode search = get("/api/superadmin/audit-log?search=" + farmId + "&action=FARM_BLOCKED", superAdminToken())
                .getBody().path("data");
        assertThat(search.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Audit Log va ledgerlarni bazada o'zgartirib/o'chirib bo'lmaydi")
    void immutable() {
        createFarmViaApi();
        assertThatThrownBy(() -> jdbc.update("UPDATE superadmin_audit_log SET action = 'X' WHERE id = (SELECT MAX(id) FROM superadmin_audit_log)"))
                .hasMessageContaining("taqiqlangan");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM superadmin_audit_log WHERE id = (SELECT MAX(id) FROM superadmin_audit_log)"))
                .hasMessageContaining("taqiqlangan");
    }

    @Test
    @DisplayName("Amal xato bilan tugasa, audit ham yozilmaydi (bitta tranzaksiya)")
    void failedActionLeavesNoAudit() {
        Map<String, Object> a = createFarmViaApi();
        Map<String, Object> b = createFarmViaApi();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM superadmin_audit_log WHERE action = 'BOSS_UPDATED' AND entity_id = ?",
                Long.class, b.get("bossId"));
        call(HttpMethod.PUT, "/api/superadmin/bosses/" + b.get("bossId"), superAdminToken(),
                Map.of("fullName", "TEST O'zgargan", "farmId", a.get("farmId"))); // 409
        long after = jdbc.queryForObject("SELECT COUNT(*) FROM superadmin_audit_log WHERE action = 'BOSS_UPDATED' AND entity_id = ?",
                Long.class, b.get("bossId"));
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, b.get("bossId")))
                .isNotEqualTo("TEST O'zgargan");
    }
}
