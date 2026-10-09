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
import uz.gidrogo.modules.order.OrderService;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
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
    private final OrderService orderService;
    private final CourierService courierService;
    private final ClientService clientService;
    private final BossService bossService;

    @GetMapping("/dashboard")
    @Operation(summary = "Boss boshqaruv ko'rsatkichlari (tushum, xarajat, buyurtmalar, zaxira, kuryerlar)")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(financeService.getDashboard(farmId)));
    }

    @GetMapping("/farm")
    @Operation(summary = "Ferma texnik pasporti va laboratoriya tahlillari")
    public ResponseEntity<ApiResponse<BossDtos.FarmTechnicalPassportResponse>> getFarmTechnicalPassport() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getFarmTechnicalPassport(farmId)));
    }

    @GetMapping("/analytics/growth")
    @Operation(summary = "Davriy biznes o'sishi va analitika (oylar va choraklar dinamikasi)")
    public ResponseEntity<ApiResponse<BossDtos.BusinessGrowthResponse>> getBusinessGrowth(
            @RequestParam(required = false, defaultValue = "quarter") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getBusinessGrowth(farmId, period, year, quarter)));
    }

    @GetMapping("/finance/chart")
    @Operation(summary = "Moliyaviy ko'rsatkichlar dinamikasi grafigi (vaqt bo'yicha saralangan)")
    public ResponseEntity<ApiResponse<List<BossDtos.FinanceChartItem>>> getFinanceChart(
            @RequestParam(required = false, defaultValue = "week") String period) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getFinanceChart(farmId, period)));
    }

    @GetMapping("/sales/products-summary")
    @Operation(summary = "Mahsulotlar toifalari bo'yicha sotuv tahlili")
    public ResponseEntity<ApiResponse<List<BossDtos.ProductSalesSummaryResponse>>> getProductsSalesSummary() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getProductsSalesSummary(farmId)));
    }

    @GetMapping("/sales/summary")
    @Operation(summary = "Savdo va mahsulotlar realizatsiyasi (kunlik/haftalik/oylik dinamika va kartochkalar)")
    public ResponseEntity<ApiResponse<BossDtos.SalesRealizationSummaryResponse>> getSalesRealizationSummary(
            @RequestParam(required = false, defaultValue = "today") String period,
            @RequestParam(required = false) Long productId) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getSalesRealizationSummary(farmId, period, productId)));
    }

    @GetMapping("/clients/segments")
    @Operation(summary = "CRM Mijozlar toifalari (B2C, B2B, ulgurji)")
    public ResponseEntity<ApiResponse<BossDtos.ClientSegmentsResponse>> getClientSegments() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getClientSegments(farmId)));
    }

    @GetMapping("/orders")
    @Operation(summary = "Fermaning buyurtmalari ro'yxati (Buyurtmalar bo'limida default: FAQAT BUGUNGI buyurtmalar)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Boolean todayOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getManagerOrders(status, todayOnly, date, startDate, endDate, all)));
    }

    @GetMapping("/orders/today")
    @Operation(summary = "Faqat bugungi buyurtmalar ro'yxati")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getTodayOrders(
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getManagerOrders(status, true, null, null, null, false)));
    }

    @GetMapping("/orders/all")
    @Operation(summary = "Barcha buyurtmalar ro'yxati (tarixiy)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getManagerOrders(status, false, null, startDate, endDate, true)));
    }

    @PostMapping("/staff")
    @Operation(summary = "Yangi Manager yoki Dastavkachi yaratish (SuperAdmin tasdig'i shart emas)")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Xodim yaratildi", staffService.createStaff(request)));
    }

    @GetMapping("/staff")
    @Operation(summary = "Fermaning barcha xodimlari ro'yxati (qidiruv va rol filtri bilan)")
    public ResponseEntity<ApiResponse<List<BossDtos.EnhancedStaffItemResponse>>> getStaff(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getEnhancedStaffList(farmId, search, role)));
    }

    @GetMapping("/staff/summary")
    @Operation(summary = "Xodimlar va dastavkachilar monitoringi statistikasi (kartochkalar uchun)")
    public ResponseEntity<ApiResponse<BossDtos.StaffSummaryResponse>> getStaffSummary() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(bossService.getStaffSummary(farmId)));
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
