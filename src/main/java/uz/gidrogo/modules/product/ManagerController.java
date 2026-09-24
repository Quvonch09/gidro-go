package uz.gidrogo.modules.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.client.ClientDtos;
import uz.gidrogo.modules.client.ClientDtos.CrmClientResponse;
import uz.gidrogo.modules.client.ClientService;
import uz.gidrogo.modules.courier.CourierDtos.*;
import uz.gidrogo.modules.courier.CourierService;
import uz.gidrogo.modules.finance.FinanceService;
import uz.gidrogo.modules.finance.dto.FinanceDtos.*;
import uz.gidrogo.modules.order.OrderService;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
import uz.gidrogo.modules.product.dto.ProductDtos.*;
import uz.gidrogo.modules.stock.StockService;
import uz.gidrogo.modules.stock.dto.StockDtos.*;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
@Tag(name = "Manager API", description = "Manager operatsion paneli uchun API lar")
public class ManagerController {

    private final ProductService productService;
    private final OrderService orderService;
    private final StockService stockService;
    private final FinanceService financeService;
    private final CourierService courierService;
    private final ClientService clientService;

    @GetMapping("/dashboard")
    @Operation(summary = "Manager operatsion boshqaruv ko'rsatkichlari (tushum, xarajat, buyurtmalar, zaxira, kuryerlar)")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(financeService.getDashboard(farmId)));
    }

    @GetMapping("/products")
    @Operation(summary = "Fermaning barcha mahsulotlari")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAllProductsForManager()));
    }

    @PostMapping("/products")
    @Operation(summary = "Yangi mahsulot qo'shish va narx belgilash (faqat Manager)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.createProduct(request)));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Mahsulot narxi yoki parametrlarini o'zgartirish (faqat Manager)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.updateProduct(id, request)));
    }

    @GetMapping("/regions")
    @Operation(summary = "Fermaning xizmat hududlari ro'yxati")
    public ResponseEntity<ApiResponse<List<ServiceRegionResponse>>> getRegions() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(productService.getFarmRegions(farmId)));
    }

    @PostMapping("/regions")
    @Operation(summary = "Yangi xizmat hududi (poligon) qo'shish")
    public ResponseEntity<ApiResponse<ServiceRegionResponse>> createRegion(@Valid @RequestBody ServiceRegionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.createServiceRegion(request)));
    }

    @PatchMapping("/regions/{id}/toggle")
    @Operation(summary = "Xizmat hududini yoqish/o'chirish (faol yoki nofaol qilish)")
    public ResponseEntity<ApiResponse<ServiceRegionResponse>> toggleRegion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Hudud holati yangilandi", productService.toggleServiceRegion(id)));
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

    @PostMapping("/orders/{id}/reassign")
    @Operation(summary = "Buyurtmani boshqa dastavkachiga qayta biriktirish")
    public ResponseEntity<ApiResponse<OrderResponse>> reassignOrder(
            @PathVariable Long id,
            @Valid @RequestBody ReassignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.reassignOrder(id, request.getNewCourierId())));
    }

    @GetMapping("/stock/warehouse")
    @Operation(summary = "Fermaning ombordagi zaxirasi")
    public ResponseEntity<ApiResponse<List<StockItemResponse>>> getWarehouseStock() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(stockService.getWarehouseStock(farmId)));
    }

    @PutMapping("/stock/warehouse")
    @Operation(summary = "Ombordagi zaxirani yangilash")
    public ResponseEntity<ApiResponse<StockItemResponse>> updateWarehouseStock(@Valid @RequestBody WarehouseStockUpdateRequest request) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(stockService.updateWarehouseStock(farmId, request.getProductId(), request.getQuantity())));
    }

    @GetMapping("/finance/summary")
    @Operation(summary = "Fermaning moliyaviy hisoboti (tushum, xarajat, sof foyda)")
    public ResponseEntity<ApiResponse<FinanceSummaryResponse>> getFinanceSummary(@RequestParam(required = false, defaultValue = "month") String period) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(financeService.getSummary(farmId, period)));
    }

    @PostMapping("/finance/expenses")
    @Operation(summary = "Xarajat kiritish (yoqilg'i, oylik, ta'mirlash va h.k.)")
    public ResponseEntity<ApiResponse<TransactionResponse>> createExpense(@Valid @RequestBody ExpenseCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(financeService.recordExpense(request)));
    }

    @GetMapping("/finance/transactions")
    @Operation(summary = "Fermaning barcha moliya tranzaksiyalari")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactions() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(financeService.getTransactions(farmId)));
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
    @Operation(summary = "Fermaning CRM mijozlar bazasi (buyurtmalar soni, sarflagan summasi, so'nggi manzil)")
    public ResponseEntity<ApiResponse<List<CrmClientResponse>>> getCrmClients() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(clientService.getCrmClients(farmId)));
    }

    @GetMapping("/clients/{id}")
    @Operation(summary = "Tanlangan mijozning to'liq CRM tafsilotlari (profil, balans, manzillar, buyurtmalar)")
    public ResponseEntity<ApiResponse<ClientDtos.CrmClientDetailResponse>> getClientDetail(@PathVariable Long id) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok(clientService.getCrmClientDetail(farmId, id)));
    }

    @GetMapping("/clients/{id}/orders")
    @Operation(summary = "Tanlangan mijozning ushbu fermadagi barcha buyurtmalari")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getClientOrders(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getClientOrdersForManager(id)));
    }
}
