package uz.gidrogo.modules.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public class StockDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestockRequest {
        @NotNull(message = "Mahsulot identifikatori kiritilishi shart")
        private Long productId;

        @NotNull(message = "Miqdor kiritilishi shart")
        @Positive(message = "Miqdor musbat son bo'lishi kerak")
        private BigDecimal quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseStockUpdateRequest {
        @NotNull(message = "Mahsulot identifikatori kiritilishi shart")
        private Long productId;

        @NotNull(message = "Miqdor kiritilishi shart")
        private BigDecimal quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private Instant updatedAt;
    }
}
