package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-07 (BUG-018, CHK-10) logout, BE-21 parol siyosati va parol almashtirish. */
class AuthSessionIT extends ItBase {

    @Test
    @DisplayName("BE-07: logout'dan keyin eski refresh → 401; takroriy logout xatosiz; refresh rotatsiyasi")
    void logoutRevokesRefreshToken() {
        String phone = uniquePhone();
        createUser("SUPER_ADMIN", phone, null, null);
        JsonNode auth = login(phone, PASSWORD).getBody().path("data");
        String access = auth.path("accessToken").asText();
        String refresh = auth.path("refreshToken").asText();

        // Rotatsiya: refresh bir marta ishlatiladi
        ResponseEntity<JsonNode> refreshed = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", refresh));
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        String refresh2 = refreshed.getBody().path("data").path("refreshToken").asText();
        assertThat(call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", refresh)).getStatusCode().value())
                .isEqualTo(401);

        Map<String, Object> body = Map.of("refreshToken", refresh2, "fcmToken", "device-token");
        assertThat(call(HttpMethod.POST, "/api/auth/logout", access, body).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.POST, "/api/auth/logout", access, body).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<JsonNode> after = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", refresh2));
        assertThat(after.getStatusCode().value()).isEqualTo(401);
        assertThat(after.getBody().path("code").asText()).isEqualTo("REFRESH_TOKEN_REVOKED");
    }

    @Test
    @DisplayName("BE-21: parol almashtirish — joriy parol, tasdiq, siyosat (422), boshqa sessiyalar bekor, audit PASSWORD_CHANGED")
    void changePassword() {
        String phone = uniquePhone();
        long id = createUser("SUPER_ADMIN", phone, null, null);
        JsonNode s1 = login(phone, PASSWORD).getBody().path("data");
        JsonNode s2 = login(phone, PASSWORD).getBody().path("data");
        String token = s1.path("accessToken").asText();

        ResponseEntity<JsonNode> wrongCurrent = call(HttpMethod.POST, "/api/superadmin/change-password", token,
                Map.of("currentPassword", "nope12345", "newPassword", "NewPass123", "confirmPassword", "NewPass123"));
        assertThat(wrongCurrent.getStatusCode().value()).isEqualTo(422);

        ResponseEntity<JsonNode> mismatch = call(HttpMethod.POST, "/api/superadmin/change-password", token,
                Map.of("currentPassword", PASSWORD, "newPassword", "NewPass123", "confirmPassword", "NewPass124"));
        assertThat(mismatch.getStatusCode().value()).isEqualTo(422);
        assertThat(mismatch.getBody().path("code").asText()).isEqualTo("PASSWORD_CONFIRMATION_MISMATCH");

        ResponseEntity<JsonNode> weak = call(HttpMethod.POST, "/api/superadmin/change-password", token,
                Map.of("currentPassword", PASSWORD, "newPassword", "onlyletters", "confirmPassword", "onlyletters"));
        assertThat(weak.getStatusCode().value()).isEqualTo(422);
        assertThat(weak.getBody().path("code").asText()).isEqualTo("PASSWORD_POLICY_VIOLATION");

        ResponseEntity<JsonNode> ok = call(HttpMethod.POST, "/api/superadmin/change-password", token,
                Map.of("currentPassword", PASSWORD, "newPassword", "NewPass123", "confirmPassword", "NewPass123"));
        assertThat(ok.getStatusCode().value()).as("%s", ok.getBody()).isEqualTo(200);

        assertThat(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", s2.path("refreshToken").asText())).getStatusCode().value()).isEqualTo(401);
        assertThat(login(phone, "NewPass123").getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM superadmin_audit_log WHERE action = 'PASSWORD_CHANGED' AND actor_id = ?",
                Long.class, id)).isEqualTo(1L);
        String meta = jdbc.queryForObject("SELECT metadata FROM superadmin_audit_log WHERE action = 'PASSWORD_CHANGED' AND actor_id = ?",
                String.class, id);
        assertThat(meta).doesNotContain("NewPass123").doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("SuperAdmin uchun maxfiy 'Admin123!' / 'Parol123!' parollari endi qabul qilinmaydi")
    void noBackdoorPasswords() {
        String phone = uniquePhone();
        createUser("SUPER_ADMIN", phone, null, null);
        for (String p : new String[]{"Admin123!", "Parol123!", "admin"}) {
            assertThat(login(phone, p).getStatusCode().value()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("PROFILE_UPDATE: entityType = SUPERADMIN, entityId = actorId, old/new")
    void profileUpdateAudit() {
        String phone = uniquePhone();
        long id = createUser("SUPER_ADMIN", phone, null, null);
        String token = loginToken(phone, PASSWORD);
        ResponseEntity<JsonNode> r = call(HttpMethod.PUT, "/api/superadmin/profile", token, Map.of("fullName", "TEST Yangi Ism"));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT entity_type, entity_id, metadata FROM superadmin_audit_log WHERE action = 'PROFILE_UPDATE' AND actor_id = ?", id);
        assertThat(row.get("entity_type")).isEqualTo("SUPERADMIN");
        assertThat(((Number) row.get("entity_id")).longValue()).isEqualTo(id);
        assertThat((String) row.get("metadata")).contains("TEST Yangi Ism").contains("\"old\"");
    }
}
