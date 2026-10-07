package uz.gidrogo.modules.superadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.modules.farm.FarmService;
import uz.gidrogo.modules.farm.dto.FarmDtos.*;
import uz.gidrogo.modules.finance.FinanceService;
import uz.gidrogo.modules.finance.dto.FinanceDtos.FinanceSummaryResponse;
import uz.gidrogo.modules.superadmin.SuperAdminService.GlobalOperationsDashboard;
import uz.gidrogo.modules.superadmin.dto.SuperAdminDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
@Tag(name = "SuperAdmin API", description = "SuperAdmin markaziy boshqaruv veb paneli uchun API lar")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final FarmService farmService;
    private final FinanceService financeService;

    @GetMapping("/dashboard")
    @Operation(summary = "Global operatsion KPI (Faqat operatsion ko'rsatkichlar, moliyaviy umumiy yig'indi yo'q!)")
    public ResponseEntity<ApiResponse<GlobalOperationsDashboard>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getOperationalDashboard()));
    }

    // ==========================================
    // 1. HUDUDIY TAQSIMOT (REGIONAL DISTRIBUTION)
    // ==========================================
    @GetMapping("/statistics/regions")
    @Operation(summary = "Hududiy taqsimot - viloyatlar kesimida fermalar soni va foiz statistikasi")
    public ResponseEntity<ApiResponse<RegionalDistributionResponse>> getRegionalDistribution() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getRegionalDistribution()));
    }

    // ==========================================
    // 2. FERMA EGALARI (BOSSES LIST & MANAGEMENT)
    // ==========================================
    @GetMapping("/bosses")
    @Operation(summary = "Ferma egalari (Bosslar) ro'yxati, qidiruv, filtr va umumiy statistik kartochkalar")
    public ResponseEntity<ApiResponse<BossListResponse>> getBosses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long farmId) {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getBosses(search, status, farmId)));
    }

    @PutMapping("/bosses/{id}")
    @Operation(summary = "Ferma egasi ma'lumotlarini tahrirlash")
    public ResponseEntity<ApiResponse<BossItemResponse>> updateBoss(
            @PathVariable Long id,
            @RequestBody BossUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Boss ma'lumotlari yangilandi", superAdminService.updateBoss(id, request)));
    }

    @PatchMapping("/bosses/{id}/toggle-status")
    @Operation(summary = "Ferma egasi statusini almashtirish (Faol <-> Bloklangan)")
    public ResponseEntity<ApiResponse<BossItemResponse>> toggleBossStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Status muvaffaqiyatli o'zgartirildi", superAdminService.toggleBossStatus(id)));
    }

    @DeleteMapping("/bosses/{id}")
    @Operation(summary = "Ferma egasini o'chirish")
    public ResponseEntity<ApiResponse<Void>> deleteBoss(@PathVariable Long id) {
        superAdminService.deleteBoss(id);
        return ResponseEntity.ok(ApiResponse.ok("Boss o'chirildi", null));
    }

    @PostMapping("/farms")
    @Operation(summary = "Yangi ferma yaratish (Ferma ma'lumotlari va uning Rahbari bir vaqtda birdaniga kiritiladi)")
    public ResponseEntity<ApiResponse<FarmResponse>> createFarm(@Valid @RequestBody FarmCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ferma muvaffaqiyatli yaratildi", farmService.createFarm(request)));
    }

    @GetMapping("/farms")
    @Operation(summary = "Barcha fermalar ro'yxati")
    public ResponseEntity<ApiResponse<List<FarmResponse>>> getFarms() {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getAllFarms()));
    }

    @GetMapping("/farms/{id}")
    @Operation(summary = "Ferma detail sahifasi uchun to'liq yagona API (kartalar, grafik, xodimlar, mahsulotlar)")
    public ResponseEntity<ApiResponse<FarmDetailResponse>> getFarmById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarmDetail(parseFarmId(id))));
    }

    @PatchMapping("/farms/{id}/status")
    @Operation(summary = "Fermani faollashtirish yoki bloklash (Bloklansa yangi buyurtmalar to'xtaydi, mavjudlari tugatiladi)")
    public ResponseEntity<ApiResponse<FarmResponse>> updateFarmStatus(
            @PathVariable String id,
            @Valid @RequestBody FarmStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ferma statusi yangilandi", farmService.updateFarmStatus(parseFarmId(id), request.getStatus())));
    }

    @GetMapping("/activation-requests")
    @Operation(summary = "Kutilayotgan Boss tasdiqlash so'rovlari ro'yxati")
    public ResponseEntity<ApiResponse<List<ActivationRequestResponse>>> getActivationRequests() {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getPendingActivationRequests()));
    }

    @PostMapping("/activation-requests/{id}/decision")
    @Operation(summary = "Boss tasdiqlash so'rovini tasdiqlash (APPROVED) yoki rad etish (REJECTED)")
    public ResponseEntity<ApiResponse<ActivationRequestResponse>> decideActivation(
            @PathVariable Long id,
            @Valid @RequestBody ActivationDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Qaror qabul qilindi", farmService.decideBossActivation(id, request)));
    }

    private Long parseFarmId(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            throw new uz.gidrogo.common.BadRequestException("Ferma ID kiritilishi shart");
        }
        String digits = idStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new uz.gidrogo.common.BadRequestException("Noto'g'ri ferma ID: " + idStr);
        }
        return Long.parseLong(digits);
    }

    @GetMapping("/audit-log")
    @Operation(summary = "SuperAdmin harakatlari auditi (Faqat SuperAdmin harakatlari saqlanadi)")
    public ResponseEntity<ApiResponse<List<SuperAdminAuditLog>>> getAuditLog() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getAuditLogs()));
    }

    // ==========================================
    // 3. SOZLAMALAR VA PROFIL (SETTINGS & PROFILE)
    // ==========================================
    @GetMapping({"/settings/profile", "/profile"})
    @Operation(summary = "SuperAdmin profil ma'lumotlari (Ism, familiya, telefon, email, lavozim, avatar)")
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok("SuperAdmin profili", superAdminService.getSuperAdminProfile()));
    }

    @PutMapping({"/settings/profile", "/profile"})
    @Operation(summary = "SuperAdmin profil ma'lumotlarini yangilash")
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> updateProfile(
            @RequestBody SuperAdminProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profil muvaffaqiyatli saqlandi", superAdminService.updateSuperAdminProfile(request)));
    }

    @PostMapping({"/settings/change-password", "/change-password"})
    @Operation(summary = "Xavfsizlik: SuperAdmin parolini yangilash (Joriy parol, Yangi parol, Tasdiqlash)")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        superAdminService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Parol muvaffaqiyatli yangilandi", null));
    }

    // ==========================================
    // 4. KUNMA-KUN DINAMIKA (DAILY DYNAMICS)
    // ==========================================
    @GetMapping("/orders/daily-dynamics")
    @Operation(summary = "Kunma-kun buyurtmalar dinamikasi (masalan: 1-oktabrda 5 ta, 2-oktabrda 8 ta arxiv ma'lumotlar)")
    public ResponseEntity<ApiResponse<DailyDynamicsResponse>> getDailyDynamics(
            @RequestParam(required = false, defaultValue = "7") Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long farmId) {
        return ResponseEntity.ok(ApiResponse.ok("Kunma-kun dinamika ma'lumotlari", superAdminService.getDailyDynamics(days, startDate, endDate, farmId)));
    }
}
