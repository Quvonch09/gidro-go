package uz.gidrogo.modules.bottle;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.bottle.BottleDtos.*;
import uz.gidrogo.modules.order.dto.OrderDtos.CartCheckoutRequest;
import uz.gidrogo.modules.order.dto.OrderDtos.CartQuoteResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bottle & Tara API", description = "Qaytariladigan idish balansi va tara hisob-kitobi")
public class BottleController {

    private final BottleService bottleService;

    @PostMapping("/api/client/cart/quote")
    @Operation(summary = "Savatdagi mahsulotlar va tara to'lovini hisoblash (order yaratmasdan)")
    public ResponseEntity<ApiResponse<CartQuoteResponse>> getCartQuote(@Valid @RequestBody CartCheckoutRequest request) {
        Long clientId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(bottleService.calculateQuote(clientId, request)));
    }

    @GetMapping("/api/client/bottles")
    @Operation(summary = "Mijozning fermalar bo'yicha idish balanslari ro'yxati")
    public ResponseEntity<ApiResponse<List<ClientBottleBalanceResponse>>> getClientBottles() {
        Long clientId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(bottleService.getClientBottleBalances(clientId)));
    }

    @PostMapping("/api/manager/clients/{clientId}/bottles/adjust")
    @Operation(summary = "Menejer tomonidan mijoz idish balansini qo'lda tuzatish (eski idishlar)")
    public ResponseEntity<ApiResponse<ClientBottleBalanceResponse>> adjustBottleBalance(
            @PathVariable Long clientId,
            @Valid @RequestBody BottleAdjustRequest request) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Long farmId = SecurityUtils.getCurrentFarmId();
        return ResponseEntity.ok(ApiResponse.ok("Idish balansi yangilandi", bottleService.adjustBottleBalance(managerId, farmId, clientId, request)));
    }
}
