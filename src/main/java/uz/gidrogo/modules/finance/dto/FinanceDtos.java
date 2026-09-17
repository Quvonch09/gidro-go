package uz.gidrogo.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class FinanceDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseCreateRequest {
        @NotBlank(message = "Kategoriya kiritilishi shart")
        private String category; // FUEL, SALARY, TRANSPORT, MAINTENANCE, OTHER

        @NotNull(message = "Summa kiritilishi shart")
        @Positive(message = "Summa musbat son bo'lishi kerak")
        private BigDecimal amount;

        private String note;
        private Long courierId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceSummaryResponse {
        private Long farmId;
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal netProfit; // totalIncome - totalExpense
        private String period;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private Long id;
        private Long farmId;
        private String type;
        private String category;
        private BigDecimal amount;
        private Long orderId;
        private Long courierId;
        private String note;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private Long farmId;
        private BigDecimal todayIncome;
        private BigDecimal monthIncome;
        private BigDecimal todayExpense;
        private BigDecimal todayProfit;
        private BigDecimal monthProfit;
        private long totalOrders;
        private long todayOrders;
        private long activeOrders;
        private long deliveredToday;
        private BigDecimal bottlesSoldToday;
        private BigDecimal warehouseBottles;
        private long activeCouriers;
        private long totalClients;
        private List<uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse> recentOrders;
    }
}
