package uz.gidrogo.modules.courier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.courier.CourierDtos.*;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
import uz.gidrogo.modules.stock.StockService;
import uz.gidrogo.modules.stock.dto.StockDtos.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courier")
@RequiredArgsConstructor
@Tag(name = "Courier API", description = "Dastavkachi mobil ilovasi uchun API lar")
public class CourierController {

    private final CourierService courierService;
    private final StockService stockService;

    // ── Profil va Dashboard ───────────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Kuryer profili va bugungi statistikasi")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierProfile()));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Kunlik dashboard: buyurtmalar va daromad xulosa")
    public ResponseEntity<ApiResponse<CourierDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierDashboard()));
    }

    // ── Online/Offline holat ─────────────────────────────────────────────────

    @RequestMapping(value = "/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    @Operation(summary = "Online/Offline holatini almashtirish (online: true/false)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleStatus(
            @Valid @RequestBody StatusToggleRequest request) {
        Map<String, Object> result = courierService.toggleOnlineStatus(request);
        return ResponseEntity.ok(ApiResponse.ok(result.get("message").toString(), result));
    }

    // ── Buyurtmalar ─────────────────────────────────────────────────────────

    @GetMapping("/orders")
    @Operation(summary = "Buyurtmalar ro'yxati (status filter: ASSIGNED, ON_THE_WAY, NEARBY, DELIVERED, COMPLETED, PROBLEM)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @Parameter(description = "Holat bo'yicha filter (bo'sh qolsa: ASSIGNED, ON_THE_WAY, NEARBY)")
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierOrders(status)));
    }

    @GetMapping("/orders/active")
    @Operation(summary = "Kuryerning ayni paytdagi faol buyurtmalari (ASSIGNED, ON_THE_WAY, NEARBY)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getActiveOrders() {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierOrders(null)));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Bitta buyurtma to'liq detali")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getOrderDetail(id)));
    }

    @PostMapping("/orders/{id}/accept")
    @Operation(summary = "Buyurtma taklifini qabul qilish (30 soniya ichida)")
    public ResponseEntity<ApiResponse<OrderResponse>> acceptOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Buyurtma qabul qilindi", courierService.acceptOrder(id)));
    }

    @PostMapping("/orders/{id}/reject")
    @Operation(summary = "Buyurtma taklifini rad etish (buyurtma qayta navbatga qaytadi)")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrder(
            @PathVariable Long id,
            @Valid @RequestBody RejectOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Buyurtma rad etildi", courierService.rejectOrder(id, request)));
    }

    @PostMapping("/orders/{id}/start")
    @Operation(summary = "Yo'lga chiqish (holat: ON_THE_WAY)")
    public ResponseEntity<ApiResponse<OrderResponse>> startOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Yo'lga chiqildi", courierService.startDelivery(id)));
    }

    @PostMapping("/orders/{id}/deliver")
    @Operation(summary = "Yetkazildi (rasm URL majburiy; bo'sh shishalar va mijoz izohini ixtiyoriy)")
    public ResponseEntity<ApiResponse<OrderResponse>> deliverOrder(
            @PathVariable Long id,
            @Valid @RequestBody DeliverRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Buyurtma yetkazildi", courierService.deliverOrder(id, request)));
    }

    @PostMapping("/orders/{id}/cash-collected")
    @Operation(summary = "Naqd to'lov tasdig'i ('Pul olindi') — olingan summani kiriting")
    public ResponseEntity<ApiResponse<OrderResponse>> cashCollected(
            @PathVariable Long id,
            @RequestBody(required = false) CashCollectedRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Pul olindi, buyurtma yakunlandi",
                courierService.confirmCashCollected(id, request)));
    }

    @PostMapping("/orders/{id}/problem")
    @Operation(summary = "Muammo haqida xabar berish (sabab kodi va matni)")
    public ResponseEntity<ApiResponse<OrderResponse>> reportProblem(
            @PathVariable Long id,
            @Valid @RequestBody ProblemReportRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Muammo qayd etildi", courierService.reportProblem(id, request)));
    }

    // ── Muammo sabablari ro'yxati ─────────────────────────────────────────────

    @GetMapping("/orders/problem-reasons")
    @Operation(summary = "Muammo sabablari ro'yxati (kod va o'zbek tili)")
    public ResponseEntity<ApiResponse<List<ProblemReasonItem>>> getProblemReasons() {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getProblemReasons()));
    }

    // ── Lokatsiya ────────────────────────────────────────────────────────────

    @PostMapping("/location")
    @Operation(summary = "Joriy GPS lokatsiyani yuborish (500m yaqinlashsa NEARBY avtomatik)")
    public ResponseEntity<ApiResponse<LocationUpdateResponse>> updateLocation(
            @Valid @RequestBody LocationUpdateRequest request) {
        LocationUpdateResponse response = courierService.updateLocation(request);
        return ResponseEntity.ok(ApiResponse.ok("Lokatsiya yangilandi", response));
    }

    // ── FCM Device token ─────────────────────────────────────────────────────

    @PostMapping("/device-token")
    @Operation(summary = "FCM push-notification token saqlash (ANDROID yoki IOS)")
    public ResponseEntity<ApiResponse<String>> saveDeviceToken(
            @Valid @RequestBody DeviceTokenRequest request) {
        courierService.saveDeviceToken(request);
        return ResponseEntity.ok(ApiResponse.ok("FCM token saqlandi", "OK"));
    }

    // ── Mahsulotlar katalogi ─────────────────────────────────────────────────

    @GetMapping("/products")
    @Operation(summary = "Fermaga tegishli mahsulotlar katalogi (kuryer uchun)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProducts() {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierProducts()));
    }

    // ── Zaxira (Stock) ───────────────────────────────────────────────────────

    @PostMapping("/stock/restock")
    @Operation(summary = "Yuk to'ldirish (Dastavkachi bazadan mashinaga suv yuklaganda)")
    public ResponseEntity<ApiResponse<StockItemResponse>> restock(@Valid @RequestBody RestockRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        StockItemResponse resp = stockService.restockVehicle(courierId, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.ok("Mashinaga suv yuklandi", resp));
    }

    @GetMapping("/stock")
    @Operation(summary = "Dastavkachi mashinasidagi joriy zaxira")
    public ResponseEntity<ApiResponse<List<StockItemResponse>>> getMyVehicleStock() {
        Long courierId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(stockService.getVehicleStock(courierId)));
    }
}
