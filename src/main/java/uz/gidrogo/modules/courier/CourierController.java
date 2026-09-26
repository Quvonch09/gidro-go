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

import uz.gidrogo.modules.notification.NotificationService;
import uz.gidrogo.modules.notification.dto.NotificationDtos.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courier")
@RequiredArgsConstructor
@Tag(name = "Courier API", description = "Dastavkachi mobil ilovasi uchun API lar")
public class CourierController {

    private final CourierService courierService;
    private final StockService stockService;
    private final NotificationService notificationService;

    // ── Profil va Dashboard ───────────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Kuryer profili va bugungi statistikasi")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok("Profil ma'lumotlari", courierService.getCourierProfile()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Kuryer shaxsiy va avtomobil ma'lumotlarini tahrirlash")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
            @RequestBody ProfileUpdateRequest request) {
        ProfileUpdateResponse updated = courierService.updateCourierProfile(request);
        return ResponseEntity.ok(ApiResponse.ok("Profil ma'lumotlari muvaffaqiyatli yangilandi", updated));
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
    @Operation(summary = "Buyurtmalar ro'yxati (sahifalangan, status va sana filtrlari bilan)")
    public ResponseEntity<ApiResponse<OrderPageResponse>> getMyOrders(
            @Parameter(description = "Holat bo'yicha filter (ASSIGNED, ON_THE_WAY, DELIVERED, COMPLETED, PROBLEM yoki ALL)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Boshlanish sanasi (YYYY-MM-DD yoki ISO-8601)")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "Tugash sanasi (YYYY-MM-DD yoki ISO-8601)")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "Sahifa raqami (0 dan boshlanadi)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sahifadagi elementlar soni")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierOrdersPaged(status, startDate, endDate, page, size)));
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

    @GetMapping("/stock/history")
    @Operation(summary = "Mashinaga suv yuklashlar tarixi jurnali (sahifalangan)")
    public ResponseEntity<ApiResponse<StockHistoryPageResponse>> getStockHistory(
            @Parameter(description = "Sana bo'yicha filter (YYYY-MM-DD)")
            @RequestParam(required = false) String date,
            @Parameter(description = "Sahifa raqami (0 dan boshlanadi)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sahifadagi elementlar soni")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Yuklashlar tarixi",
                courierService.getCourierStockHistory(page, size, date)));
    }

    // ── Bildirishnomalar (In-App Notifications Inbox) ─────────────────────────

    @GetMapping("/notifications")
    @Operation(summary = "Kuryer bildirishnomalari ro'yxati (sahifalangan)")
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(ApiResponse.ok("Bildirishnomalar ro'yxati",
                notificationService.getCourierNotifications(page, size, unreadOnly)));
    }

    @PatchMapping("/notifications/{id}/read")
    @Operation(summary = "Bitta bildirishnomani o'qilgan deb belgilash")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markNotificationAsRead(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Bildirishnoma o'qildi deb belgilandi",
                notificationService.markAsRead(id)));
    }

    @PostMapping("/notifications/read-all")
    @Operation(summary = "Barcha bildirishnomalarni birdan o'qilgan deb belgilash")
    public ResponseEntity<ApiResponse<Boolean>> markAllNotificationsAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.ok("Barcha bildirishnomalar o'qildi deb belgilandi", true));
    }

    @GetMapping("/notifications/unread-count")
    @Operation(summary = "O'qilmagan bildirishnomalar soni (AppBar counter badge uchun)")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadNotificationsCount() {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadCount()));
    }
}
