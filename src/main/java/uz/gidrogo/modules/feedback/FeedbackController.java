package uz.gidrogo.modules.feedback;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.modules.feedback.FeedbackDtos.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Feedback API", description = "Mijoz va kuryer fikr-mulohazalari; Manager/Boss ko'radi va holatini belgilaydi")
public class FeedbackController {

    private final FeedbackService feedbackService;

    // ── Mijoz ────────────────────────────────────────────────────────────────

    @PostMapping("/api/client/feedback")
    @Operation(summary = "Mijoz feedback yuboradi (ixtiyoriy orderId bilan)")
    public ResponseEntity<ApiResponse<FeedbackResponse>> createClientFeedback(@Valid @RequestBody FeedbackCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Fikringiz yuborildi", feedbackService.createFromClient(request)));
    }

    @GetMapping("/api/client/feedback")
    @Operation(summary = "Mijozning o'z feedback'lari va manager javobi")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> getClientFeedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.getMine(page, size)));
    }

    // ── Kuryer ───────────────────────────────────────────────────────────────

    @PostMapping("/api/courier/feedback")
    @Operation(summary = "Kuryer feedback yuboradi (ixtiyoriy orderId bilan)")
    public ResponseEntity<ApiResponse<FeedbackResponse>> createCourierFeedback(@Valid @RequestBody FeedbackCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Fikringiz yuborildi", feedbackService.createFromCourier(request)));
    }

    @GetMapping("/api/courier/feedback")
    @Operation(summary = "Kuryerning o'z feedback'lari va manager javobi")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> getCourierFeedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.getMine(page, size)));
    }

    // ── Manager (Boss ham) ───────────────────────────────────────────────────

    @GetMapping("/api/manager/feedback")
    @Operation(summary = "Fermaga kelgan feedback'lar (status: NEW/SEEN/RESOLVED, source: CLIENT/COURIER, category)")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> getFarmFeedback(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.getFarmFeedback(status, source, category, page, size)));
    }

    @GetMapping("/api/manager/feedback/summary")
    @Operation(summary = "Feedback'lar soni holat bo'yicha (yangi/ko'rilgan/hal qilingan)")
    public ResponseEntity<ApiResponse<FeedbackSummaryResponse>> getFeedbackSummary() {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.getSummary()));
    }

    @GetMapping("/api/manager/feedback/{id}")
    @Operation(summary = "Bitta feedback tafsiloti")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getFeedback(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.getFarmFeedbackById(id)));
    }

    @PatchMapping("/api/manager/feedback/{id}")
    @Operation(summary = "Feedback holatini o'zgartirish (SEEN/RESOLVED) va izoh qoldirish")
    public ResponseEntity<ApiResponse<FeedbackResponse>> updateFeedback(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Feedback yangilandi", feedbackService.update(id, request)));
    }
}
