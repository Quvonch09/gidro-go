package uz.gidrogo.modules.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.modules.notification.dto.NotificationDtos.*;

/** Band 24: Manager (va Boss) bildirishnomalari. /api/manager/** BOSS ga ham ochiq; /api/boss/** — Boss paneli uchun muqobil yo'l. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Staff Notifications API", description = "Manager va Boss bildirishnomalari (kuryer bildirishnomalari bilan bir xil sxema)")
public class StaffNotificationController {

    private final StaffNotificationService service;

    @GetMapping({"/api/manager/notifications", "/api/boss/notifications"})
    @Operation(summary = "Bildirishnomalar (sahifalangan, unreadOnly filtri)")
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(ApiResponse.ok(service.getMine(page, size, unreadOnly)));
    }

    @GetMapping({"/api/manager/notifications/unread-count", "/api/boss/notifications/unread-count"})
    @Operation(summary = "O'qilmagan bildirishnomalar soni")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.ok(service.getUnreadCount()));
    }

    @PatchMapping({"/api/manager/notifications/{id}/read", "/api/boss/notifications/{id}/read"})
    @Operation(summary = "Bildirishnomani o'qilgan deb belgilash")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.markAsRead(id)));
    }

    @PostMapping({"/api/manager/notifications/read-all", "/api/boss/notifications/read-all"})
    @Operation(summary = "Barcha bildirishnomalarni o'qilgan deb belgilash")
    public ResponseEntity<ApiResponse<Boolean>> markAllAsRead() {
        return ResponseEntity.ok(ApiResponse.ok("Barchasi o'qildi", service.markAllAsRead()));
    }
}
