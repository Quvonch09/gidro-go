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
        @io.swagger.v3.oas.annotations.media.Schema(description = "Kuryer: NEW_OFFER, OFFER_CANCELLED, ORDER_ASSIGNED, "
                + "CASH_HANDOVER_CONFIRMED, CASH_HANDOVER_REJECTED, SYSTEM. Manager/Boss: CASH_HANDOVER_REQUESTED, "
                + "CASH_HANDOVER_CANCELLED, CASH_HANDOVER_SHORTAGE (faqat Boss), FEEDBACK_CREATED, SYSTEM",
                allowableValues = {"NEW_OFFER", "OFFER_CANCELLED", "ORDER_ASSIGNED", "CASH_HANDOVER_CONFIRMED",
                        "CASH_HANDOVER_REJECTED", "CASH_HANDOVER_REQUESTED", "CASH_HANDOVER_CANCELLED",
                        "CASH_HANDOVER_SHORTAGE", "FEEDBACK_CREATED", "SYSTEM"})
        private String type;
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
