package uz.gidrogo.modules.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.modules.farm.ActivationStatus;
import uz.gidrogo.modules.farm.FarmStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class FarmDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmCreateRequest {
        @NotBlank(message = "Ferma nomi kiritilishi shart")
        private String name;

        @NotBlank(message = "Ferma telefoni kiritilishi shart")
        private String phone;

        private String address;
        private Double latitude;
        private Double longitude;
        private String logoUrl;

        // Boss ma'lumotlari
        @NotBlank(message = "Boss ismi kiritilishi shart")
        private String bossFullName;

        @NotBlank(message = "Boss telefoni kiritilishi shart")
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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationDecisionRequest {
        @NotNull(message = "Qaror (APPROVED yoki REJECTED) kiritilishi shart")
        private ActivationStatus status;

        private String rejectReason; // REJECTED bo'lsa majburiy
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

        // 2. Yuqori 4 ta statistika kartalari (Figma dagi ko'rsatkichlar)
        private StatisticsCards statistics;

        // 3. Analitika va savdo dinamikasi grafigi (Haftalik: Dush, Sesh, Chor, Pay, Jum, Shan, Yak)
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsCards {
        // Karta 1: JAMI BUYURTMALAR
        private long totalOrders;
        private double ordersGrowthPercentage; // 12.0
        private String ordersGrowthText;       // "+12% o'tgan oydan"

        // Karta 2: FAOL JARAYONDA
        private long activeOrders;
        private String activeOrdersText;       // "Yetkazib berilmoqda"

        // Karta 3: TUGALLANGAN
        private long completedOrders;
        private double successRatePercentage;  // 98.5
        private String successRateText;        // "98.5% muvaffaqiyat"

        // Karta 4: JAMI DAROMAD
        private BigDecimal totalRevenue;
        private String formattedRevenue;       // "$12,450" yoki "12,450 so'm"
        private double revenueGrowthPercentage;// 18.4
        private String revenueGrowthText;      // "+18.4% o'sish"

        // Frontend o'zgaruvchilari bilan to'liq mos kelishi uchun qo'shimcha aliaslar:
        private long jamiBuyurtmalar;
        private long faolJarayonda;
        private long tugallangan;
        private BigDecimal jamiDaromad;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataPoint {
        private String day;         // "Dush", "Sesh", "Chor", "Pay", "Jum", "Shan", "Yak"
        private String dayName;     // "Dushanba", "Seshanba", ...
        private String date;        // "2026-09-14"
        private long ordersCount;   // Buyurtmalar soni
        private BigDecimal revenue; // Daromad summasi

        // Frontend grafik seriyalari nomlari (Figma dagi nomlar):
        private long buyurtmalar;
        private BigDecimal daromad;
    }
}
