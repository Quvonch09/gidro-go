package uz.gidrogo.modules.superadmin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.modules.farm.BossActivationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class SuperAdminDtos {

    // ==========================================
    // 0. DASHBOARD (BE-10, TZ §5)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private long totalFarms;
        private long activeFarms;
        private long blockedFarms;
        private long inactiveFarms;
        private long totalCouriers;
        private long totalClients;
        /** activeOrders + completedOrders + cancelledOrders + returnedOrders = totalOrders */
        private long totalOrders;
        private long activeOrders;
        private long completedOrders;
        private long cancelledOrders;
        private long returnedOrders;
        private PeriodStats period;
        private Growth growth;
        /** Tanlangan davrdagi o'sish foizi (growthRate) */
        private Double growthRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodStats {
        private String from;
        private String to;
        private String previousFrom;
        private String previousTo;
        private long newClients;
        private long activeClients;
        private long orders;
        private long newFarms;
    }

    /** O'sish %: (joriy − oldingi) ÷ oldingi × 100; oldingi davr 0 bo'lsa null. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Growth {
        private Double clientsPercent;
        private Double ordersPercent;
        private Double farmsPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthPoint {
        private String periodStart;
        private String periodEnd;
        private long newClients;
        private long activeClients;
        private long orders;
        private long newFarms;
    }

    // ==========================================
    // 1. STATISTIKA (BE-15, TZ §11)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionalDistributionResponse {
        private String from;
        private String to;
        private long totalFarms;
        private List<RegionItem> regions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionItem {
        /** farms.city; belgilanmagan bo'lsa "Belgilanmagan". */
        private String regionName;
        private long totalFarms;
        private long activeFarms;
        private long blockedFarms;
        private long inactiveFarms;
        /** totalFarms ning barcha fermalarga ulushi, %. */
        private double percentage;
        private long ordersInPeriod;
        private long ordersPreviousPeriod;
        private Double growthPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrdersByStatusResponse {
        private String from;
        private String to;
        private Long farmId;
        private long total;
        private List<StatusCount> statuses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
    }

    // ==========================================
    // 2. FERMA EGALARI (BE-03, BE-16)
    // ==========================================
    /** Ro'yxat bilan bir xil manba va filtrdan hisoblanadi: total = totalElements. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossSummaryCards {
        private long total;
        private long active;
        private long blocked;
        /** Oxirgi 30 kunda qo'shilganlar. */
        private long recentlyAdded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossItemResponse {
        private Long id;
        private String fullName;
        private String phone;
        private String avatarUrl;
        /** farms.boss_user_id dan (yagona manba). */
        private Long farmId;
        private String farmCode;
        private String farmName;
        /** Hisob holati: ACTIVE | BLOCKED */
        private String status;
        private boolean active;
        private String statusLabel;
        /** Faollashtirish holati: NOT_REQUESTED | PENDING | APPROVED | REJECTED */
        private BossActivationStatus activationStatus;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossListResponse {
        private BossSummaryCards summary;
        private List<BossItemResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /** PUT /bosses/{id}. Bo'sh maydonlar o'zgartirilmaydi. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossUpdateRequest {
        @Size(min = 2, max = 100, message = "Ism 2–100 belgidan iborat bo'lishi kerak")
        private String fullName;
        @Pattern(regexp = "^\\+?[0-9 ()-]{9,20}$", message = "Telefon formati noto'g'ri. Masalan: +998901234567")
        private String phone;
        private String password;
        private Long farmId;
        @Pattern(regexp = "^(ACTIVE|BLOCKED)$", message = "Status faqat ACTIVE yoki BLOCKED bo'lishi mumkin")
        private String status;
        @Size(max = 500)
        private String avatarUrl;
    }

    // ==========================================
    // 3. AUDIT LOG (BE-12)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogItem {
        private Long id;
        private Long actorId;
        private String actorName;
        private String action;
        private String entityType;
        private Long entityId;
        /** {"old": ..., "new": ..., ...} */
        private JsonNode metadata;
        private String ipAddress;
        private String userAgent;
        /** Natija: SUCCESS | FAILED | WARNING */
        private String status;
        private Instant createdAt;
    }

    // ==========================================
    // 4. SOZLAMALAR VA PROFIL (BE-21)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class SuperAdminProfileResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String fullName;
        private String phone;
        private String email;
        private String role;
        private String avatarUrl;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuperAdminProfileUpdateRequest {
        @Size(max = 100)
        private String firstName;
        @Size(max = 100)
        private String lastName;
        @Size(max = 200)
        private String fullName;
        @Pattern(regexp = "^\\+?[0-9 ()-]{9,20}$", message = "Telefon formati noto'g'ri. Masalan: +998901234567")
        private String phone;
        @Email(message = "Email formati noto'g'ri")
        @Size(max = 255)
        private String email;
        @Size(max = 500)
        private String avatarUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank(message = "Joriy parol kiritilishi shart")
        private String currentPassword;

        @NotBlank(message = "Yangi parol kiritilishi shart")
        private String newPassword;

        @NotBlank(message = "Yangi parolni tasdiqlash kiritilishi shart")
        private String confirmPassword;
    }

    // ==========================================
    // 5. KUNMA-KUN DINAMIKA (BE-01, BE-10)
    // ==========================================
    /**
     * revenue/totalRevenue faqat farmId berilganda qaytariladi (TZ §5.5 — global tushum yo'q).
     * Tushum — faqat COMPLETED, completed_at kuni bo'yicha (Asia/Tashkent). Tara — alohida.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DailyDynamicsResponse {
        private Long farmId;
        private String startDate;
        private String endDate;
        private long totalOrders;
        private long completedOrders;
        private long cancelledOrders;
        private BigDecimal totalRevenue;
        private BigDecimal totalTaraRevenue;
        private List<DailyDynamicsItem> days;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DailyDynamicsItem {
        private String date;           // "2026-10-01"
        private String dayOfWeek;      // "Payshanba"
        /** Shu kuni yaratilgan buyurtmalar. */
        private long totalOrders;
        /** Shu kuni COMPLETED bo'lganlar (completed_at bo'yicha). */
        private long completedOrders;
        /** Shu kuni yaratilib, hozir CANCELLED holatidagilar. */
        private long cancelledOrders;
        /** Shu kuni yaratilgan (bekor qilinmagan) buyurtmalar summasi — TUSHUM EMAS. */
        private BigDecimal orderedAmount;
        /** Tushum (faqat farmId bilan). */
        private BigDecimal revenue;
        private BigDecimal taraRevenue;
    }
}
