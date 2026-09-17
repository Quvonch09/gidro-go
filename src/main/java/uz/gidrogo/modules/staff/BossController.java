package uz.gidrogo.modules.staff;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.client.ClientDtos.CrmClientResponse;
import uz.gidrogo.modules.client.ClientService;
import uz.gidrogo.modules.courier.CourierDtos.*;
import uz.gidrogo.modules.courier.CourierService;
import uz.gidrogo.modules.finance.FinanceService;
import uz.gidrogo.modules.finance.dto.FinanceDtos.FinanceSummaryResponse;
import uz.gidrogo.modules.finance.dto.FinanceDtos.DashboardResponse;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.staff.StaffDtos.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boss")
@RequiredArgsConstructor
@Tag(name = "Boss API", description = "Ferma Boss boshqaruv paneli uchun API lar")
public class BossController {

    private final StaffService staffService;
    private final FinanceService financeService;
    private final OrderRepository orderRepository;
    private final CourierService courierService;
    private final ClientService clientService;

    @GetMapping("/dashboard")
    @Operation(summary = "Boss boshqaruv ko'rsatkichlari (tushum, xarajat, buyurtmalar, zaxira, kuryerlar)")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(financeService.getDashboard(farmId)));
    }

    @PostMapping("/staff")
    @Operation(summary = "Yangi Manager yoki Dastavkachi yaratish (SuperAdmin tasdig'i shart emas)")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Xodim yaratildi", staffService.createStaff(request)));
    }

    @GetMapping("/staff")
    @Operation(summary = "Fermaning barcha xodimlari ro'yxati")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getStaff() {
        return ResponseEntity.ok(ApiResponse.ok(staffService.getFarmStaff()));
    }

    @PatchMapping("/staff/{id}/status")
    @Operation(summary = "Xodim holatini faol/bloklangan qilish")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaffStatus(
            @PathVariable Long id,
            @Valid @RequestBody StaffStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.updateStaffStatus(id, request.getStatus())));
    }

    @GetMapping("/finance/summary")
    @Operation(summary = "Ferma moliyaviy ko'rsatkichlarini kuzatish")
    public ResponseEntity<ApiResponse<FinanceSummaryResponse>> getFinanceSummary(@RequestParam(required = false, defaultValue = "month") String period) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(financeService.getSummary(farmId, period)));
    }

    @GetMapping("/couriers/daily-summary")
    @Operation(summary = "Kuryerlarning kunlik suv statistikasi (yuklangan, sotilgan, qolgan balonlar, naqd/online tushum)")
    public ResponseEntity<ApiResponse<List<CourierDailySummaryResponse>>> getCourierDailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierDailySummary(farmId, date)));
    }

    @GetMapping("/couriers/live-tracking")
    @Operation(summary = "Kuryerlarning onlayn GPS lokatsiyasi va joriy holati")
    public ResponseEntity<ApiResponse<List<CourierTrackingResponse>>> getCourierLiveTracking() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(courierService.getLiveTracking(farmId)));
    }

    @GetMapping("/clients")
    @Operation(summary = "Fermaning barcha mijozlari ro'yxati (CRM)")
    public ResponseEntity<ApiResponse<List<CrmClientResponse>>> getCrmClients() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(clientService.getCrmClients(farmId)));
    }

    @GetMapping("/clients/{id}")
    @Operation(summary = "Tanlangan mijozning to'liq CRM tafsilotlari (profil, balans, manzillar, buyurtmalar)")
    public ResponseEntity<ApiResponse<uz.gidrogo.modules.client.ClientDtos.CrmClientDetailResponse>> getClientDetail(@PathVariable Long id) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(clientService.getCrmClientDetail(farmId, id)));
    }
}
