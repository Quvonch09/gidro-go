package uz.gidrogo.modules.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.modules.auth.dto.AuthDtos.AuthResponse;
import uz.gidrogo.modules.auth.dto.TelegramMiniAppDtos.*;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Telegram Mini App API", description = "Telegram Mini App mijozi uchun sessiya, ro'yxatdan o'tish, ferma va ticket API lari")
public class TelegramMiniAppController {

    private final TelegramMiniAppService miniAppService;

    @PostMapping("/telegram/session")
    @Operation(summary = "Telegram Mini App sessiyasini ochish (initData tekshirish)")
    public ResponseEntity<ApiResponse<TelegramSessionResponse>> getOrCreateSession(
            @Valid @RequestBody TelegramSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(miniAppService.getOrCreateSession(request)));
    }

    @PostMapping("/telegram/register")
    @Operation(summary = "Telegram Mini App orqali parolsiz va OTPsiz ro'yxatdan o'tish")
    public ResponseEntity<ApiResponse<AuthResponse>> registerTelegramClient(
            @Valid @RequestBody TelegramRegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Muvaffaqiyatli ro'yxatdan o'tdingiz", miniAppService.registerTelegramClient(request)));
    }

    @PostMapping("/ws-ticket")
    @Operation(summary = "WebSocket xavfsiz ulanishi uchun 60 soniyalik bir martalik ticket olish")
    public ResponseEntity<ApiResponse<WsTicketResponse>> createWsTicket() {
        return ResponseEntity.ok(ApiResponse.ok(miniAppService.createWsTicket()));
    }

    @PutMapping("/farm")
    @Operation(summary = "Mijozning biriktirilgan fermasini almashtirish (faol buyurtma va idish qarzi bo'lmasa)")
    public ResponseEntity<ApiResponse<String>> changeClientFarm(
            @Valid @RequestBody ChangeFarmRequest request) {
        miniAppService.changeClientFarm(request);
        return ResponseEntity.ok(ApiResponse.ok("Fermangiz muvaffaqiyatli almashtirildi", "OK"));
    }

    @GetMapping("/farms/{id}")
    @Operation(summary = "Ochiq ferma ma'lumotlari (boss shaxsiy ma'lumotlarisiz)")
    public ResponseEntity<ApiResponse<FarmPublicDetailsResponse>> getFarmPublicDetails(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(miniAppService.getFarmPublicDetails(id)));
    }
}
