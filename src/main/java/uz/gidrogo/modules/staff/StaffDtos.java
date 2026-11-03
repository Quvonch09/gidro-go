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

    public enum StaffRole {
        MANAGER, COURIER
    }

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
        private StaffRole role;

        private String shiftType; // DAY, NIGHT, FULL_TIME

        // Faqat COURIER uchun: transport ma'lumotlari (mashina raqami majburiy)
        @io.swagger.v3.oas.annotations.media.Schema(description = "Transport (mashina rusumi), faqat COURIER", example = "Damas")
        @jakarta.validation.constraints.Size(max = 100, message = "Transport nomi ko'pi bilan 100 ta belgi")
        private String vehicleModel;

        @io.swagger.v3.oas.annotations.media.Schema(description = "Mashina raqami, COURIER uchun majburiy", example = "01 A 123 BC")
        @jakarta.validation.constraints.Size(max = 50, message = "Mashina raqami ko'pi bilan 50 ta belgi")
        private String vehiclePlateNumber;

        @io.swagger.v3.oas.annotations.media.Schema(description = "Yuk sig'imi (nechta idish sig'adi), faqat COURIER", example = "40")
        @jakarta.validation.constraints.Min(value = 1, message = "Yuk sig'imi kamida 1 bo'lishi kerak")
        @jakarta.validation.constraints.Max(value = 10000, message = "Yuk sig'imi 10000 dan oshmasligi kerak")
        private Integer maxCapacity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffVehicleUpdateRequest {
        @jakarta.validation.constraints.Size(max = 100, message = "Transport nomi ko'pi bilan 100 ta belgi")
        private String vehicleModel;

        @jakarta.validation.constraints.Size(max = 50, message = "Mashina raqami ko'pi bilan 50 ta belgi")
        private String vehiclePlateNumber;

        @jakarta.validation.constraints.Min(value = 1, message = "Yuk sig'imi kamida 1 bo'lishi kerak")
        @jakarta.validation.constraints.Max(value = 10000, message = "Yuk sig'imi 10000 dan oshmasligi kerak")
        private Integer maxCapacity;
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
        private String shiftType;
        private String vehicleModel;
        private String vehiclePlateNumber;
        private Integer maxCapacity;
        private Instant createdAt;
    }
}
