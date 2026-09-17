package uz.gidrogo.modules.courier;

import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequestMapping("/api/courier")
@RequiredArgsConstructor
@Tag(name = "Courier API", description = "Dastavkachi mobil ilovasi uchun API lar")
public class CourierController {

    private final CourierService courierService;
    private final StockService stockService;

    @GetMapping("/orders")
    @Operation(summary = "Dastavkachiga biriktirilgan buyurtmalar (Online bo'lsa summa yashirilgan)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders() {
        return ResponseEntity.ok(ApiResponse.ok(courierService.getCourierOrders()));
    }

    @PostMapping("/orders/{id}/accept")
    @Operation(summary = "Buyurtma taklifini qabul qilish (30 soniya ichida)")
    public ResponseEntity<ApiResponse<OrderResponse>> acceptOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Buyurtma qabul qilindi", courierService.acceptOrder(id)));
    }

    @PostMapping("/orders/{id}/start")
    @Operation(summary = "Yo'lga chiqish (holat: ON_THE_WAY)")
    public ResponseEntity<ApiResponse<OrderResponse>> startOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Yo'lga chiqildi", courierService.startDelivery(id)));
    }

    @PostMapping("/orders/{id}/deliver")
    @Operation(summary = "Yetkazildi (1 ta rasm yuklash majburiy, zaxira avtomatik kamayadi)")
    public ResponseEntity<ApiResponse<OrderResponse>> deliverOrder(
            @PathVariable Long id,
            @Valid @RequestBody DeliverRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Buyurtma yetkazildi", courierService.deliverOrder(id, request)));
    }

    @PostMapping("/orders/{id}/cash-collected")
    @Operation(summary = "Naqd to'lov tasdig'i ('Pul olindi')")
    public ResponseEntity<ApiResponse<OrderResponse>> cashCollected(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Pul olindi, buyurtma yakunlandi", courierService.confirmCashCollected(id)));
    }

    @PostMapping("/orders/{id}/problem")
    @Operation(summary = "Muammo haqida xabar berish (sabab kodi va matni)")
    public ResponseEntity<ApiResponse<OrderResponse>> reportProblem(
            @PathVariable Long id,
            @Valid @RequestBody ProblemReportRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Muammo qayd etildi", courierService.reportProblem(id, request)));
    }

    @PostMapping("/location")
    @Operation(summary = "Joriy GPS lokatsiyani yuborish (500 metr masofada avtomatik NEARBY ga o'tadi)")
    public ResponseEntity<ApiResponse<String>> updateLocation(@Valid @RequestBody LocationUpdateRequest request) {
        courierService.updateLocation(request);
        return ResponseEntity.ok(ApiResponse.ok("Lokatsiya yangilandi", "OK"));
    }

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
