package uz.gidrogo.modules.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class OrderTrackingDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Mijoz buyurtmasi bo'yicha kuryer jonli kuzatuv ma'lumotlari")
    public static class ClientOrderTrackingResponse {
        @Schema(description = "Buyurtma ID", example = "10284")
        private Long orderId;

        @Schema(description = "Buyurtma raqami", example = "ORD-10284")
        private String orderNumber;

        @Schema(description = "Buyurtma holati", example = "ON_THE_WAY")
        private String status;

        @Schema(description = "Yetkazib berish manzili", example = "Toshkent sh., Chilonzor 9-mavze, 14-uy")
        private String deliveryAddress;

        @Schema(description = "Yetkazish manzili kengligi (lat)", example = "41.2856")
        private Double deliveryLatitude;

        @Schema(description = "Yetkazish manzili uzunligi (lon)", example = "69.2034")
        private Double deliveryLongitude;

        @Schema(description = "Buyurtma berilgan ferma ma'lumotlari")
        private FarmBriefDto farm;

        @Schema(description = "Biriktirilgan kuryer jonli ma'lumotlari")
        private CourierLiveDto courier;

        @Schema(description = "Yetkazish masofasi va ETA ko'rsatkichlari")
        private TrackingMetricsDto metrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Ferma qisqacha ma'lumoti")
    public static class FarmBriefDto {
        @Schema(description = "Ferma ID", example = "1")
        private Long id;

        @Schema(description = "Ferma nomi", example = "GidroGo Tashkent Ferma")
        private String name;

        @Schema(description = "Ferma koordinatasi (lat)", example = "41.3050")
        private Double latitude;

        @Schema(description = "Ferma koordinatasi (lon)", example = "69.2200")
        private Double longitude;

        @Schema(description = "Ferma telefon raqami", example = "+998712000000")
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Kuryer jonli lokatsiyasi va avtomobil ma'lumoti")
    public static class CourierLiveDto {
        @Schema(description = "Kuryer foydalanuvchi ID", example = "7")
        private Long id;

        @Schema(description = "Kuryer to'liq ismi", example = "Jamshid Qodirov")
        private String fullName;

        @Schema(description = "Kuryer telefon raqami", example = "+998901234567")
        private String phone;

        @Schema(description = "Avatar rasmi URL manzili")
        private String avatarUrl;

        @Schema(description = "Avtomobil rusumi", example = "Chevrolet Damas")
        private String vehicleModel;

        @Schema(description = "Davlat raqam belgisi", example = "01 A 777 BA")
        private String vehiclePlateNumber;

        @Schema(description = "Kuryer reytingi", example = "4.9")
        private Double rating;

        @Schema(description = "Kuryer joriy koordinatasi (lat)", example = "41.2910")
        private Double currentLatitude;

        @Schema(description = "Kuryer joriy koordinatasi (lon)", example = "69.2095")
        private Double currentLongitude;

        @Schema(description = "Yo'nalish burchagi (daraja 0-360)", example = "185.4")
        private Float bearing;

        @Schema(description = "Tezlik km/soat", example = "38.5")
        private Float speedKmh;

        @Schema(description = "Oxirgi yangilangan vaqti")
        private Instant lastUpdatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Kuzatuv metrikalari (masofa va vaqt)")
    public static class TrackingMetricsDto {
        @Schema(description = "Mijozgacha qolgan masofa (metrda)", example = "950.0")
        private Double distanceMeters;

        @Schema(description = "Mijozgacha qolgan masofa (km da)", example = "0.95")
        private Double distanceKm;

        @Schema(description = "Taxminiy yetib kelish vaqti (daqiqa)", example = "6")
        private Integer etaMinutes;

        @Schema(description = "500m masofada yaqinlashganmi", example = "false")
        private Boolean isNearby;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Kuryerdan mijozgacha avtomobil yo'l marshruti")
    public static class OrderRouteResponse {
        @Schema(description = "Buyurtma ID", example = "10284")
        private Long orderId;

        @Schema(description = "Umumiy marshrut masofasi (metrda)", example = "1420.0")
        private Double totalDistanceMeters;

        @Schema(description = "Umumiy yetib kelish vaqti (soniyada)", example = "480")
        private Integer totalDurationSeconds;

        @Schema(description = "Marshrut tavsifi", example = "Bunyodkor shoh ko'chasi orqali")
        private String summary;

        @Schema(description = "Google Encoded Polyline satri", example = "q`_rF_`vfO{c@..._pA")
        private String encodedPolyline;

        @Schema(description = "Marshrutning koordinatali nuqtalari ketma-ketligi")
        private List<RoutePointDto> waypoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Marshrut nuqtasi")
    public static class RoutePointDto {
        @Schema(description = "Kenglik (lat)", example = "41.2910")
        private Double latitude;

        @Schema(description = "Uzunlik (lon)", example = "69.2095")
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "WebSocket orqali real-time uzatiladigan jonli kuzatuv xabari")
    public static class OrderLiveTrackingWsMessage {
        private Long orderId;
        private String status;
        private Double courierLatitude;
        private Double courierLongitude;
        private Float bearing;
        private Float speedKmh;
        private Double distanceMeters;
        private Integer etaMinutes;
        private Boolean isNearby;
        private Instant timestamp;
    }
}
