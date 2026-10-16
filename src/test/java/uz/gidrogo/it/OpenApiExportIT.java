package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-06: OpenAPI kontraktini docs/openapi.json ga eksport qiladi (frontend tiplarni shundan generatsiya qiladi, FE-01).
 * Har chiqarishdan oldin: ./mvnw test -Dtest=OpenApiExportIT
 */
class OpenApiExportIT extends ItBase {

    @Test
    @DisplayName("docs/openapi.json yangilanadi")
    void exportOpenApi() throws Exception {
        ResponseEntity<JsonNode> r = get("/v3/api-docs", null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        JsonNode spec = r.getBody();
        // Tasodifiy test porti kontraktga kirmasin
        if (spec.has("servers")) ((com.fasterxml.jackson.databind.node.ObjectNode) spec).remove("servers");
        Path out = Path.of("docs", "openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(spec) + System.lineSeparator());
        assertThat(spec.path("paths").has("/api/superadmin/clients")).isTrue();
        assertThat(spec.path("paths").has("/api/superadmin/orders/{id}")).isTrue();
    }
}
