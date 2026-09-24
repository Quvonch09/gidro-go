package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import uz.gidrogo.modules.courier.CourierDtos.LocationUpdateRequest;
import uz.gidrogo.modules.courier.CourierDtos.LocationUpdateResponse;
import uz.gidrogo.modules.courier.CourierDtos.StatusToggleRequest;
import uz.gidrogo.modules.courier.CourierService;
import uz.gidrogo.security.UserPrincipal;
import uz.gidrogo.websocket.WebSocketDtos.WsLocationPayload;
import uz.gidrogo.websocket.WebSocketDtos.WsStatusPayload;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {

    private final CourierService courierService;
    private final WebSocketEventPublisher eventPublisher;

    /**
     * Dastavkachi ilovasidan GPS lokatsiyani to'g'ridan-to'g'ri WebSocket orqali qabul qilish
     * Destination: /app/courier/location
     */
    @MessageMapping("/courier/location")
    public void handleLocationUpdate(@Payload WsLocationPayload payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            log.warn("WebSocket location update: Unauthenticated request");
            return;
        }

        LocationUpdateRequest request = new LocationUpdateRequest();
        request.setLatitude(payload.getLatitude());
        request.setLongitude(payload.getLongitude());
        request.setAccuracy(payload.getAccuracy());
        request.setBearing(payload.getBearing());
        request.setSpeed(payload.getSpeed());

        // CourierService orqali saqlash va geofence tekshirish
        courierService.updateLocationForUser(principal.getId(), principal.getFarmId(), request);
    }

    /**
     * Dastavkachi holatini (Online/Offline) WebSocket orqali almashtirish
     * Destination: /app/courier/status
     */
    @MessageMapping("/courier/status")
    public void handleStatusToggle(@Payload WsStatusPayload payload, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return;
        }

        StatusToggleRequest request = new StatusToggleRequest();
        request.setOnline(payload.getOnline());
        request.setStatus(payload.getStatus());

        Map<String, Object> res = courierService.toggleOnlineStatusForUser(principal.getId(), principal.getFarmId(), request);
        log.info("Courier {} toggled status via WebSocket: {}", principal.getId(), res.get("status"));
    }

    /**
     * Ulanish liveness testi (Heartbeat)
     * Destination: /app/ping
     */
    @MessageMapping("/ping")
    public Map<String, Object> handlePing(Authentication authentication) {
        String user = authentication != null ? authentication.getName() : "anonymous";
        return Map.of("pong", true, "user", user, "time", System.currentTimeMillis());
    }
}
