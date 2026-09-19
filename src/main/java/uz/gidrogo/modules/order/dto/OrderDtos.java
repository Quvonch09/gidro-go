package uz.gidrogo.modules.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.PaymentMethod;
import uz.gidrogo.modules.order.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartCheckoutRequest {
        @NotEmpty(message = "Savat bo'sh bo'lishi mumkin emas")
        private List<CartItemDto> items;

        @NotNull(message = "Yetkazib berish manzili ID kiritilishi shart")
        private Long addressId;

        @NotNull(message = "To'lov usuli (CASH yoki ONLINE) kiritilishi shart")
        private PaymentMethod paymentMethod;

        private String clientComment;
        private String deliverySlot;
        private Integer emptyBottlesReturned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDto {
        @NotNull(message = "Mahsulot ID kiritilishi shart")
        private Long productId;

        @NotNull(message = "Miqdor kiritilishi shart")
        @Positive(message = "Miqdor musbat son bo'lishi kerak")
        private BigDecimal quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemReportRequest {
        @NotNull(message = "Muammo kodi kiritilishi shart")
        private String reasonCode; // CLIENT_UNREACHABLE, ADDRESS_NOT_FOUND, VEHICLE_ISSUE, PRODUCT_ISSUE, FUEL_EMPTY, OTHER

        private String reasonText; // OTHER bo'lsa majburiy
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReassignRequest {
        @NotNull(message = "Yangi dastavkachi ID kiritilishi shart")
        private Long newCourierId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private String orderNumber;
        private UUID cartGroupId;
        private Long farmId;
        private String farmName;
        private Long clientId;
        private String clientName;
        private String clientPhone;
        private Long courierId;
        private String courierName;
        private String courierPhone;
        private OrderStatus status;
        private PaymentMethod paymentMethod;
        private PaymentStatus paymentStatus;
        private BigDecimal totalSum; // Eslatma: Online to'langan bo'lsa kuryerga null/yashirin ko'rsatiladi
        private String deliveryAddress;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String deliverySlot;
        private Integer emptyBottlesReturned;
        private BigDecimal depositAmount;
        private String clientComment;
        private List<OrderItemResponse> items;
        private Instant assignedAt;
        private Instant deliveredAt;
        private Instant completedAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPageResponse {
        private List<OrderResponse> content;
        private long totalElements;
        private int totalPages;
        private int currentPage;
        private int pageSize;
    }
}
