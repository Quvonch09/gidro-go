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
        @NotBlank(message = "Login (telefon raqam yoki username) kiritilishi shart")
        private String login;

        @NotBlank(message = "Parol kiritilishi shart")
        private String password;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpSendRequest {
        @NotBlank(message = "Telefon raqam kiritilishi shart")
        private String phone;

        private String telegramChatId;
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientRegisterRequest {
        @NotNull(message = "Doimiy xizmat ko'rsatuvchi firma (farmId) tanlanishi shart")
        private Long farmId;

        @NotBlank(message = "Ism-familiya kiritilishi shart")
        private String fullName;

        @NotBlank(message = "Telefon raqam kiritilishi shart")
        private String phone;

        @NotBlank(message = "Parol kiritilishi shart")
        private String password;

        @NotBlank(message = "Manzil kiritilishi shart")
        private String address;

        @NotNull(message = "Latitude kiritilishi shart")
        private Double latitude;

        @NotNull(message = "Longitude kiritilishi shart")
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
        private Role role;
        private Long farmId;
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
