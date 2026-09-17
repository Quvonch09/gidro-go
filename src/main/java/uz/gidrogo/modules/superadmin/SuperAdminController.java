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

import java.util.List;
import java.util.Map;

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
    @GetMapping({"/statistics/regions", "/regions-distribution", "/regional-distribution"})
    @Operation(summary = "Hududiy taqsimot - viloyatlar kesimida fermalar soni va foiz statistikasi")
    public ResponseEntity<ApiResponse<RegionalDistributionResponse>> getRegionalDistribution() {
        return ResponseEntity.ok(ApiResponse.ok(superAdminService.getRegionalDistribution()));
    }

    // ==========================================
    // 2. FERMA EGALARI (FARM OWNERS / BOSSES LIST)
    // ==========================================
    @GetMapping({"/bosses", "/farm-owners", "/ferma-egalari"})
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

    @PatchMapping({"/bosses/{id}/toggle-status", "/bosses/{id}/status"})
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

    @PostMapping({"/farms", "/fermalar"})
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
    @Operation(summary = "Ferma ma'lumotlari va to'liq statistikasi (kartalar, grafik, xodimlar, mahsulotlar)")
    public ResponseEntity<ApiResponse<FarmDetailResponse>> getFarmById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarmDetail(parseFarmId(id))));
    }

    @GetMapping("/farms/{id}/detail")
    @Operation(summary = "Ferma detail sahifasi uchun to'liq yagona API (kartalar, grafik, xodimlar, mahsulotlar)")
    public ResponseEntity<ApiResponse<FarmDetailResponse>> getFarmDetail(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarmDetail(parseFarmId(id))));
    }

    @GetMapping("/farms/{id}/statistics")
    @Operation(summary = "Ferma statistikasi va savdo dinamikasi grafigi")
    public ResponseEntity<ApiResponse<FarmDetailResponse>> getFarmStatistics(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(farmService.getFarmDetail(parseFarmId(id))));
    }

    @GetMapping("/farms/{id}/analytics")
    @Operation(summary = "Bitta ferma bo'yicha to'liq moliyaviy va operatsion analitika")
    public ResponseEntity<ApiResponse<FarmDetailResponse>> getFarmAnalytics(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "month") String period) {
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
}
