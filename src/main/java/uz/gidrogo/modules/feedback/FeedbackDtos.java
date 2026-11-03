package uz.gidrogo.modules.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class FeedbackDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackCreateRequest {
        @Schema(description = "SERVICE, DELIVERY, PRODUCT, COURIER, APP, PAYMENT, OTHER (standart OTHER)", example = "DELIVERY")
        private String category;

        @NotBlank(message = "Xabar matni kiritilishi shart")
        @Size(max = 1000, message = "Xabar ko'pi bilan 1000 ta belgi bo'lishi kerak")
        private String message;

        @Schema(description = "Ixtiyoriy: qaysi buyurtma haqida (o'zingizning buyurtmangiz bo'lishi shart)")
        private Long orderId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackUpdateRequest {
        @NotBlank(message = "Holat kiritilishi shart")
        @Schema(description = "NEW, SEEN yoki RESOLVED", example = "RESOLVED")
        private String status;

        @Size(max = 1000, message = "Izoh ko'pi bilan 1000 ta belgi bo'lishi kerak")
        @Schema(description = "Manager izohi (muallifga ham ko'rinadi)")
        private String managerNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackResponse {
        private Long id;
        private Long farmId;
        private String source;
        private Long authorUserId;
        private String authorName;
        private String authorPhone;
        private Long orderId;
        private String orderNumber;
        private String category;
        private String message;
        private String status;
        private String managerNote;
        private Long handledBy;
        private String handledByName;
        private Instant handledAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackSummaryResponse {
        private long newCount;
        private long seenCount;
        private long resolvedCount;
    }
}
