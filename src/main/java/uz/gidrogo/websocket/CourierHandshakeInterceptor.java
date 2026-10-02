package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.security.JwtTokenProvider;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourierHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("Courier WS handshake rad etildi: token yo'q yoki yaroqsiz");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            Role role = jwtTokenProvider.getRoleFromToken(token);

            if (role != Role.COURIER && role != Role.SUPER_ADMIN) {
                log.warn("Courier WS handshake rad etildi: noto'g'ri rol: {}", role);
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }

            attributes.put("courierId", userId);
            attributes.put("role", role.name());
            log.info("Courier WS handshake muvaffaqiyatli: courierId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("Courier WS token tekshirishda xatolik: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        // 1. Authorization: Bearer <token>
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. Query param: ?token=<JWT>
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && ("token".equalsIgnoreCase(pair[0]) || "jwt".equalsIgnoreCase(pair[0]))) {
                    return pair[1].trim();
                }
            }
        }

        // 3. Servlet request fallback
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String tokenParam = servletRequest.getServletRequest().getParameter("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                return tokenParam.trim();
            }
        }

        return null;
    }
}
