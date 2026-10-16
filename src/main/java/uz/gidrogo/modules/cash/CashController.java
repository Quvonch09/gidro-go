package uz.gidrogo.modules.cash;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.cash.CashDtos.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Cash & Handover API", description = "Dastavkachi kassa balansi va menejerga topshirish oqimi")
public class CashController {

    private final CashService cashService;

    // ── Courier Endpoints ────────────────────────────────────────────────────

    @GetMapping("/api/courier/cash")
    @Operation(summary = "Kuryerning kassa balansi, bugun yig'ilgan summa va oxirgi tranzaksiyalar")
    public ResponseEntity<ApiResponse<CourierCashSummaryResponse>> getCourierCash() {
        Long courierId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(cashService.getCourierCash(courierId)));
    }

    @PostMapping("/api/courier/cash/handovers")
    @Operation(summary = "Kuryer tomonidan kassani topshirish so'rovini yuborish")
    public ResponseEntity<ApiResponse<CashHandoverResponse>> requestHandover(@Valid @RequestBody CourierHandoverRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok("Topshirish so'rovi yuborildi", cashService.requestHandover(courierId, farmId, request)));
    }

    // ── Manager Endpoints ────────────────────────────────────────────────────

    @PostMapping("/api/manager/cash/handovers/{id}/confirm")
    @Operation(summary = "Menejer tomonidan topshirilgan naqd pulni tasdiqlash")
    public ResponseEntity<ApiResponse<CashHandoverResponse>> confirmHandover(
            @PathVariable Long id,
            @Valid @RequestBody ManagerHandoverConfirmRequest request) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok("Kassa qabul qilindi", cashService.confirmHandover(managerId, farmId, id, request)));
    }

    @PostMapping("/api/manager/cash/handovers/{id}/reject")
    @Operation(summary = "Menejer tomonidan kassa topshirish so'rovini rad etish")
    public ResponseEntity<ApiResponse<CashHandoverResponse>> rejectHandover(
            @PathVariable Long id,
            @Valid @RequestBody ManagerHandoverRejectRequest request) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok("So'rov rad etildi", cashService.rejectHandover(managerId, farmId, id, request)));
    }

    @PostMapping("/api/manager/couriers/{courierId}/cash/handovers")
    @Operation(summary = "Menejer tomonidan to'g'ridan-to'g'ri topshirilgan naqd pulni kiritish")
    public ResponseEntity<ApiResponse<CashHandoverResponse>> managerDirectHandover(
            @PathVariable Long courierId,
            @Valid @RequestBody ManagerDirectHandoverRequest request) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok("Kassa topshirildi", cashService.managerDirectHandover(managerId, farmId, courierId, request)));
    }

    @GetMapping("/api/manager/couriers/cash-balances")
    @Operation(summary = "Fermaning barcha dastavkachilari kassa balanslari va oxirgi topshirish sanalari")
    public ResponseEntity<ApiResponse<List<CourierCashBalanceItem>>> getCouriersCashBalances() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(cashService.getCouriersCashBalances(farmId)));
    }

    @GetMapping("/api/manager/cash/handovers")
    @Operation(summary = "Fermadagi kassa topshirishlar tarixi va holati")
    public ResponseEntity<ApiResponse<List<CashHandoverResponse>>> getManagerHandovers(
            @RequestParam(required = false) String status) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(cashService.getFarmHandovers(farmId, status)));
    }

    // ── Boss Endpoints ───────────────────────────────────────────────────────

    @GetMapping("/api/boss/cash/summary")
    @Operation(summary = "Boss uchun kassa tushumi, topshirilgan va dastavkachilarda qolgan summa hisoboti")
    public ResponseEntity<ApiResponse<BossCashSummaryResponse>> getBossCashSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(cashService.getBossCashSummary(farmId, from, to)));
    }
}
