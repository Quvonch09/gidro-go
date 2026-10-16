package uz.gidrogo.modules.bottle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class BottleDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BottleAdjustRequest {
        @NotNull(message = "Mahsulot ID kiritilishi shart")
        private Long productId;

        @NotNull(message = "Delta soni kiritilishi shart")
        private Integer delta;

        @NotBlank(message = "Izoh kiritilishi shart")
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientBottleBalanceResponse {
        private Long clientId;
        private Long farmId;
        private String farmName;
        private Long productId;
        private String productName;
        private Integer balance;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BottleLedgerEntryResponse {
        private Long id;
        private Long clientId;
        private Long farmId;
        private Long productId;
        private String productName;
        private Integer delta;
        private String reason;
        private Long orderId;
        private Long actorId;
        private String note;
        private Instant createdAt;
    }
}
