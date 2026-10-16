package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-18 (yagona xato formati), BE-19 (RBAC, login rate-limit). */
class ErrorFormatAndRbacIT extends ItBase {

    @Test
    @DisplayName("BE-18: days=abc → 400, ichki Java matnisiz, fieldErrors bilan")
    void typeMismatchIs400WithoutInternals() {
        ResponseEntity<JsonNode> r = get("/api/superadmin/orders/daily-dynamics?days=abc", superAdminToken());
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        JsonNode b = r.getBody();
        assertThat(b.path("success").asBoolean()).isFalse();
        assertThat(b.path("code").asText()).isEqualTo("INVALID_PARAMETER");
        assertThat(b.path("fieldErrors").get(0).path("field").asText()).isEqualTo("days");
        assertThat(b.toString()).doesNotContainIgnoringCase("java.").doesNotContain("Exception");
    }

    @Test
    @DisplayName("BE-18: farmId=abc, startDate=abc → 400; days chegarasi 1–366")
    void otherTypeErrorsAndBounds() {
        String t = superAdminToken();
        assertThat(get("/api/superadmin/orders/daily-dynamics?farmId=abc", t).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/api/superadmin/orders/daily-dynamics?startDate=abc", t).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/api/superadmin/orders/daily-dynamics?days=0", t).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/api/superadmin/orders/daily-dynamics?days=367", t).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/api/superadmin/farms?size=101", t).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/api/superadmin/farms?page=-1", t).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/api/superadmin/farms/abc", t).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("BE-19: SUPER_ADMIN tokeni bilan /api/courier/** va /api/client/** → 403")
    void superAdminCannotUseCourierOrClientApi() {
        String t = superAdminToken();
        ResponseEntity<JsonNode> courier = get("/api/courier/profile", t);
        assertThat(courier.getStatusCode().value()).isEqualTo(403);
        assertThat(courier.getBody().path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(get("/api/client/orders", t).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("BE-19: boshqa rollar /api/superadmin/** ga kira olmaydi; tokensiz → 401")
    void otherRolesCannotUseSuperAdminApi() {
        Map<String, Object> farm = createFarmViaApi();
        Map<String, Object> client = createClient((Long) farm.get("farmId"));
        String clientToken = loginToken((String) client.get("phone"), PASSWORD);
        assertThat(get("/api/superadmin/dashboard", clientToken).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/superadmin/dashboard", null).getStatusCode().value()).isEqualTo(401);

        String courierPhone = uniquePhone();
        createUser("COURIER", courierPhone, (Long) farm.get("farmId"), null);
        String courierToken = loginToken(courierPhone, PASSWORD);
        assertThat(get("/api/superadmin/dashboard", courierToken).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/client/orders", courierToken).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("BE-19: 5 ta noto'g'ri urinishdan keyin 6-chi → 429; xabar umumiy")
    void loginRateLimit() {
        String phone = uniquePhone();
        createUser("SUPER_ADMIN", phone, null, null);
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/auth/login", null,
                    Map.of("phone", phone, "password", "wrongPass1"), ip);
            assertThat(r.getStatusCode().value()).isEqualTo(401);
            assertThat(r.getBody().path("message").asText()).isEqualTo("Telefon yoki parol noto'g'ri");
        }
        ResponseEntity<JsonNode> sixth = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("phone", phone, "password", PASSWORD), ip);
        assertThat(sixth.getStatusCode().value()).isEqualTo(429);
        assertThat(sixth.getBody().path("code").asText()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
    }

    @Test
    @DisplayName("Mavjud bo'lmagan telefon va noto'g'ri parol — bir xil javob")
    void unknownUserSameMessage() {
        ResponseEntity<JsonNode> r = login(uniquePhone(), "whatever1");
        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getBody().path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }
}
