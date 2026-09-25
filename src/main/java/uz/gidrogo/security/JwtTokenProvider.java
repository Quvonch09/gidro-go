package uz.gidrogo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.gidrogo.modules.auth.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(Long userId, String identifier, Role role, Long farmId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("identifier", identifier)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey);

        if (farmId != null) {
            builder.claim("farmId", farmId);
        }

        return builder.compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public Role getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Role.valueOf(claims.get("role", String.class));
    }

    public Long getFarmIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Number farmId = claims.get("farmId", Number.class);
        return farmId != null ? farmId.longValue() : null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateVerificationToken(String phone) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 10 * 60 * 1000); // 10 minutes

        return Jwts.builder()
                .subject(phone)
                .claim("type", "VERIFICATION")
                .claim("phone", phone)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateVerificationToken(String token, String phone) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = getClaimsFromToken(token);
            String type = claims.get("type", String.class);
            String sub = claims.getSubject();
            String claimPhone = claims.get("phone", String.class);
            boolean phoneMatches = phone == null || phone.equals(sub) || phone.equals(claimPhone);
            return "VERIFICATION".equals(type) && phoneMatches;
        } catch (Exception e) {
            return false;
        }
    }
}
