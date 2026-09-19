package uz.gidrogo.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class NotificationDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationItemResponse {
        private Long id;
        private String title;
        private String message;
        private String type; // ORDER_ASSIGNED, STOCK_RESTOCKED, BROADCAST, SYSTEM
        private Long referenceId;
        private Boolean isRead;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPageResponse {
        private List<NotificationItemResponse> content;
        private long totalElements;
        private int totalPages;
        private int currentPage;
        private long unreadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationReadResponse {
        private Long id;
        private Boolean isRead;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnreadCountResponse {
        private long unreadCount;
    }
}
