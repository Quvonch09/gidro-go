package uz.gidrogo.modules.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
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

        @Size(max = 300, message = "Izoh ko'pi bilan 300 ta belgidan oshmasligi kerak")
        @Schema(description = "Mijoz izohi (maksimal 300 ta belgi)", maxLength = 300)
        private String clientComment;
        private String deliverySlot;
        private Integer emptyBottlesReturned;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartQuoteResponse {
        private BigDecimal productsSum;
        private List<TaraLineDto> taraLines;
        private BigDecimal totalSum;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaraLineDto {
        private Long productId;
        private String productName;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal sum;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDto {
        @NotNull(message = "Mahsulot ID kiritilishi shart")
        private Long productId;

        @NotNull(message = "Miqdor kiritilishi shart")
        @Min(value = 1, message = "Miqdor kamida 1 bo'lishi kerak")
        @Max(value = 99, message = "Miqdor ko'pi bilan 99 bo'lishi mumkin")
        @Schema(description = "Mahsulot soni (butun son, 1 dan 99 gacha)", minimum = "1", maximum = "99")
        private Integer quantity;
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
        private String courierVehicleModel;
        private String courierVehiclePlateNumber;
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
        @Size(max = 300, message = "Izoh ko'pi bilan 300 ta belgidan oshmasligi kerak")
        @Schema(description = "Mijoz izohi (maksimal 300 ta belgi)", maxLength = 300)
        private String clientComment;
        private List<OrderItemResponse> items;
        private Integer deliverySequence;
        private Long anchorOrderId;
        private String preparingReason;
        private Instant preparingSince;
        private Integer dispatchAttempts;
        private BigDecimal productsSum;
        private Integer taraQtyEstimated;
        private Integer taraQtyFinal;
        private BigDecimal taraSum;
        private List<OrderBottleDetailDto> bottles;
        private List<String> warnings;
        private Instant assignedAt;
        private Instant deliveredAt;
        private Instant completedAt;
        private Instant createdAt;
        private List<StatusHistoryItem> statusHistory;
        private Boolean reviewed;
        private Instant reviewedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryItem {
        private String fromStatus;
        private String status;
        private Instant at;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBottleDetailDto {
        private Long productId;
        private String productName;
        private int fullQty;
        private int expectedEmptyQty;
        private int taraQtyEstimated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverResponse {
        private Long orderId;
        private String status;
        private Integer taraQtyFinal;
        private BigDecimal taraSum;
        private BigDecimal totalSum;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusUpdateRequest {
        @jakarta.validation.constraints.NotBlank(message = "Status kiritilishi shart")
        private String status; // CONFIRMED, PROCESSING, CANCELLED, DELIVERED, COMPLETED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerOrderCreateRequest {
        @jakarta.validation.constraints.NotBlank(message = "Mijoz ismi kiritilishi shart")
        private String customerName;

        @jakarta.validation.constraints.NotBlank(message = "Telefon raqami kiritilishi shart")
        private String phone;

        @jakarta.validation.constraints.NotBlank(message = "Manzil kiritilishi shart")
        private String address;

        private Double latitude;
        private Double longitude;
        private Integer bottlesCount;
        private Long productId;
        private String paymentMethod; // CASH or ONLINE
        private String notes;
        private Long courierId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignCourierRequest {
        @NotNull(message = "Dastavkachi ID kiritilishi shart")
        private Long courierId;
    }
}
