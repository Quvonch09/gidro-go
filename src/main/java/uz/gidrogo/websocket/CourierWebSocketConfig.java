package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CourierWebSocketConfig implements WebSocketConfigurer {

    private final CourierWebSocketHandler courierWebSocketHandler;
    private final CourierHandshakeInterceptor courierHandshakeInterceptor;

    @Value("${app.cors.allowed-origins:https://gidrogo-project.vercel.app,https://*.vercel.app,https://gidrogo-miniapp.pages.dev,https://*.gidrogo-miniapp.pages.dev,https://*.pages.dev,https://web.telegram.org,https://*.telegram.org,https://169.58.215.170,http://169.58.215.170,https://169.58.215.170:8080,http://169.58.215.170:8080,http://localhost:*,https://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // Native plain WebSocket endpoint for courier mobile app (JSON text frames)
        registry.addHandler(courierWebSocketHandler, "/ws/courier")
                .addInterceptors(courierHandshakeInterceptor)
                .setAllowedOriginPatterns(origins);
    }
}
