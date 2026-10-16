package uz.gidrogo.modules.superadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.Granularity;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.modules.farm.ActivationStatus;
import uz.gidrogo.modules.farm.BossActivationStatus;
import uz.gidrogo.modules.farm.FarmService;
import uz.gidrogo.modules.farm.FarmStatus;
import uz.gidrogo.modules.farm.dto.FarmDtos.*;
import uz.gidrogo.modules.superadmin.dto.SuperAdminDtos.*;

import java.time.LocalDate;
import java.util.List;

/**
 * SuperAdmin API. RBAC: /api/superadmin/** — faqat SUPER_ADMIN (SecurityConfig, server tomonda).
 * Sanalar: YYYY-MM-DD, kun chegaralari Asia/Tashkent. Ro'yxatlar: page ≥ 0, size 1–100, sort "field,asc|desc".
 * Sana oralig'i uchun from/to; startDate/endDate — eski nomlar (moslik uchun) qabul qilinadi.
 */
@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
@Tag(name = "SuperAdmin API", description = "SuperAdmin markaziy boshqaruv veb paneli uchun API lar")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final FarmService farmService;

    // ==========================================
    // DASHBOARD VA STATISTIKA (BE-10, BE-15)
    // ==========================================

    @GetMapping("/dashboard")
    @Operation(summary = "Global operatsion KPI + tanlangan davr va o'sish % (growthRate). Global tushum yo'q (TZ §5.5)")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @Parameter(description = "today, this_week, this_month, this_year") @RequestParam(required = false) String period,
            @Parameter(description = "Davr boshi (standart: to − 29 kun)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Davr oxiri (standart: bugun)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] range = uz.gidrogo.common.TashkentTime.resolveRange(period, null, startDate, endDate, from, to);
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getDashboard(range[0], range[1])));
    }

    @GetMapping("/statistics/growth")
    @Operation(summary = "O'sish seriyasi: yangi mijozlar, faol mijozlar, buyurtmalar, yangi fermalar (TZ §5.3, §11)")
    public ResponseEntity<ApiResponse<List<GrowthPoint>>> getGrowth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "MONTH") Granularity granularity) {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getGrowthSeries(from, to, granularity)));
    }

    @GetMapping("/statistics/regions")
    @Operation(summary = "Hududlar (farms.city) bo'yicha: faol/bloklangan/nofaol fermalar, davrdagi buyurtmalar va o'sish %")
    public ResponseEntity<ApiResponse<RegionalDistributionResponse>> getRegionalDistribution(
            @Parameter(description = "month, quarter, year") @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Hudud nomi (farms.city)") @RequestParam(required = false) String region,
            @Parameter(description = "Hudud ID yoki nomi") @RequestParam(required = false) String regionId) {
        LocalDate[] range = uz.gidrogo.common.TashkentTime.resolveRange(period, null, startDate, endDate, from, to);
        String targetRegion = region != null && !region.isBlank() ? region : regionId;
        return ResponseEntity.ok(ApiResponse.ok(
                superAdminService.getRegionalDistribution(range[0], range[1], targetRegion)));
    }

    @GetMapping("/statistics/orders-by-status")
    @Operation(summary = "Holatlar bo'yicha buyurtmalar soni (created_at bo'yicha, TZ §11.2)")
    public ResponseEntity<ApiResponse<OrdersByStatusResponse>> getOrdersByStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long farmId) {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getOrdersByStatus(from, to, farmId)));
    }

    @GetMapping("/orders/daily-dynamics")
    @Operation(summary = "Kunma-kun dinamika. Tushum faqat COMPLETED (completed_at kuni) va faqat farmId bilan qaytariladi")
    public ResponseEntity<ApiResponse<DailyDynamicsResponse>> getDailyDynamics(
            @Parameter(description = "Kunlar soni, 1–366") @RequestParam(required = false, defaultValue = "7") @Min(1) @Max(366) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long farmId) {
        return ResponseEntity.ok(ApiResponse.ok("Kunma-kun dinamika ma'lumotlari",
                superAdminService.getDailyDynamics(days, startDate, endDate, farmId)));
    }

    // ==========================================
    // FERMA EGALARI (BE-03, BE-16)
    // ==========================================

    @GetMapping("/bosses")
    @Operation(summary = "Bosslar ro'yxati (sahifalangan) va summary (ro'yxat bilan bir xil filtr)")
    public ResponseEntity<ApiResponse<BossListResponse>> getBosses(
            @RequestParam(required = false) String search,
            @Parameter(description = "ACTIVE | BLOCKED") @RequestParam(required = false) String status,
            @RequestParam(required = false) Long farmId,
            @RequestParam(required = false) BossActivationStatus activationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "createdAt | fullName | id, masalan: createdAt,desc") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.ok(
                superAdminService.getBosses(search, status, farmId, activationStatus, page, size, sort)));
    }

    @PutMapping("/bosses/{id}")
    @Operation(summary = "Boss ma'lumotlarini tahrirlash. farmId: 404 FARM_NOT_FOUND, 409 FARM_ALREADY_HAS_BOSS")
    public ResponseEntity<ApiResponse<BossItemResponse>> updateBoss(
            @PathVariable Long id,
            @Valid @RequestBody BossUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Boss ma'lumotlari yangilandi", superAdminService.updateBoss(id, request)));
    }

    @PatchMapping("/bosses/{id}/toggle-status")
    @Operation(summary = "Boss hisob holatini almashtirish (ACTIVE ↔ BLOCKED)")
    public ResponseEntity<ApiResponse<BossItemResponse>> toggleBossStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Status muvaffaqiyatli o'zgartirildi", superAdminService.toggleBossStatus(id)));
    }

    @DeleteMapping("/bosses/{id}")
    @Operation(summary = "Bossni o'chirish (soft delete; ferma bog'lanishi uziladi)")
    public ResponseEntity<ApiResponse<Void>> deleteBoss(@PathVariable Long id) {
        superAdminService.deleteBoss(id);
        return ResponseEntity.ok(ApiResponse.ok("Boss o'chirildi", null));
    }

    // ==========================================
    // FERMALAR (BE-02, BE-11, BE-14, BE-17, BE-22)
    // ==========================================

    @PostMapping("/farms")
    @Operation(summary = "Yangi ferma va uning Bossi (bitta tranzaksiyada). 409 PHONE_ALREADY_EXISTS / FARM_NAME_EXISTS, 422 PASSWORD_POLICY_VIOLATION")
    public ResponseEntity<ApiResponse<FarmResponse>> createFarm(@Valid @RequestBody FarmCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ferma muvaffaqiyatli yaratildi", farmService.createFarm(request)));
    }

    @GetMapping("/farms")
    @Operation(summary = "Fermalar ro'yxati (sahifalangan): qidiruv (nom, manzil, telefon), holat, hudud")
    public ResponseEntity<ApiResponse<PageResponse<FarmResponse>>> getFarms(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) FarmStatus status,
            @Parameter(description = "Hudud (farms.city)") @RequestParam(required = false) String region,
            @Parameter(description = "Hudud ID yoki nomi") @RequestParam(required = false) String regionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "id | name | createdAt | status | city") @RequestParam(required = false) String sort) {
        String targetRegion = region != null && !region.isBlank() ? region : regionId;
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarms(search, status, targetRegion, page, size, sort)));
    }

    @GetMapping("/farms/{id}")
    @Operation(summary = "Ferma tafsiloti: real statistika, oxirgi 7 kun va 4 hafta grafiklari, xodimlar, mahsulotlar")
    public ResponseEntity<ApiResponse<FarmDetailResponse>> getFarmById(
            @Parameter(description = "Ferma ID (raqam yoki farm-008 ko'rinishida)") @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarmDetail(parseFarmId(id))));
    }

    @GetMapping("/farms/{id}/analytics")
    @Operation(summary = "Ferma analitikasi: tushum, tara, xarajat, foyda, buyurtmalar, yangi mijozlar seriyasi va mahsulotlar samaradorligi")
    public ResponseEntity<ApiResponse<FarmAnalyticsResponse>> getFarmAnalytics(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") Granularity granularity) {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarmAnalytics(parseFarmId(id), from, to, granularity)));
    }

    @PatchMapping("/farms/{id}/status")
    @Operation(summary = "Ferma holati: ACTIVE | BLOCKED | INACTIVE. Bloklangan/nofaol fermaga yangi buyurtma 409")
    public ResponseEntity<ApiResponse<FarmResponse>> updateFarmStatus(
            @PathVariable String id,
            @Valid @RequestBody FarmStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ferma statusi yangilandi",
                farmService.updateFarmStatus(parseFarmId(id), request.getStatus(), request.getReason())));
    }

    // ==========================================
    // BOSS TASDIQLASH SO'ROVLARI (BE-04, BE-05)
    // ==========================================

    @GetMapping("/activation-requests")
    @Operation(summary = "Boss tasdiqlash so'rovlari (arxiv bilan) va summary {pending, approved, rejected}")
    public ResponseEntity<ApiResponse<ActivationRequestPage>> getActivationRequests(
            @Parameter(description = "PENDING | APPROVED | REJECTED (bo'sh — barchasi)") @RequestParam(required = false) ActivationStatus status,
            @Parameter(description = "today, this_week, this_month, this_year") @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "requestedAt | decidedAt | id") @RequestParam(required = false) String sort) {
        LocalDate[] range = (period != null || startDate != null || endDate != null || from != null || to != null)
                ? uz.gidrogo.common.TashkentTime.resolveRange(period, null, startDate, endDate, from, to)
                : new LocalDate[]{null, null};
        return ResponseEntity.ok(ApiResponse.ok(farmService.getActivationRequests(
                status, range[0], range[1], page, size, sort)));
    }

    @PostMapping("/activation-requests/{id}/decision")
    @Operation(summary = "Qaror: APPROVED | REJECTED. REJECTED da sabab 10–500 belgi (422). Qaror qilingan so'rov → 409 REQUEST_ALREADY_DECIDED")
    public ResponseEntity<ApiResponse<ActivationRequestResponse>> decideActivation(
            @PathVariable Long id,
            @Valid @RequestBody ActivationDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Qaror qabul qilindi", farmService.decideBossActivation(id, request)));
    }

    // ==========================================
    // AUDIT LOG (BE-12)
    // ==========================================

    @GetMapping("/audit-log")
    @Operation(summary = "Audit Log: action (bir nechta), entityType, status, from/to, search, sahifalash")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogItem>>> getAuditLog(
            @Parameter(description = "Masalan: action=FARM_BLOCKED&action=BOSS_UPDATED yoki vergul bilan") @RequestParam(required = false) List<String> action,
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Natija: SUCCESS | FAILED | WARNING") @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "createdAt | action | id") @RequestParam(required = false) String sort) {
        LocalDate[] range = (startDate != null || endDate != null || from != null || to != null)
                ? uz.gidrogo.common.TashkentTime.resolveRange(null, null, startDate, endDate, from, to)
                : new LocalDate[]{null, null};
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getAuditLogs(
                action, entityType, status, range[0], range[1], search, page, size, sort)));
    }

    // ==========================================
    // PROFIL VA XAVFSIZLIK (BE-21)
    // ==========================================

    @GetMapping("/profile")
    @Operation(summary = "SuperAdmin profili")
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok("SuperAdmin profili", superAdminService.getSuperAdminProfile()));
    }

    @PutMapping("/profile")
    @Operation(summary = "SuperAdmin profilini yangilash (audit PROFILE_UPDATE)")
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> updateProfile(
            @Valid @RequestBody SuperAdminProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profil muvaffaqiyatli saqlandi", superAdminService.updateSuperAdminProfile(request)));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Parolni almashtirish: joriy parol, yangi = tasdiq, siyosat 8–50 harf+raqam (422). Barcha sessiyalar bekor qilinadi")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        superAdminService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Parol muvaffaqiyatli yangilandi", null));
    }

    @Deprecated
    @GetMapping("/settings/profile")
    @Operation(summary = "ESKIRGAN: GET /api/superadmin/profile dan foydalaning", deprecated = true)
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> getProfileDeprecated() {
        return getProfile();
    }

    @Deprecated
    @PutMapping("/settings/profile")
    @Operation(summary = "ESKIRGAN: PUT /api/superadmin/profile dan foydalaning", deprecated = true)
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> updateProfileDeprecated(
            @Valid @RequestBody SuperAdminProfileUpdateRequest request) {
        return updateProfile(request);
    }

    @Deprecated
    @PostMapping("/settings/change-password")
    @Operation(summary = "ESKIRGAN: POST /api/superadmin/change-password dan foydalaning", deprecated = true)
    public ResponseEntity<ApiResponse<Void>> changePasswordDeprecated(@Valid @RequestBody ChangePasswordRequest request) {
        return changePassword(request);
    }

    /** "8" yoki "farm-008" qabul qilinadi; boshqa qiymat → 400 INVALID_PARAMETER. */
    private static Long parseFarmId(String idStr) {
        String s = idStr == null ? "" : idStr.trim();
        if (s.matches("\\d{1,18}")) return Long.parseLong(s);
        if (s.matches("(?i)farm-\\d{1,18}")) return Long.parseLong(s.substring(5));
        throw new uz.gidrogo.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Noto'g'ri ferma ID: raqam yoki farm-008 ko'rinishida bo'lishi kerak",
                List.of(new ApiResponse.FieldErrorItem("id", "Noto'g'ri ferma ID")), null);
    }

    private static LocalDate first(LocalDate a, LocalDate b) {
        return a != null ? a : b;
    }
}
