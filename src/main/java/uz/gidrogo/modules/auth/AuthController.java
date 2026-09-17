package uz.gidrogo.modules.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.modules.auth.dto.AuthDtos.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Tizimga kirish va ro'yxatdan o'tish amallari")
public class AuthController {

    private final AuthService authService;
    private final TelegramOtpService telegramOtpService;

    @PostMapping("/login")
    @Operation(summary = "Yagona kirish API (barcha rollar uchun: SuperAdmin, Boss, Manager, Courier, Client)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Muvaffaqiyatli kirildi", response));
    }


    @PostMapping("/client/otp/send")
    @Operation(summary = "Mijozga Telegram orqali OTP kod yuborish")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        String code = telegramOtpService.sendOtp(request.getPhone(), request.getTelegramChatId());
        Map<String, String> data = telegramOtpService.isMockMode() ?
                Map.of("phone", request.getPhone(), "debugCode", code) :
                Map.of("phone", request.getPhone());
        return ResponseEntity.ok(ApiResponse.ok("Tasdiqlash kodi Telegram bot orqali yuborildi", data));
    }

    @PostMapping("/client/otp/verify")
    @Operation(summary = "Mijoz OTP kodini tasdiqlash")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        boolean ok = telegramOtpService.verifyOtp(request.getPhone(), request.getCode());
        return ResponseEntity.ok(ApiResponse.ok("Kod tasdiqlandi", Map.of("verified", ok)));
    }

    @PostMapping("/client/register")
    @Operation(summary = "Mijoz ro'yxatdan o'tishi (OTP tasdiqlanganidan so'ng)")
    public ResponseEntity<ApiResponse<AuthResponse>> registerClient(@Valid @RequestBody ClientRegisterRequest request) {
        AuthResponse response = authService.registerClient(request);
        return ResponseEntity.ok(ApiResponse.ok("Mijoz muvaffaqiyatli ro'yxatdan o'tdi", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access tokenni yangilash (refresh token yordamida)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok("Token yangilandi", response));
    }
}
