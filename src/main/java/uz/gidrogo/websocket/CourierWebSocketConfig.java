package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CourierWebSocketConfig implements WebSocketConfigurer {

    private final CourierWebSocketHandler courierWebSocketHandler;
    private final CourierHandshakeInterceptor courierHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Native plain WebSocket endpoint for courier mobile app (JSON text frames)
        registry.addHandler(courierWebSocketHandler, "/ws/courier")
                .addInterceptors(courierHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
