package uz.gidrogo.modules.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class SuperAdminDtos {

    // ==========================================
    // 1. HUDUDIY TAQSIMOT (REGIONAL DISTRIBUTION)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionalDistributionResponse {
        private String title;          // "Hududiy Taqsimot"
        private String description;    // "Rahbarlarga biriktirilgan suv fermalari viloyatlar kesimida"
        private long totalFarms;       // 48
        private List<RegionItem> regions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionItem {
        private String regionName;     // "Toshkent viloyati", "Samarqand & Qashqadaryo", "Farg'ona vodiysi"
        private long count;            // 18
        private double percentage;     // 37.5
        private String formattedText;  // "18 ta (37.5%)"
        private String color;          // Rang kodi

        // Frontend grafik va progress barlar uchun qo'shimcha aliaslar:
        private String name;           // regionName bilan bir xil
        private long value;            // count bilan bir xil
    }

    // ==========================================
    // 2. FERMA EGALARI (BOSSES / FARM OWNERS)
    // ==========================================
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossSummaryCards {
        private long totalBosses;         // 48
        private long activeBosses;        // 44
        private long recentlyApproved;    // 5
        private long blockedBosses;       // 4

        private String jamiBosslarText;      // "48 nafar"
        private String faolHisoblarText;     // "44 nafar"
        private String yangiTasdiqlanganText;// "5 nafar"
        private String bloklanganText;       // "4 nafar"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossItemResponse {
        private Long id;                  // Boss User ID
        private String fullName;          // "Aliyev Bobur Mansurovich"
        private String phone;             // "+998 90 123 45 67"
        private String avatarUrl;         // Rasm URL
        private Long farmId;              // 1
        private String farmCode;          // "farm-001"
        private String farmName;          // "Oazis Gidroponika Majmuasi"
        private String status;            // "ACTIVE" or "BLOCKED"
        private boolean active;           // true (toggle switch holati)
        private String statusLabel;       // "Faol" or "Bloklangan"
        private Instant createdAt;
        private String formattedCreatedAt;// "12.08.2026"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossListResponse {
        private BossSummaryCards summary;
        private List<BossItemResponse> bosses;
        private List<BossItemResponse> content; // bosses bilan bir xil
        private long totalElements;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossUpdateRequest {
        private String fullName;
        private String phone;
        private String password;
        private Long farmId;
        private String status;
        private String avatarUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BossStatusUpdateRequest {
        @NotNull(message = "Status kiritilishi shart")
        private String status; // ACTIVE, BLOCKED
    }
}
