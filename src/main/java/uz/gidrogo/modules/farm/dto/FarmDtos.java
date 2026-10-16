package uz.gidrogo.modules.farm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.common.Granularity;
import uz.gidrogo.modules.farm.ActivationDecision;
import uz.gidrogo.modules.farm.ActivationStatus;
import uz.gidrogo.modules.farm.FarmStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class FarmDtos {

    /** Telefon: raqamlar, bo'sh joy, qavs, chiziqcha va boshida "+" (normallashtirilgach +998XXXXXXXXX bo'lishi shart). */
    public static final String PHONE_INPUT_REGEX = "^\\+?[0-9 ()-]{9,20}$";

    /**
     * POST /api/superadmin/farms (BE-17 / CHK-05). Koordinata chegaralari frontend bilan bir xil: lat 37–46, lon 55–74.
     * Boss paroli — yagona parol siyosati (8–50, harf + raqam) → buzilsa 422.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmCreateRequest {
        @NotBlank(message = "Ferma nomi kiritilishi shart")
        @Size(min = 2, max = 100, message = "Ferma nomi 2–100 belgidan iborat bo'lishi kerak")
        private String name;

        @NotBlank(message = "Ferma telefoni kiritilishi shart")
        @Pattern(regexp = PHONE_INPUT_REGEX, message = "Telefon formati noto'g'ri. Masalan: +998901234567")
        private String phone;

        @NotBlank(message = "Manzil kiritilishi shart")
        @Size(max = 500, message = "Manzil 500 belgidan oshmasligi kerak")
        private String address;

        @NotNull(message = "Kenglik (latitude) kiritilishi shart")
        @DecimalMin(value = "37.0", message = "Kenglik 37 dan 46 gacha bo'lishi kerak (O'zbekiston)")
        @DecimalMax(value = "46.0", message = "Kenglik 37 dan 46 gacha bo'lishi kerak (O'zbekiston)")
        private Double latitude;

        @NotNull(message = "Uzunlik (longitude) kiritilishi shart")
        @DecimalMin(value = "55.0", message = "Uzunlik 55 dan 74 gacha bo'lishi kerak (O'zbekiston)")
        @DecimalMax(value = "74.0", message = "Uzunlik 55 dan 74 gacha bo'lishi kerak (O'zbekiston)")
        private Double longitude;

        @Size(max = 500)
        private String logoUrl;

        @Size(max = 100)
        private String city;

        @Size(max = 100)
        private String district;

        // Boss ma'lumotlari
        @NotBlank(message = "Boss ismi kiritilishi shart")
        @Size(min = 2, max = 100, message = "Boss ismi 2–100 belgidan iborat bo'lishi kerak")
        private String bossFullName;

        @NotBlank(message = "Boss telefoni kiritilishi shart")
        @Pattern(regexp = PHONE_INPUT_REGEX, message = "Telefon formati noto'g'ri. Masalan: +998901234567")
        private String bossPhone;

        @NotBlank(message = "Boss doimiy paroli kiritilishi shart")
        private String bossPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmStatusUpdateRequest {
        @NotNull(message = "Status kiritilishi shart")
        private FarmStatus status; // ACTIVE, BLOCKED, INACTIVE

        @Size(max = 500)
        private String reason;
    }

    /**
     * POST /activation-requests/{id}/decision (BE-04 / BUG-006).
     * status — faqat APPROVED | REJECTED (boshqa qiymat → 400).
     * REJECTED da rejectReason trim'dan keyin 10–500 belgi (aks holda → 422).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationDecisionRequest {
        @NotNull(message = "Qaror (APPROVED yoki REJECTED) kiritilishi shart")
        private ActivationDecision status;

        private String rejectReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmResponse {
        private Long id;
        private String name;
        private String phone;
        private String address;
        private String city;
        private String district;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String logoUrl;
        private FarmStatus status;
        private Long bossUserId;
        private String bossFullName;
        private String bossPhone;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationRequestResponse {
        private Long id;
        private Long farmId;
        private String farmName;
        private Long bossUserId;
        private String bossFullName;
        private String bossPhone;
        private ActivationStatus status;
        private String rejectReason;
        private Instant requestedAt;
        private Instant decidedAt;
        private Long decidedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationSummary {
        private long pending;
        private long approved;
        private long rejected;
    }

    /** GET /activation-requests: sahifa + umumiy son (filtrsiz). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationRequestPage {
        private ActivationSummary summary;
        private List<ActivationRequestResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmDetailResponse {
        // 1. Ferma ma'lumotlari
        private Long id;
        private String name;
        private String phone;
        private String address;
        private String city;
        private String district;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String logoUrl;
        private FarmStatus status;
        private Instant createdAt;

        // Boss ma'lumotlari
        private Long bossUserId;
        private String bossFullName;
        private String bossPhone;
        private String bossStatus;

        // 2. Statistika (real hisob; ma'lumot bo'lmasa 0 yoki null)
        private StatisticsCards statistics;

        // 3. Grafiklar: weeklyChart — bugun bilan tugaydigan oxirgi 7 kun; monthlyChart — oxirgi 4 hafta
        private List<ChartDataPoint> weeklyChart;
        private List<ChartDataPoint> monthlyChart;

        // 4. Xodimlar va boshqa agregat ma'lumotlar
        private int totalStaff;
        private int totalManagers;
        private int totalCouriers;
        private int activeCouriers;
        private long totalClients;

        // 5. Mahsulotlar ro'yxati
        private List<uz.gidrogo.modules.product.dto.ProductDtos.ProductResponse> products;

        // 6. Xodimlar ro'yxati
        private List<uz.gidrogo.modules.staff.StaffDtos.StaffResponse> staff;

        // 7. Oxirgi buyurtmalar
        private List<uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse> recentOrders;
    }

    /**
     * Ferma statistikasi (BE-02, BE-11). Buyurtma sonlari — butun davr bo'yicha; o'sish foizlari —
     * oxirgi 30 kun oldingi 30 kun bilan (oldingi davr 0 bo'lsa null). Pul — so'mda, valyuta belgisisiz.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsCards {
        private long totalOrders;
        private long activeOrders;
        private long completedOrders;
        private long cancelledOrders;
        private long returnedOrders;
        /** completed ÷ total × 100; buyurtma bo'lmasa null. */
        private Double successRatePercentage;
        /** Oxirgi 30 kunda yaratilgan buyurtmalar oldingi 30 kunga nisbatan. */
        private Double ordersGrowthPercentage;

        /** Tushum (faqat COMPLETED, suv/mahsulot qismi) — butun davr. */
        private BigDecimal totalRevenue;
        /** Tara tushumi — alohida qator, butun davr. */
        private BigDecimal taraRevenue;
        /** Manager xarajatlari — butun davr. */
        private BigDecimal expenses;
        /** totalRevenue − expenses (tara kirmaydi). */
        private BigDecimal profit;
        /** Oxirgi 30 kun tushumi oldingi 30 kunga nisbatan. */
        private Double revenueGrowthPercentage;
        /** BE-11 nomi bilan: revenueGrowthPercentage bilan bir xil (30 kunlik tushum o'sishi). */
        private Double monthlyGrowthPercent;

        private long activeManagers;
        private long activeCouriers;
        private long clients;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataPoint {
        private String day;          // "Dush" ... yoki "1-hafta"
        private String dayName;      // "Dushanba" ...
        private String date;         // oraliq boshi, YYYY-MM-DD
        private String endDate;      // oraliq oxiri, YYYY-MM-DD
        private long ordersCount;    // shu oraliqda yaratilgan buyurtmalar
        private long completedOrders;// shu oraliqda yakunlanganlar
        private BigDecimal revenue;  // tushum (COMPLETED, completed_at bo'yicha)
        private BigDecimal taraRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsPoint {
        private String periodStart;
        private String periodEnd;
        private BigDecimal revenue;
        private BigDecimal taraRevenue;
        private BigDecimal expenses;
        private BigDecimal profit;
        private long orders;
        private long completedOrders;
        private long newClients;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPerformance {
        private Long productId;
        private String name;
        private BigDecimal soldQty;
        private BigDecimal revenue;
    }

    /** GET /api/superadmin/farms/{id}/analytics (BE-11). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FarmAnalyticsResponse {
        private Long farmId;
        private String from;
        private String to;
        private Granularity granularity;
        private AnalyticsPoint totals;
        private List<AnalyticsPoint> series;
        private List<ProductPerformance> products;
    }
}
