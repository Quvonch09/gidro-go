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
    private final OtpService otpService;
    private final TelegramOtpService telegramOtpService;

    @PostMapping("/check-phone")
    @Operation(summary = "Telefon raqami holatini tekshirish (NEW, CLIENT, COURIER)")
    public ResponseEntity<ApiResponse<CheckPhoneResponse>> checkPhone(@Valid @RequestBody CheckPhoneRequest request) {
        CheckPhoneResponse response = authService.checkPhone(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Yagona kirish API (barcha rollar uchun: SuperAdmin, Boss, Manager, Courier, Client)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Muvaffaqiyatli kirildi", response));
    }

    @PostMapping({"/otp/send", "/client/otp/send"})
    @Operation(summary = "SMS yoki Telegram orqali 6 xonali tasdiqlash kodini (OTP) yuborish")
    public ResponseEntity<ApiResponse<OtpSendResponse>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        OtpSendResponse response = otpService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Tasdiqlash kodi yuborildi", response));
    }

    @PostMapping({"/otp/verify", "/client/otp/verify"})
    @Operation(summary = "OTP kodini tekshirish va bir martalik verificationToken olish")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        OtpVerifyResponse response = otpService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Telefon raqami muvaffaqiyatli tasdiqlandi", response));
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

    @PostMapping("/logout")
    @Operation(summary = "Tizimdan chiqish (sessiyani yakunlash va FCM tokenini o'chirish)")
    public ResponseEntity<ApiResponse<Boolean>> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok("Sessiya muvaffaqiyatli yakunlandi va qurilma tokeni o'chirildi", true));
    }
}
