package uz.gidrogo.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-06 (SEC-01): prod sozlamasi (springdoc o'chirilgan) bilan /v3/api-docs va Swagger UI yopiq. */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class SwaggerDisabledIT extends ItBase {

    @Test
    @DisplayName("/v3/api-docs va /swagger-ui.html tokensiz → 401 yoki 404")
    void apiDocsClosed() {
        assertThat(get("/v3/api-docs", null).getStatusCode().value()).isIn(401, 404);
        assertThat(get("/swagger-ui.html", null).getStatusCode().value()).isIn(401, 404);
        assertThat(get("/swagger-ui/index.html", null).getStatusCode().value()).isIn(401, 404);
    }
}
