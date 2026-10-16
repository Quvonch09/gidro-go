package uz.gidrogo.modules.auth;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.security.JwtTokenProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Refresh tokenlar hayotiy sikli (BE-07 / BUG-018, CHK-10):
 * <ul>
 *   <li>berilganda bazaga xeshi yoziladi;</li>
 *   <li>/auth/refresh — faqat bazada bor va bekor qilinmagan token qabul qilinadi; eski token bekor qilinib
 *       yangisi beriladi (rotatsiya);</li>
 *   <li>logout — token bekor qilinadi (idempotent);</li>
 *   <li>parol almashtirish — foydalanuvchining barcha refresh tokenlari bekor qilinadi.</li>
 * </ul>
 * Eslatma: bu o'zgarishdan oldin berilgan refresh tokenlar bazada yo'q, shuning uchun ular bilan
 * /auth/refresh 401 qaytaradi — foydalanuvchi bir marta qayta kiradi.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String issue(Long userId) {
        String token = jwtTokenProvider.generateRefreshToken(userId);
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        repository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(token))
                .expiresAt(claims.getExpiration().toInstant())
                .build());
        return token;
    }

    /**
     * Tokenni tekshiradi va bekor qiladi (rotatsiya). Yaroqsiz, muddati o'tgan yoki bekor qilingan bo'lsa — 401.
     *
     * @return token egasining ID si
     */
    @Transactional
    public Long consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw unauthorized("REFRESH_TOKEN_INVALID", "Refresh token yuborilmadi");
        }
        String token = rawToken.trim();
        if (!jwtTokenProvider.validateToken(token)) {
            throw unauthorized("REFRESH_TOKEN_INVALID", "Refresh token yaroqsiz yoki muddati o'tgan");
        }
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        if (!"REFRESH".equals(claims.get("type", String.class))) {
            throw unauthorized("REFRESH_TOKEN_INVALID", "Yaroqli refresh token taqdim etilmadi");
        }
        Long userId = Long.parseLong(claims.getSubject());

        RefreshToken stored = repository.findByTokenHashForUpdate(hash(token))
                .orElseThrow(() -> unauthorized("REFRESH_TOKEN_REVOKED",
                        "Refresh token tanilmadi yoki bekor qilingan, qaytadan kiring"));
        if (stored.isRevoked() || !stored.getUserId().equals(userId)) {
            throw unauthorized("REFRESH_TOKEN_REVOKED", "Refresh token bekor qilingan, qaytadan kiring");
        }
        stored.setRevoked(true);
        stored.setRevokedAt(Instant.now());
        repository.save(stored);
        return userId;
    }

    /** Logout: tokenni bekor qiladi. Takroriy chaqiruv xatosiz o'tadi. Qaytaradi: token egasi (topilsa). */
    @Transactional
    public Optional<Long> revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        Optional<RefreshToken> stored = repository.findByTokenHashForUpdate(hash(rawToken.trim()));
        stored.ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevoked(true);
                t.setRevokedAt(Instant.now());
                repository.save(t);
            }
        });
        return stored.map(RefreshToken::getUserId);
    }

    @Transactional
    public int revokeAll(Long userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
