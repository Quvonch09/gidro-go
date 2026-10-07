package uz.gidrogo.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourierWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> courierSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long courierId = (Long) session.getAttributes().get("courierId");
        if (courierId != null) {
            Set<WebSocketSession> set = courierSessions.computeIfAbsent(courierId, k -> new CopyOnWriteArraySet<>());
            // Eski yopiq sessiyalarni tozalash
            set.removeIf(s -> !s.isOpen());
            set.add(session);
            log.info("Courier WS ulangan: courierId={}, sessionId={}", courierId, session.getId());
            sendConnectedFrame(session, courierId);
        } else {
            log.warn("Courier WS ulangan lekin courierId topilmadi! sessionId={}", session.getId());
        }
    }

    private void sendConnectedFrame(WebSocketSession session, Long courierId) {
        if (session == null || !session.isOpen() || courierId == null) return;
        try {
            String connectedMsg = objectMapper.writeValueAsString(Map.of(
                    "type", "CONNECTED",
                    "courierId", courierId,
                    "timestamp", Instant.now().toString()
            ));
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(connectedMsg));
                }
            }
        } catch (Exception e) {
            log.warn("CONNECTED xabarini yuborishda xatolik: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long courierId = (Long) session.getAttributes().get("courierId");
        if (courierId != null) {
            Set<WebSocketSession> sessions = courierSessions.get(courierId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    courierSessions.remove(courierId);
                }
            }
            log.info("Courier WS uzildi: courierId={}, sessionId={}, status={}", courierId, session.getId(), status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long courierId = (Long) session.getAttributes().get("courierId");
        String payload = message.getPayload().trim();
        if (payload.contains("CONNECT") || payload.contains("HELLO")) {
            sendConnectedFrame(session, courierId);
            return;
        }
        if ("PING".equalsIgnoreCase(payload) || payload.contains("\"PING\"")) {
            try {
                String pong = objectMapper.writeValueAsString(Map.of(
                        "type", "PONG",
                        "timestamp", Instant.now().toString()
                ));
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(pong));
                    }
                }
            } catch (IOException e) {
                log.warn("PONG yuborishda xatolik: {}", e.getMessage());
            }
        }
    }

    public void sendToCourier(Long courierId, Object event) {
        if (courierId == null) return;
        Set<WebSocketSession> sessions = courierSessions.get(courierId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("Courier {} uchun aktiv WS sessiya topilmadi", courierId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            if (session.isOpen()) {
                                session.sendMessage(textMessage);
                                log.info("Courier WS xabar yuborildi: courierId={}, json={}", courierId, json);
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Courier session {} ga yuborishda xatolik: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Courier WS xabarini serializatsiya qilishda xatolik: {}", e.getMessage());
        }
    }

    public boolean isCourierConnected(Long courierId) {
        Set<WebSocketSession> sessions = courierSessions.get(courierId);
        return sessions != null && !sessions.isEmpty();
    }
}
