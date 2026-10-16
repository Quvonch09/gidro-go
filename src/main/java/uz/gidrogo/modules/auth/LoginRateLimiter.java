package uz.gidrogo.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uz.gidrogo.common.ApiException;

import java.time.Duration;

/**
 * Login rate-limit (BE-19 / CHK-07): bitta identifikator (telefon/login) yoki IP uchun
 * 15 daqiqada 5 ta noto'g'ri urinishdan keyin → 429 TOO_MANY_LOGIN_ATTEMPTS.
 * Redis ishlamasa — so'rov bloklanmaydi (log yoziladi).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final StringRedisTemplate redis;

    @Value("${app.security.login.max-failures:5}")
    private int maxFailures;

    @Value("${app.security.login.window-minutes:15}")
    private long windowMinutes;

    public void checkAllowed(String identifier, String ip) {
        if (count(key("id", identifier)) >= maxFailures || count(key("ip", ip)) >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
                    "Juda ko'p noto'g'ri urinish. " + windowMinutes + " daqiqadan keyin qayta urinib ko'ring");
        }
    }

    public void recordFailure(String identifier, String ip) {
        increment(key("id", identifier));
        increment(key("ip", ip));
    }

    public void recordSuccess(String identifier) {
        try {
            String k = key("id", identifier);
            if (k != null) redis.delete(k);
        } catch (Exception e) {
            log.warn("Login rate-limit: Redis bilan ishlab bo'lmadi: {}", e.getMessage());
        }
    }

    private long count(String key) {
        if (key == null) return 0;
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? 0 : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("Login rate-limit: Redis bilan ishlab bo'lmadi: {}", e.getMessage());
            return 0;
        }
    }

    private void increment(String key) {
        if (key == null) return;
        try {
            Long v = redis.opsForValue().increment(key);
            if (v != null && v == 1L) {
                redis.expire(key, Duration.ofMinutes(windowMinutes));
            }
        } catch (Exception e) {
            log.warn("Login rate-limit: Redis bilan ishlab bo'lmadi: {}", e.getMessage());
        }
    }

    private static String key(String kind, String value) {
        if (value == null || value.isBlank()) return null;
        return "login:fail:" + kind + ":" + value.trim().toLowerCase();
    }
}
