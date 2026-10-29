package uz.gidrogo.modules.client;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.client.ClientDtos.*;
import uz.gidrogo.modules.farm.dto.FarmDtos.FarmResponse;
import uz.gidrogo.modules.order.OrderService;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
import uz.gidrogo.modules.notification.NotificationService;
import uz.gidrogo.modules.notification.dto.NotificationDtos.*;
import uz.gidrogo.modules.order.dto.OrderTrackingDtos.ClientOrderTrackingResponse;
import uz.gidrogo.modules.order.dto.OrderTrackingDtos.OrderRouteResponse;
import uz.gidrogo.modules.product.ProductService;
import uz.gidrogo.modules.product.dto.ProductDtos.ProductResponse;
import uz.gidrogo.modules.rating.RatingService;
import uz.gidrogo.modules.rating.RatingService.CreateRatingRequest;

import java.util.List;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client API", description = "Mijoz mobil ilovasi uchun API lar")
public class ClientController {

    private final ClientService clientService;
    private final ProductService productService;
    private final OrderService orderService;
    private final RatingService ratingService;
    private final ClientOrderTrackingService clientOrderTrackingService;
    private final NotificationService notificationService;

    @GetMapping({"/farms", "/farms/available", "/available-farms"})
    @Operation(summary = "Shahar, tuman va lokatsiya bo'yicha mavjud fermalar ro'yxatini olish (Ochiq API)")
    public ResponseEntity<ApiResponse<List<AvailableFarmResponse>>> getAvailableFarms(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<AvailableFarmResponse> farms = clientService.getFarmsByFilter(city, district, lat, lon, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Fermalar ro'yxati muvaffaqiyatli yuklandi", farms));
    }

    @GetMapping("/my-farm")
    @Operation(summary = "Mijoz biriktirilgan suv firmasi ma'lumotlari")
    public ResponseEntity<ApiResponse<FarmResponse>> getMyFarm() {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getMyFarm()));
    }

    @GetMapping("/my-farm/products")
    @Operation(summary = "Mijoz biriktirilgan suv firmasi mahsulotlari katalogi")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getMyFarmProducts() {
        FarmResponse farm = clientService.getMyFarm();
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductsByFarm(farm.getId())));
    }

    @GetMapping("/farms/nearby")
    @Operation(summary = "Mijoz joylashuviga yaqin xizmat ko'rsatuvchi fermalar ro'yxati")
    public ResponseEntity<ApiResponse<List<NearbyFarmResponse>>> getNearbyFarms(
            @RequestParam double lat,
            @RequestParam double lon) {
        List<NearbyFarmResponse> farms = clientService.getNearbyFarms(lat, lon);
        return ResponseEntity.ok(ApiResponse.ok(farms));
    }

    @GetMapping("/farms/{id}/products")
    @Operation(summary = "Tanlangan fermaga tegishli mahsulotlar ro'yxati")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getFarmProducts(@PathVariable Long id) {
        List<ProductResponse> products = productService.getProductsByFarm(id);
        return ResponseEntity.ok(ApiResponse.ok(products));
    }

    @PostMapping("/cart/checkout")
    @Operation(summary = "Ko'p-fermali savatni rasmiylashtirish (har bir ferma uchun alohida buyurtmaga bo'linadi)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> checkout(@Valid @RequestBody CartCheckoutRequest request) {
        List<OrderResponse> orders = orderService.checkout(request);
        return ResponseEntity.ok(ApiResponse.ok("Buyurtmalar muvaffaqiyatli qabul qilindi", orders));
    }

    @GetMapping("/orders")
    @Operation(summary = "Mijozning buyurtmalar tarixi")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders() {
        List<OrderResponse> orders = orderService.getClientOrders();
        return ResponseEntity.ok(ApiResponse.ok(orders));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Buyurtma holati va tafsilotlari")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long id) {
        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    @GetMapping("/orders/{id}/tracking")
    @Operation(summary = "Kuryerning jonli GPS lokatsiyasi va masofasi (Live Tracking)")
    public ResponseEntity<ApiResponse<ClientOrderTrackingResponse>> getOrderTracking(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ClientOrderTrackingResponse tracking = clientOrderTrackingService.getTracking(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Kuryer kuzatuv ma'lumotlari", tracking));
    }

    @GetMapping("/orders/{id}/route")
    @Operation(summary = "Kuryerdan mijoz manziliga qadar yo'l marshruti (Street Polyline Waypoints)")
    public ResponseEntity<ApiResponse<OrderRouteResponse>> getOrderRoute(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        OrderRouteResponse route = clientOrderTrackingService.getRoute(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Marshrut koordinatalari", route));
    }

    @PostMapping("/orders/{id}/cancel")
    @Operation(summary = "Buyurtmani bekor qilish (faqat kuryer yo'lga chiqmagan bo'lsa)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        OrderResponse order = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.ok("Buyurtma bekor qilindi", order));
    }

    @PostMapping("/ratings")
    @Operation(summary = "Yetkazilgan buyurtma bo'yicha kuryer yoki fermani baholash (1-5 yulduz)")
    public ResponseEntity<ApiResponse<String>> rateOrder(@Valid @RequestBody CreateRatingRequest request) {
        ratingService.submitRating(request);
        return ResponseEntity.ok(ApiResponse.ok("Baholash qabul qilindi", "OK"));
    }

    @PostMapping("/orders/{id}/review")
    @Operation(summary = "Mobil ilovadan buyurtma yakunlangach kuryer va suv sifatini birdaniga baholash")
    public ResponseEntity<ApiResponse<String>> reviewOrder(
            @PathVariable Long id,
            @Valid @RequestBody RatingService.OrderReviewRequest request) {
        ratingService.submitOrderReview(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Fikr va baholash uchun rahmat!", "OK"));
    }

    @GetMapping("/addresses")
    @Operation(summary = "Mijozning saqlangan manzillari")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses() {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getClientAddresses()));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Yangi manzil qo'shish")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@Valid @RequestBody AddressCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.addAddress(request)));
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Mavjud manzilni tahrirlash yoki asosiy qilish (M-1)")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody uz.gidrogo.modules.auth.dto.TelegramMiniAppDtos.AddressUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Manzil muvaffaqiyatli yangilandi", clientService.updateAddress(id, request)));
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "Manzilni o'chirish (M-1)")
    public ResponseEntity<ApiResponse<String>> deleteAddress(@PathVariable Long id) {
        clientService.deleteAddress(id);
        return ResponseEntity.ok(ApiResponse.ok("Manzil muvaffaqiyatli o'chirildi", "OK"));
    }

    @DeleteMapping("/account")
    @Operation(summary = "Mijoz hisobini o'chirish (M-2: shaxsiy ma'lumotlar anonimlashtiriladi)")
    public ResponseEntity<ApiResponse<String>> deleteAccount() {
        clientService.deleteClientAccount();
        return ResponseEntity.ok(ApiResponse.ok("Hisobingiz muvaffaqiyatli o'chirildi", "OK"));
    }

    // --- Mijoz bildirishnomalari (Band 11.2) ---

    @GetMapping("/notifications")
    @Operation(summary = "Mijoz bildirishnomalari ro'yxati (sahifalangan)")
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(ApiResponse.ok("Bildirishnomalar ro'yxati",
                notificationService.getClientNotifications(page, size, unreadOnly)));
    }

    @GetMapping("/notifications/unread-count")
    @Operation(summary = "Mijoz o'qilmagan bildirishnomalari soni")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadNotificationsCount() {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getClientUnreadCount()));
    }

    @RequestMapping(value = "/notifications/{id}/read", method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.POST})
    @Operation(summary = "Mijoz bildirishnomasini o'qilgan deb belgilash")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markNotificationAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Bildirishnoma o'qildi deb belgilandi",
                notificationService.markClientNotificationAsRead(id)));
    }

    @RequestMapping(value = "/notifications/read-all", method = {RequestMethod.PUT, RequestMethod.POST})
    @Operation(summary = "Mijozning barcha bildirishnomalarini o'qilgan deb belgilash")
    public ResponseEntity<ApiResponse<Boolean>> markAllNotificationsAsRead() {
        notificationService.markAllClientNotificationsAsRead();
        return ResponseEntity.ok(ApiResponse.ok("Barcha bildirishnomalar o'qildi deb belgilandi", true));
    }
}

