package uz.gidrogo.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.modules.auth.Role;

public class AuthDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @io.swagger.v3.oas.annotations.media.Schema(description = "Username yoki telefon raqami", example = "admin")
        private String login;

        @io.swagger.v3.oas.annotations.media.Schema(description = "Telefon raqam (agar login kiritilmagan bo'lsa)", example = "")
        private String phone;

        @NotBlank(message = "Parol kiritilishi shart")
        @io.swagger.v3.oas.annotations.media.Schema(description = "Parol", example = "Parol123!")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckPhoneRequest {
        @NotBlank(message = "Telefon raqam kiritilishi shart")
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckPhoneResponse {
        private String status; // "NEW", "CLIENT", "COURIER"
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpSendRequest {
        @NotBlank(message = "Telefon raqam kiritilishi shart")
        private String phone;

        private String channel; // "SMS" (default) yoki "TELEGRAM"

        private String telegramChatId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpSendResponse {
        private String phone;
        private int expiresInSeconds;
        private int resendAfterSeconds;
        private String debugCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerifyRequest {
        @NotBlank(message = "Telefon raqam kiritilishi shart")
        private String phone;

        @NotBlank(message = "Tasdiqlash kodi kiritilishi shart")
        private String code;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpVerifyResponse {
        private String phone;
        private String verificationToken;
        private boolean verified;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientRegisterRequest {
        @NotBlank(message = "Telefon raqam kiritilishi shart")
        private String phone;

        private String verificationToken;

        @NotBlank(message = "Ism-familiya kiritilishi shart")
        private String fullName;

        @NotBlank(message = "Parol kiritilishi shart")
        private String password;

        @NotNull(message = "Doimiy xizmat ko'rsatuvchi firma (farmId) tanlanishi shart")
        private Long farmId;

        @NotBlank(message = "Manzil kiritilishi shart")
        private String address;

        private Double latitude;

        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private Long userId;
        private String fullName;
        private String phone;
        private Role role;
        private Long farmId;
        private String farmName;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshTokenRequest {
        @NotBlank(message = "Refresh token kiritilishi shart")
        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogoutRequest {
        private String refreshToken;
        private String fcmToken;
    }
}
