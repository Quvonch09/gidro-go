package uz.gidrogo.modules.banner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class BannerDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerResponse {
        private Long id;
        private String badgeText;
        private String title;
        private String subtitle;
        private String imageUrl;
        private String gradientStart;
        private String gradientEnd;
        private String iconName;
        private String actionType;
        private String actionValue;
        private Integer sortOrder;
        private Boolean isActive;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerCreateRequest {
        private String badgeText;
        private String title;
        private String subtitle;
        private String imageUrl;
        private String gradientStart;
        private String gradientEnd;
        private String iconName;
        private String actionType;
        private String actionValue;
        private Integer sortOrder;
        private Boolean isActive;
        private Long farmId;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerUpdateRequest {
        private String badgeText;
        private String title;
        private String subtitle;
        private String imageUrl;
        private String gradientStart;
        private String gradientEnd;
        private String iconName;
        private String actionType;
        private String actionValue;
        private Integer sortOrder;
        private Boolean isActive;
        private Long farmId;
        private String type;
    }
}
