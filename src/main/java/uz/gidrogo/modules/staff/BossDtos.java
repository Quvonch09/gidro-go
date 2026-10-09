package uz.gidrogo.modules.staff;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class BossDtos {

    // ==========================================
    // 1. FERMA PASPORTI VA LABORATORIYA TAHLILI
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmTechnicalPassportResponse {
        @JsonProperty("farm_name")
        private String farmName;

        @JsonProperty("inn")
        private String inn;

        @JsonProperty("license_number")
        private String licenseNumber;

        @JsonProperty("address")
        private String address;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("daily_capacity_liters")
        private Long dailyCapacityLiters;

        @JsonProperty("max_capacity_liters")
        private Long maxCapacityLiters;

        @JsonProperty("filter_type")
        private String filterType;

        @JsonProperty("laboratory")
        private LaboratoryAnalysis laboratory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaboratoryAnalysis {
        @JsonProperty("tds_ppm")
        private String tdsPpm;

        @JsonProperty("ph_level")
        private String phLevel;

        @JsonProperty("hardness")
        private String hardness;

        @JsonProperty("last_tested_at")
        private Instant lastTestedAt;

        @JsonProperty("certificate")
        private String certificate;
    }

    // ==========================================
    // 2. DAVRIY BIZNES O'SISHI VA ANALITIKA
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessGrowthResponse {
        private List<String> categories;

        @JsonProperty("new_clients")
        private List<Long> newClients;

        @JsonProperty("orders_volume")
        private List<Long> ordersVolume;

        @JsonProperty("revenue_growth_percent")
        private Double revenueGrowthPercent;

        @JsonProperty("retention_rate_percent")
        private Double retentionRatePercent;
    }

    // ==========================================
    // 3. MOLIYAVIY GRAFIK DINAMIKASI
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceChartItem {
        private String date;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal profit;
    }

    // ==========================================
    // 4. MAHSULOTLAR SOTUV SUMMARY
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSalesSummaryResponse {
        private Long productId;
        private String name;
        private long soldCount;
        private BigDecimal revenue;
        private BigDecimal stock;
    }

    // ==========================================
    // 5. CRM MIJOZLAR TOIFALARI (SEGMENTS)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientSegmentsResponse {
        private long b2cCount;
        private long b2bCount;
        private long wholesaleCount;
        private long total;
    }

    // ==========================================
    // 6. SAVDO VA REALIZATSIYA DASHBOARD (Image 1 /boss/savdo)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesRealizationSummaryResponse {
        private double totalWaterVolumeLiters;
        private long totalBottlesSold;
        private double waterVolumeChangePercent;
        private long completedOrders;
        private long totalOrders;
        private double completionPercent;
        private long pendingOrders;
        private long deliveringOrders;
        private TopProductSummary topProduct;
        private List<WeeklyDynamicsItem> weeklyDynamics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProductSummary {
        private String name;
        private double soldLiters;
        private double sharePercent;
        private long soldCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyDynamicsItem {
        private String day; // "Dush", "Sesh", ...
        private String date;
        private double waterVolumeKl;
        private long ordersCount;
    }

    // ==========================================
    // 7. XODIMLAR MONITORINGI SUMMARY (Image 4 /boss/xodimlar)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffSummaryResponse {
        private long totalStaffCount;
        private long managersCount;
        private long couriersCount;
        private long activeOnShiftCount;
        private double deliveryDisciplinePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedStaffItemResponse {
        private Long id;
        private Long farmId;
        private String fullName;
        private String phone;
        private String role;
        private String roleLabel;
        private String status;
        private String statusLabel;
        private boolean isOnline;
        private String regionName;
        private BigDecimal todayDeliveredBottles;
        private String vehicleModel;
        private String vehiclePlateNumber;
        private String avatarUrl;
        private Instant createdAt;
    }
}
