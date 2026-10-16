package uz.gidrogo.modules.superadmin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** SuperAdmin Mijozlar (BE-08, TZ §9) va Buyurtmalar (BE-09, TZ §10) API kontrakti. */
public class SuperAdminCrmDtos {

    // ==========================================
    // MIJOZLAR (BE-08)
    // ==========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientSummary {
        private String from;
        private String to;
        /** O'chirilmagan mijozlar. */
        private long total;
        /** Davrda ro'yxatdan o'tganlar. */
        private long newInPeriod;
        /** `to` bilan tugaydigan oxirgi 30 kunda kamida bitta buyurtma berganlar. */
        private long active;
        private long blocked;
        /** newInPeriod oldingi teng davrga nisbatan; oldingi 0 bo'lsa null. */
        private Double growthPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientListItem {
        /** clients.id */
        private Long id;
        private Long userId;
        private String fullName;
        private String phone;
        /** ACTIVE | BLOCKED */
        private String status;
        private Long farmId;
        private String farmName;
        private Instant createdAt;
        private long ordersCount;
        private Instant lastOrderAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BottleBalanceItem {
        private Long farmId;
        private String farmName;
        private Long productId;
        private String productName;
        private int balance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDetail {
        private Long id;
        private Long userId;
        private String fullName;
        private String phone;
        private String status;
        private Long farmId;
        private String farmName;
        private Instant createdAt;
        private long addressesCount;
        private long ordersCount;
        private long completedOrders;
        private long activeOrders;
        private Instant lastOrderAt;
        /** Fermalar va mahsulotlar kesimida idish (tara) balansi. */
        private List<BottleBalanceItem> bottleBalances;
        private int totalBottleBalance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientStatusUpdateRequest {
        @NotNull(message = "Status kiritilishi shart")
        @Pattern(regexp = "^(ACTIVE|BLOCKED)$", message = "Status faqat ACTIVE yoki BLOCKED bo'lishi mumkin")
        private String status;

        @Size(max = 500)
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientPaymentItem {
        private Long orderId;
        private String orderNumber;
        private Long farmId;
        private String farmName;
        /** ONLINE | CASH */
        private String paymentType;
        /** PAID | CASH_COLLECTED */
        private String paymentStatus;
        /** Suv/mahsulot qismi. */
        private BigDecimal amount;
        private BigDecimal taraSum;
        private BigDecimal totalSum;
        private Instant paidAt;
    }

    // ==========================================
    // BUYURTMALAR (BE-09)
    // ==========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ref {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonRef {
        private Long id;
        private String fullName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemLine {
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private BigDecimal lat;
        private BigDecimal lon;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderListItem {
        private Long id;
        private String orderNumber;
        /** client.id = clients.id */
        private PersonRef client;
        private Ref farm;
        private List<OrderItemLine> items;
        private BigDecimal totalSum;
        /** ONLINE | CASH */
        private String paymentType;
        private String status;
        private Instant createdAt;
        private DeliveryInfo delivery;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryItem {
        private String fromStatus;
        private String status;
        private Instant at;
        private Long actorId;
        /** SUPER_ADMIN | BOSS | MANAGER | COURIER | CLIENT | SYSTEM */
        private String actorRole;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaraInfo {
        /** Tara narxi qo'llangan to'la idishlar soni. */
        private Integer fullGiven;
        private Integer emptyReturned;
        private BigDecimal taraSum;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDetail {
        private Long id;
        private String orderNumber;
        private PersonRef client;
        private String clientPhone;
        private Ref farm;
        private List<OrderItemLine> items;
        private BigDecimal productsSum;
        private BigDecimal totalSum;
        private String paymentType;
        private String paymentStatus;
        private String status;
        private Instant createdAt;
        private DeliveryInfo delivery;
        private PersonRef courier;
        private Instant assignedAt;
        private Instant deliveredAt;
        private Instant completedAt;
        private TaraInfo tara;
        /** Bekor qilish sababi alohida saqlanmaydi — hozircha doim null (hisobotdagi ochiq savolga qarang). */
        private String cancelReason;
        private List<StatusHistoryItem> statusHistory;
    }
}
