package uz.gidrogo.modules.courier;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CourierDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationUpdateRequest {
        @NotNull(message = "Latitude kiritilishi shart")
        private Double latitude;

        @NotNull(message = "Longitude kiritilishi shart")
        private Double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverRequest {
        @NotNull(message = "Yetkazilganlik rasmi URL kiritilishi shart")
        private String photoUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierDailySummaryResponse {
        private Long courierId;
        private String fullName;
        private String phone;
        private String currentStatus;
        private java.time.LocalDate date;
        private java.math.BigDecimal loadedBottlesToday;
        private java.math.BigDecimal soldBottlesToday;
        private java.math.BigDecimal remainingBottlesInVehicle;
        private long completedOrdersCount;
        private java.math.BigDecimal cashCollected;
        private java.math.BigDecimal onlineCollected;
        private java.math.BigDecimal totalRevenue;
        private Double latitude;
        private Double longitude;
        private java.time.Instant lastSeenAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierTrackingResponse {
        private Long courierId;
        private String fullName;
        private String phone;
        private String currentStatus; // IDLE, ON_THE_WAY, NEARBY, OFFLINE
        private Double latitude;
        private Double longitude;
        private java.time.Instant lastSeenAt;
        private Long activeOrderId;
        private String activeOrderNumber;
        private String activeOrderAddress;
    }
}
