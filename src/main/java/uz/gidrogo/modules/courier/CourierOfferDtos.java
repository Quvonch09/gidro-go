package uz.gidrogo.modules.courier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public class CourierOfferDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierOfferResponse {
        private Long offerId;
        private Long orderId;
        private String status;
        private Instant expiresAt;
        private Long ttlSeconds;
        private Integer distanceMeters;
        private OrderBriefDto order;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBriefDto {
        private Long id;
        private String orderNumber;
        private String status;
        private Long farmId;
        private String farmName;
        private Double farmLatitude;
        private Double farmLongitude;
        private String deliveryAddress;
        private Double latitude;
        private Double longitude;
        private BigDecimal totalSum;
        private String paymentMethod;
        private Integer itemsCount;
        private String deliverySlot;
        private Instant createdAt;
    }
}
