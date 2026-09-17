package uz.gidrogo.modules.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uz.gidrogo.modules.auth.Role;

import java.time.Instant;

public class StaffDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffCreateRequest {
        @NotBlank(message = "Xodim F.I.SH. kiritilishi shart")
        private String fullName;

        @NotBlank(message = "Telefon raqami kiritilishi shart")
        private String phone;

        @NotBlank(message = "Parol kiritilishi shart")
        private String password;

        @NotNull(message = "Rol (MANAGER yoki COURIER) kiritilishi shart")
        private Role role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffStatusUpdateRequest {
        @NotBlank(message = "Status (ACTIVE yoki BLOCKED) kiritilishi shart")
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffResponse {
        private Long id;
        private Long farmId;
        private String fullName;
        private String phone;
        private Role role;
        private String status;
        private Instant createdAt;
    }
}
