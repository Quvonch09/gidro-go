package uz.gidrogo.modules.client;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.modules.client.ClientDtos.*;
import uz.gidrogo.modules.farm.dto.FarmDtos.FarmResponse;
import uz.gidrogo.modules.order.OrderService;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
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

    @GetMapping("/available-farms")
    @Operation(summary = "Ro'yxatdan o'tish yoki tanlash uchun mavjud faol suv firmalari ro'yxati")
    public ResponseEntity<ApiResponse<List<AvailableFarmResponse>>> getAvailableFarms() {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getAvailableFarms()));
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
}
