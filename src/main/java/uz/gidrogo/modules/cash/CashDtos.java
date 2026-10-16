package uz.gidrogo.modules.cash;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class CashDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierHandoverRequest {
        @NotNull(message = "Summa kiritilishi shart")
        @Positive(message = "Summa 0 dan katta bo'lishi kerak")
        private BigDecimal declaredAmount;

        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerHandoverConfirmRequest {
        @NotNull(message = "Tasdiqlangan summa kiritilishi shart")
        @Positive(message = "Summa 0 dan katta bo'lishi kerak")
        private BigDecimal confirmedAmount;

        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerHandoverRejectRequest {
        @NotNull(message = "Rad etish sababi kiritilishi shart")
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerDirectHandoverRequest {
        @NotNull(message = "Summa kiritilishi shart")
        @Positive(message = "Summa 0 dan katta bo'lishi kerak")
        private BigDecimal amount;

        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierCashSummaryResponse {
        private BigDecimal balance;
        private BigDecimal collectedToday;
        private PendingHandoverDto pendingHandover;
        private List<CashLedgerEntryDto> entries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingHandoverDto {
        private Long id;
        private BigDecimal declaredAmount;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashLedgerEntryDto {
        private Long id;
        private String type;
        private BigDecimal amount;
        private Long orderId;
        private Long handoverId;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierCashBalanceItem {
        private Long courierId;
        private String fullName;
        private String phone;
        private BigDecimal balance;
        private Instant oldestUnsubmittedAt;
        private Instant lastHandoverAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashHandoverResponse {
        private Long id;
        private Long courierId;
        private String courierName;
        private Long farmId;
        private BigDecimal declaredAmount;
        private BigDecimal confirmedAmount;
        private String status;
        private String initiatedBy;
        private Long managerId;
        private String managerName;
        private String note;
        private String rejectReason;
        private Instant createdAt;
        private Instant resolvedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossCashSummaryResponse {
        private BigDecimal totalCollected;
        private BigDecimal totalHandedOver;
        private BigDecimal remainingWithCouriers;
        private int activeCouriersWithCash;
    }
}
