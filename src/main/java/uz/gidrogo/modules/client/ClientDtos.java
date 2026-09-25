package uz.gidrogo.modules.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.modules.farm.dto.FarmDtos.FarmResponse;

import java.math.BigDecimal;
import java.util.List;

public class ClientDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressCreateRequest {
        private String label;

        @NotBlank(message = "Manzil kiritilishi shart")
        private String address;

        @NotNull(message = "Latitude kiritilishi shart")
        private Double latitude;

        @NotNull(message = "Longitude kiritilishi shart")
        private Double longitude;

        private boolean isDefault;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressResponse {
        private Long id;
        private String label;
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private boolean isDefault;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NearbyFarmResponse {
        private FarmResponse farm;
        private double distanceMeters;
        private double rating;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableFarmResponse {
        private Long id;
        private String name;
        private String phone;
        private String address;
        private String logoUrl;
        private double rating;
        private Integer reviewCount;
        private Double distanceKm;
        private Integer deliveryTimeMinutes;
        private Double latitude;
        private Double longitude;
        private Boolean isOpen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrmClientResponse {
        private Long clientId;
        private Long userId;
        private String fullName;
        private String phone;
        private java.time.Instant registeredAt;
        private long totalOrders;
        private BigDecimal totalSpent;
        private java.time.Instant lastOrderAt;
        private String primaryAddress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrmClientDetailResponse {
        private Long clientId;
        private Long userId;
        private String fullName;
        private String phone;
        private java.time.Instant registeredAt;
        private long totalOrders;
        private BigDecimal totalSpent;
        private Integer bottleBalance;
        private List<AddressResponse> addresses;
        private List<CrmClientOrderSummary> recentOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrmClientOrderSummary {
        private Long orderId;
        private String orderNumber;
        private String status;
        private BigDecimal totalSum;
        private String deliveryAddress;
        private String deliverySlot;
        private Integer emptyBottlesReturned;
        private java.time.Instant createdAt;
    }
}
