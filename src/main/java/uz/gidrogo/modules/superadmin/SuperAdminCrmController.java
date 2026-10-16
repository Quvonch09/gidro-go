package uz.gidrogo.modules.superadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.PaymentMethod;
import uz.gidrogo.modules.superadmin.dto.SuperAdminCrmDtos.*;

import java.time.LocalDate;
import java.util.List;

/** SuperAdmin: Mijozlar (BE-08, TZ §9) va Buyurtmalar (BE-09, TZ §10). */
@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
@Tag(name = "SuperAdmin CRM", description = "SuperAdmin: mijozlar va buyurtmalar")
public class SuperAdminCrmController {

    private final SuperAdminClientService clientService;
    private final SuperAdminOrderService orderService;

    // ==========================================
    // MIJOZLAR
    // ==========================================

    @GetMapping("/clients/summary")
    @Operation(summary = "Mijozlar: jami, davrdagi yangi, faol (oxirgi 30 kun), bloklangan, o'sish %")
    public ResponseEntity<ApiResponse<ClientSummary>> getClientSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getSummary(from, to)));
    }

    @GetMapping("/clients")
    @Operation(summary = "Mijozlar ro'yxati: qidiruv (ism, telefon — probelsiz ham), holat, ferma, sahifalash")
    public ResponseEntity<ApiResponse<PageResponse<ClientListItem>>> getClients(
            @RequestParam(required = false) String search,
            @Parameter(description = "ACTIVE | BLOCKED") @RequestParam(required = false) String status,
            @RequestParam(required = false) Long farmId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "createdAt | fullName | ordersCount | lastOrderAt | id") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getClients(search, status, farmId, page, size, sort)));
    }

    @GetMapping("/clients/{id}")
    @Operation(summary = "Mijoz profili: manzillar soni, buyurtmalar, fermalar kesimida idish balansi")
    public ResponseEntity<ApiResponse<ClientDetail>> getClient(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getClient(id)));
    }

    @PatchMapping("/clients/{id}/status")
    @Operation(summary = "Mijozni bloklash/faollashtirish (audit CLIENT_BLOCKED / CLIENT_ACTIVATED)")
    public ResponseEntity<ApiResponse<ClientDetail>> updateClientStatus(
            @PathVariable Long id, @Valid @RequestBody ClientStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Mijoz holati yangilandi", clientService.updateStatus(id, request)));
    }

    @DeleteMapping("/clients/{id}")
    @Operation(summary = "Mijozni o'chirish (soft delete). 409 CLIENT_HAS_ACTIVE_ORDERS / CLIENT_HAS_BOTTLE_BALANCE")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok(ApiResponse.ok("Mijoz o'chirildi", null));
    }

    @GetMapping("/clients/{id}/orders")
    @Operation(summary = "Mijoz buyurtmalari")
    public ResponseEntity<ApiResponse<PageResponse<OrderListItem>>> getClientOrders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        clientService.getClient(id); // mavjudligini tekshirish (404)
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrders(
                SuperAdminOrderService.OrderFilter.builder().clientId(id).build(), page, size, null)));
    }

    @GetMapping("/clients/{id}/payments")
    @Operation(summary = "Mijoz to'lovlari (PAID / CASH_COLLECTED buyurtmalar)")
    public ResponseEntity<ApiResponse<PageResponse<ClientPaymentItem>>> getClientPayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getPayments(id, page, size)));
    }

    // ==========================================
    // BUYURTMALAR (faqat o'qish)
    // ==========================================

    @GetMapping("/orders")
    @Operation(summary = "Buyurtmalar ro'yxati: filtrlar birgalikda (AND); from/to — createdAt, Asia/Tashkent")
    public ResponseEntity<ApiResponse<PageResponse<OrderListItem>>> getOrders(
            @RequestParam(required = false) Long farmId,
            @Parameter(description = "Bir nechta: status=CANCELLED&status=RETURNED") @RequestParam(required = false) List<OrderStatus> status,
            @Parameter(description = "ONLINE | CASH") @RequestParam(required = false) PaymentMethod paymentType,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Buyurtma raqami, mijoz ismi yoki telefoni") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "createdAt | totalSum | id | status") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrders(SuperAdminOrderService.OrderFilter.builder()
                .farmId(farmId).statuses(status).paymentType(paymentType).productId(productId)
                .from(from).to(to).search(search).build(), page, size, sort)));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Buyurtma tafsiloti: status tarixi, kuryer, tara, vaqtlar")
    public ResponseEntity<ApiResponse<OrderDetail>> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrder(id)));
    }
}
