package uz.gidrogo.modules.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public class ProductDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductCreateRequest {
        @NotBlank(message = "Mahsulot nomi kiritilishi shart")
        private String name;

        @NotNull(message = "Narx kiritilishi shart")
        @Positive(message = "Narx musbat son bo'lishi kerak")
        private BigDecimal price;

        private BigDecimal volumeLiters;
        private BigDecimal depositPrice;
        private String imageUrl;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductUpdateRequest {
        private String name;

        @Positive(message = "Narx musbat son bo'lishi kerak")
        private BigDecimal price;

        private BigDecimal volumeLiters;
        private BigDecimal depositPrice;
        private String imageUrl;
        private String description;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductResponse {
        private Long id;
        private Long farmId;
        private String name;
        private BigDecimal price;
        private BigDecimal volumeLiters;
        private BigDecimal depositPrice;
        private String imageUrl;
        private String description;
        private boolean active;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceRegionCreateRequest {
        @NotBlank(message = "Hudud nomi kiritilishi shart")
        private String regionName;

        @NotBlank(message = "Poligon koordinatalari JSON kiritilishi shart")
        private String polygonJson;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceRegionResponse {
        private Long id;
        private Long farmId;
        private String regionName;
        private String polygonJson;
        private boolean active;
        private Instant createdAt;
    }
}
