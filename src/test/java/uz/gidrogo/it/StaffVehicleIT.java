package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boss dastavkachi yaratganda transport, mashina raqami va yuk sig'imi kiritiladi. */
class StaffVehicleIT extends ItBase {

    private String bossToken() {
        Map<String, Object> f = createFarmViaApi();
        jdbc.update("UPDATE users SET activation_status = 'APPROVED' WHERE id = ?", f.get("bossId"));
        return loginToken((String) f.get("bossPhone"), PASSWORD);
    }

    private Map<String, Object> courier(String plate) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "fullName", "TEST Kuryer", "phone", uniquePhone(), "password", PASSWORD, "role", "COURIER",
                "vehicleModel", "Damas", "maxCapacity", 40));
        if (plate != null) body.put("vehiclePlateNumber", plate);
        return body;
    }

    @Test
    @DisplayName("Dastavkachi yaratish: transport, raqam, sig'im saqlanadi; raqamsiz 422; keyin tahrirlanadi")
    void courierVehicleOnCreateAndUpdate() {
        String boss = bossToken();

        ResponseEntity<JsonNode> noPlate = call(HttpMethod.POST, "/api/boss/staff", boss, courier(null));
        assertThat(noPlate.getStatusCode().value()).isEqualTo(422);
        assertThat(noPlate.getBody().path("code").asText()).isEqualTo("VEHICLE_PLATE_REQUIRED");

        ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/api/boss/staff", boss, courier(" 01 a  123 bc "));
        assertThat(created.getStatusCode().value()).as("%s", created.getBody()).isEqualTo(200);
        JsonNode data = created.getBody().path("data");
        assertThat(data.path("vehicleModel").asText()).isEqualTo("Damas");
        assertThat(data.path("vehiclePlateNumber").asText()).isEqualTo("01 A 123 BC");
        assertThat(data.path("maxCapacity").asInt()).isEqualTo(40);
        long id = data.path("id").asLong();

        JsonNode detail = get("/api/boss/staff/" + id, boss).getBody().path("data");
        assertThat(detail.path("vehiclePlateNumber").asText()).isEqualTo("01 A 123 BC");
        assertThat(detail.path("maxCapacity").asInt()).isEqualTo(40);

        ResponseEntity<JsonNode> updated = call(HttpMethod.PUT, "/api/boss/staff/" + id + "/vehicle", boss,
                Map.of("vehicleModel", "Labo", "maxCapacity", 60));
        assertThat(updated.getStatusCode().value()).as("%s", updated.getBody()).isEqualTo(200);
        assertThat(updated.getBody().path("data").path("vehicleModel").asText()).isEqualTo("Labo");
        assertThat(updated.getBody().path("data").path("maxCapacity").asInt()).isEqualTo(60);
        assertThat(updated.getBody().path("data").path("vehiclePlateNumber").asText()).isEqualTo("01 A 123 BC");

        // Boshqa ferma bossi tahrirlay olmaydi, sig'im chegarasi tekshiriladi
        assertThat(call(HttpMethod.PUT, "/api/boss/staff/" + id + "/vehicle", bossToken(), Map.of("maxCapacity", 10))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.PUT, "/api/boss/staff/" + id + "/vehicle", boss, Map.of("maxCapacity", 0))
                .getStatusCode().value()).isIn(400, 422);
    }

    @Test
    @DisplayName("Manager yaratishda mashina raqami talab qilinmaydi")
    void managerNeedsNoVehicle() {
        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/boss/staff", bossToken(), Map.of(
                "fullName", "TEST Manager", "phone", uniquePhone(), "password", PASSWORD, "role", "MANAGER"));
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(200);
        assertThat(r.getBody().path("data").path("vehiclePlateNumber").isNull()).isTrue();
    }
}
