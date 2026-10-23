package uz.gidrogo.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import uz.gidrogo.modules.auth.dto.AuthDtos.AuthResponse;

public class TelegramMiniAppDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelegramSessionRequest {
        @NotBlank(message = "initData kiritilishi shart")
        private String initData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelegramSessionResponse {
        private boolean registered;
        private boolean phoneVerified;
        private String phone;
        private Long telegramId;
        private String firstName;
        private String lastName;
        private String username;
        private String tempToken;
        private String token;
        private String refreshToken;
        private AuthResponse user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelegramRegisterRequest {
        @NotNull(message = "Doimiy xizmat ko'rsatuvchi ferma (farmId) tanlanishi shart")
        private Long farmId;

        @NotBlank(message = "Manzil kiritilishi shart")
        private String address;

        private Double latitude;
        private Double longitude;

        private String tempToken;
        private String initData;
        private String fullName;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WsTicketResponse {
        private String ticket;
        private long expiresInSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressUpdateRequest {
        @NotBlank(message = "Manzil matni kiritilishi shart")
        private String address;

        private String label; // "Uy", "Ish"
        private Double latitude;
        private Double longitude;
        private boolean isDefault;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeFarmRequest {
        @NotNull(message = "Yangi ferma (farmId) tanlanishi shart")
        private Long farmId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FarmPublicDetailsResponse {
        private Long id;
        private String name;
        private String farmCode;
        private String address;
        private String phone;
        private String status;
        private Double latitude;
        private Double longitude;
        private String workingHours;
        private String logoUrl;
        private String description;
    }
}
