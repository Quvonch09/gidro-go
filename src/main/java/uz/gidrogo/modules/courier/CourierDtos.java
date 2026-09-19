package uz.gidrogo.modules.courier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class CourierDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationUpdateRequest {
        @NotNull(message = "Latitude kiritilishi shart")
        private Double latitude;

        @NotNull(message = "Longitude kiritilishi shart")
        private Double longitude;

        private Float accuracy; // metr (ixtiyoriy)
        private Float bearing;  // yo'nalish (ixtiyoriy)
        private Float speed;    // m/s (ixtiyoriy)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationUpdateResponse {
        private String status;      // OK
        private boolean nearbyTriggered; // Agar biron buyurtmaga 500m yaqin bo'lsa true
        private Long nearbyOrderId; // Qaysi buyurtmaga yaqinlashdi
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverRequest {
        @NotNull(message = "Yetkazilganlik rasmi URL kiritilishi shart")
        private String photoUrl;

        private Integer emptyBottlesReturned; // Qaytarilgan bo'sh shishalar soni
        private String clientNote;            // Mijozdan olgan izoh
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashCollectedRequest {
        @NotNull(message = "Olingan summa kiritilishi shart")
        private BigDecimal amountCollected; // Haqiqatda olingan naqd pul
        private String note;                // Izoh (ixtiyoriy)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectOrderRequest {
        @NotBlank(message = "Rad etish sababi kiritilishi shart")
        private String reason; // CLIENT_UNREACHABLE, WRONG_ADDRESS, OVERLOADED, OTHER
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusToggleRequest {
        private Boolean online; // true = ONLINE, false = OFFLINE
        private String status;  // "ONLINE" yoki "OFFLINE"

        public Boolean isOnlineEffective() {
            if (online != null) return online;
            if (status != null) return "ONLINE".equalsIgnoreCase(status);
            return true;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceTokenRequest {
        @NotBlank(message = "FCM token kiritilishi shart")
        private String fcmToken;

        private String deviceType; // ANDROID, IOS (ixtiyoriy)
    }

    // ── Response DTOs ────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleInfoDto {
        private String model;
        private String plateNumber;
        private Integer maxCapacity;
        private String licenseNumber;
        private String passportSerial;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierProfileResponse {
        private Long id;
        private String fullName;
        private String phone;
        private String avatarUrl;
        private String status;       // ACTIVE, BLOCKED
        private Boolean isOnline;    // Redis'dan olinadi
        private Long farmId;
        private String farmName;
        private Double rating;
        private Double latitude;
        private Double longitude;
        private Instant lastSeenAt;
        // Bugungi statistika
        private long todayCompleted;
        private BigDecimal todayCash;
        private BigDecimal todayOnline;
        private BigDecimal todayTotal;
        private BigDecimal vehicleStock; // Mashinadagi suv qoldig'i
        // Avtomobil va haydovchi ma'lumotlari (ob'ekt va ildiz maydonlari)
        private String vehicleModel;
        private String vehiclePlateNumber;
        private Integer maxCapacity;
        private String driverLicenseNumber;
        private String passportSerial;
        private VehicleInfoDto vehicle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourierDashboardResponse {
        private LocalDate date;
        private int targetOrdersCount; // Kunlik maqsadli buyurtmalar soni
        private BigDecimal targetRevenue; // Kunlik maqsadli daromad
        private long assignedOrders;
        private long completedOrders;
        private long problemOrders;
        private BigDecimal totalRevenue;
        private BigDecimal cashRevenue;
        private BigDecimal onlineRevenue;
        private BigDecimal loadedBottles;
        private BigDecimal soldBottles;
        private BigDecimal remainingBottles;
        private List<RecentOrderSummary> recentOrders; // So'nggi 5 ta buyurtma
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileUpdateRequest {
        private String phone;
        private String currentPassword;
        private String newPassword;
        private String vehicleModel;
        private String vehiclePlateNumber;
        private Integer maxCapacity;
        private String driverLicenseNumber;
        private String passportSerial;
        private String avatarUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileUpdateResponse {
        private Long id;
        private String fullName;
        private String phone;
        private String avatarUrl;
        private String vehicleModel;
        private String vehiclePlateNumber;
        private Integer maxCapacity;
        private String driverLicenseNumber;
        private String passportSerial;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockHistoryPageResponse {
        private List<StockHistoryItem> content;
        private long totalElements;
        private int totalPages;
        private int currentPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockHistoryItem {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private String location;
        private String warehouseManagerName;
        private Instant restockedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentOrderSummary {
        private Long id;
        private String orderNumber;
        private String clientName;
        private String status;
        private BigDecimal totalSum;
        private Instant createdAt;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemReasonItem {
        private String code;
        private String label; // O'zbek tilidagi nomi
    }
}
