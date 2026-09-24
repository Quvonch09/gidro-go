package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.security.JwtTokenProvider;
import uz.gidrogo.security.UserPrincipal;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Klientlar obuna bo'ladigan topic va navbat prefikslari
        registry.enableSimpleBroker("/topic", "/queue");
        // Klientlardan keladigan xabarlar uchun prefiks (/app/...)
        registry.setApplicationDestinationPrefixes("/app");
        // Shaxsiy foydalanuvchiga yo'naltirilgan xabarlar
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. Native WebSocket endpoint (Flutter, React, Postman, wscat uchun)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // 2. SockJS fallback bilan brauzerlar uchun endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = extractToken(accessor);

                    if (token != null && jwtTokenProvider.validateToken(token)) {
                        try {
                            Long userId = jwtTokenProvider.getUserIdFromToken(token);
                            Role role = jwtTokenProvider.getRoleFromToken(token);
                            Long farmId = jwtTokenProvider.getFarmIdFromToken(token);
                            String identifier = jwtTokenProvider.getClaimsFromToken(token).get("identifier", String.class);

                            UserPrincipal principal = UserPrincipal.builder()
                                    .id(userId)
                                    .farmId(farmId)
                                    .role(role)
                                    .identifier(identifier)
                                    .active(true)
                                    .build();

                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            principal,
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                                    );

                            accessor.setUser(auth);
                            log.info("WebSocket authenticated user: id={}, role={}, farmId={}", userId, role, farmId);
                        } catch (Exception e) {
                            log.warn("WebSocket token parsing xatolik: {}", e.getMessage());
                        }
                    }
                }
                return message;
            }
        });
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Authorization: Bearer <token>
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. token: <token>
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader.trim();
        }

        // 3. Query string / query parameter or headers
        List<String> nativeHeaders = accessor.getNativeHeader("token");
        if (nativeHeaders != null && !nativeHeaders.isEmpty()) {
            return nativeHeaders.get(0).trim();
        }

        return null;
    }
}
