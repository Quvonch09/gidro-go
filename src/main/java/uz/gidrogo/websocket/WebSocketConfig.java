package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.security.JwtTokenProvider;
import uz.gidrogo.security.UserPrincipal;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;

    @Value("${app.cors.allowed-origins:https://gidrogo-project.vercel.app,https://*.vercel.app,https://gidrogo-miniapp.pages.dev,https://*.gidrogo-miniapp.pages.dev,https://*.pages.dev,https://web.telegram.org,https://*.telegram.org,https://169.58.215.170,http://169.58.215.170,https://169.58.215.170:8080,http://169.58.215.170:8080,http://localhost:*,https://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    /** Band 19: 1 mijozga <= 3 parallel ulanish */
    private final Map<Long, List<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public static class StompAuthenticationToken extends UsernamePasswordAuthenticationToken {
        private final UserPrincipal userPrincipal;

        public StompAuthenticationToken(UserPrincipal principal) {
            super(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.getRole().name())));
            this.userPrincipal = principal;
        }

        @Override
        public String getName() {
            return String.valueOf(userPrincipal.getId());
        }

        public UserPrincipal getUserPrincipal() {
            return userPrincipal;
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        HandshakeInterceptor clientHandshakeInterceptor = new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                // 1. Bir martalik ?ticket=<ticket> tekshiruvi (Band 18 / B-TG-6)
                String ticket = extractQueryParam(request, "ticket");
                if (ticket == null || ticket.isBlank()) {
                    ticket = request.getHeaders().getFirst("ticket");
                }

                if (ticket != null && !ticket.isBlank()) {
                    String cachedUserId = redisTemplate.opsForValue().getAndDelete("ws:ticket:" + ticket.trim());
                    if (cachedUserId == null) {
                        log.warn("WS /ws/client handshake rad etildi: yaroqsiz yoki ishlatilgan ticket");
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                    try {
                        Long userId = Long.valueOf(cachedUserId);
                        User user = userRepository.findById(userId).orElse(null);
                        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                            log.warn("WS /ws/client handshake rad etildi: user topilmadi yoki bloklangan id={}", userId);
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return false;
                        }
                        UserPrincipal principal = UserPrincipal.create(user);
                        attributes.put("principal", principal);
                        attributes.put("userId", user.getId());
                        log.info("WS /ws/client handshake muvaffaqiyatli (ticket): userId={}, role={}", user.getId(), user.getRole());
                        return true;
                    } catch (Exception e) {
                        log.warn("WS /ws/client ticket xatolik: {}", e.getMessage());
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                }

                // 2. JWT Token tekshiruvi (?token=<jwt> yoki Authorization: Bearer <jwt>)
                String token = extractBearerOrQueryToken(request);
                if (token != null && !token.isBlank()) {
                    if (!jwtTokenProvider.validateToken(token)) {
                        log.warn("WS /ws/client handshake rad etildi: yaroqsiz JWT token");
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
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
                        attributes.put("principal", principal);
                        attributes.put("userId", userId);
                        log.info("WS /ws/client handshake muvaffaqiyatli (JWT): userId={}, role={}", userId, role);
                        return true;
                    } catch (Exception e) {
                        log.warn("WS /ws/client JWT parsing xatolik: {}", e.getMessage());
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                }

                // 3. Ticket ham, JWT token ham yo'q -> Handshake rad etiladi (401)
                log.warn("WS /ws/client handshake rad etildi: ticket yoki token berilmagan");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {
            }
        };

        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                              Map<String, Object> attributes) {
                UserPrincipal principal = (UserPrincipal) attributes.get("principal");
                if (principal != null) {
                    return new StompAuthenticationToken(principal);
                }
                return null;
            }
        };

        registry.addEndpoint("/ws/client")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(clientHandshakeInterceptor)
                .setAllowedOriginPatterns(origins);

        registry.addEndpoint("/ws/client")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(clientHandshakeInterceptor)
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                Long userId = (Long) session.getAttributes().get("userId");
                if (userId != null) {
                    List<WebSocketSession> sessions = userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
                    sessions.removeIf(s -> !s.isOpen());
                    // Band 19: 1 mijozga <= 3 parallel ulanish
                    while (sessions.size() >= 3) {
                        WebSocketSession oldest = sessions.remove(0);
                        try {
                            if (oldest.isOpen()) {
                                oldest.close(CloseStatus.NORMAL);
                            }
                        } catch (Exception ignored) {}
                    }
                    sessions.add(session);
                }
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                Long userId = (Long) session.getAttributes().get("userId");
                if (userId != null) {
                    List<WebSocketSession> sessions = userSessions.get(userId);
                    if (sessions != null) {
                        sessions.remove(session);
                        if (sessions.isEmpty()) {
                            userSessions.remove(userId);
                        }
                    }
                }
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                StompCommand command = accessor.getCommand();

                if (StompCommand.CONNECT.equals(command)) {
                    ensureAuthenticated(accessor);
                    return message;
                }

                if (StompCommand.SUBSCRIBE.equals(command)) {
                    UserPrincipal principal = resolvePrincipal(accessor);
                    if (principal == null) {
                        throw new MessagingException("UNAUTHORIZED: WebSocket avtorizatsiyadan o'tilmagan");
                    }
                    String destination = accessor.getDestination();
                    validateSubscription(principal, destination);
                    return message;
                }

                if (StompCommand.SEND.equals(command)) {
                    UserPrincipal principal = resolvePrincipal(accessor);
                    if (principal == null) {
                        throw new MessagingException("UNAUTHORIZED");
                    }
                    String destination = accessor.getDestination();
                    if (destination != null && (destination.startsWith("/topic/") || destination.startsWith("/queue/") || destination.startsWith("/user/"))) {
                        throw new MessagingException("FORBIDDEN: Klientdan to'g'ridan-to'g'ri broker kanaliga yozish taqiqlangan");
                    }
                }

                return message;
            }
        });
    }

    private void ensureAuthenticated(StompHeaderAccessor accessor) {
        UserPrincipal existing = resolvePrincipal(accessor);
        if (existing != null) {
            return;
        }

        // Session attributes dan tiklash (HandshakeInterceptor qo'ygan principal)
        if (accessor.getSessionAttributes() != null) {
            UserPrincipal sessionPrincipal = (UserPrincipal) accessor.getSessionAttributes().get("principal");
            if (sessionPrincipal != null) {
                accessor.setUser(new StompAuthenticationToken(sessionPrincipal));
                return;
            }
        }

        throw new MessagingException("UNAUTHORIZED: Ticket yoki token tasdiqlanmagan");
    }

    private UserPrincipal resolvePrincipal(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof StompAuthenticationToken stompAuth) {
            return stompAuth.getUserPrincipal();
        }
        if (user instanceof UsernamePasswordAuthenticationToken upAuth && upAuth.getPrincipal() instanceof UserPrincipal up) {
            return up;
        }
        if (accessor.getSessionAttributes() != null) {
            Object p = accessor.getSessionAttributes().get("principal");
            if (p instanceof UserPrincipal up) {
                accessor.setUser(new StompAuthenticationToken(up));
                return up;
            }
        }
        return null;
    }

    private void validateSubscription(UserPrincipal principal, String destination) {
        if (destination == null || destination.isBlank()) {
            throw new MessagingException("FORBIDDEN: Destination ko'rsatilmagan");
        }

        Long userId = principal.getId();
        Role role = principal.getRole();
        Long farmId = principal.getFarmId();

        // 1. Shaxsiy user destination (/user/queue/orders, /user/queue/notifications)
        if (destination.equals("/user/queue/orders") || destination.equals("/user/queue/notifications")) {
            return;
        }

        // 2. Buyurtma kuzatuv kanallari (/topic/orders/{id}/tracking yoki /topic/order/{id}**)
        if (destination.startsWith("/topic/orders/") || destination.startsWith("/topic/order/")) {
            Long orderId = extractOrderIdFromTopic(destination);
            if (orderId == null) {
                throw new MessagingException("FORBIDDEN: Noto'g'ri buyurtma kanali: " + destination);
            }
            uz.gidrogo.modules.order.Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                throw new MessagingException("FORBIDDEN: Buyurtma topilmadi yoki ruxsat yo'q");
            }

            if (role == Role.SUPER_ADMIN) {
                return;
            }
            if (role == Role.CLIENT) {
                Client client = clientRepository.findByUserId(userId).orElse(null);
                if (client == null || !client.getId().equals(order.getClientId())) {
                    log.warn("WS SUBSCRIBE rad etildi: userId={} begona orderId={} ga urinish", userId, orderId);
                    throw new MessagingException("FORBIDDEN: Boshqa mijozning buyurtmasini kuzatish taqiqlangan");
                }
                return;
            }
            if (role == Role.COURIER) {
                if (!userId.equals(order.getCourierId())) {
                    throw new MessagingException("FORBIDDEN: Boshqa kuryerning buyurtmasiga obuna bo'lish taqiqlangan");
                }
                return;
            }
            if (role == Role.BOSS || role == Role.MANAGER) {
                if (farmId == null || !farmId.equals(order.getFarmId())) {
                    throw new MessagingException("FORBIDDEN: Boshqa ferma buyurtmasiga obuna bo'lish taqiqlangan");
                }
                return;
            }
            throw new MessagingException("FORBIDDEN");
        }

        // 3. Mijoz shaxsiy topic kanali (/topic/client/{clientId}/orders)
        if (destination.startsWith("/topic/client/")) {
            Long targetClientId = extractIdSegment(destination, "/topic/client/");
            if (targetClientId == null) {
                throw new MessagingException("FORBIDDEN: Noto'g'ri mijoz kanali");
            }
            if (role == Role.SUPER_ADMIN) {
                return;
            }
            if (role == Role.CLIENT) {
                Client client = clientRepository.findByUserId(userId).orElse(null);
                if (client != null && client.getId().equals(targetClientId)) {
                    return;
                }
            }
            throw new MessagingException("FORBIDDEN: Boshqa mijoz kanaliga obuna bo'lish taqiqlangan");
        }

        // 4. Kuryer shaxsiy topic kanali (/topic/courier/{courierId}/**)
        if (destination.startsWith("/topic/courier/")) {
            Long targetCourierId = extractIdSegment(destination, "/topic/courier/");
            if (targetCourierId == null) {
                throw new MessagingException("FORBIDDEN: Noto'g'ri kuryer kanali");
            }
            if (role == Role.SUPER_ADMIN || (role == Role.COURIER && userId.equals(targetCourierId))) {
                return;
            }
            throw new MessagingException("FORBIDDEN: Boshqa kuryer kanaliga obuna bo'lish taqiqlangan");
        }

        // 5. Ferma topic kanali (/topic/farm/{farmId}/**)
        if (destination.startsWith("/topic/farm/")) {
            Long targetFarmId = extractIdSegment(destination, "/topic/farm/");
            if (targetFarmId == null) {
                throw new MessagingException("FORBIDDEN: Noto'g'ri ferma kanali");
            }
            if (role == Role.SUPER_ADMIN) {
                return;
            }
            if ((role == Role.BOSS || role == Role.MANAGER) && farmId != null && farmId.equals(targetFarmId)) {
                return;
            }
            throw new MessagingException("FORBIDDEN: Boshqa ferma kanaliga obuna bo'lish taqiqlangan");
        }

        throw new MessagingException("FORBIDDEN: Ruxsat etilmagan kanal: " + destination);
    }

    private Long extractOrderIdFromTopic(String destination) {
        String rest = null;
        if (destination.startsWith("/topic/orders/")) {
            rest = destination.substring("/topic/orders/".length());
        } else if (destination.startsWith("/topic/order/")) {
            rest = destination.substring("/topic/order/".length());
        }
        if (rest == null || rest.isBlank()) return null;
        int slashIdx = rest.indexOf('/');
        String idPart = slashIdx >= 0 ? rest.substring(0, slashIdx) : rest;
        try {
            return Long.valueOf(idPart.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long extractIdSegment(String destination, String prefix) {
        if (!destination.startsWith(prefix)) return null;
        String rest = destination.substring(prefix.length());
        int slashIdx = rest.indexOf('/');
        String idPart = slashIdx >= 0 ? rest.substring(0, slashIdx) : rest;
        try {
            return Long.valueOf(idPart.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractQueryParam(ServerHttpRequest request, String paramName) {
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && paramName.equalsIgnoreCase(pair[0])) {
                    return URLDecoder.decode(pair[1].trim(), StandardCharsets.UTF_8);
                }
            }
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String val = servletRequest.getServletRequest().getParameter(paramName);
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return null;
    }

    private String extractBearerOrQueryToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String token = extractQueryParam(request, "token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        return extractQueryParam(request, "jwt");
    }
}
