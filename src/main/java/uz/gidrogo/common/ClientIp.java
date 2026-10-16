package uz.gidrogo.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Joriy HTTP so'rovning IP manzili va User-Agent'i (Nginx ortida X-Forwarded-For birinchi qiymati). */
public final class ClientIp {

    /**
     * X-Forwarded-For / X-Real-IP faqat ishonchli reverse proxy (Nginx) ortida qabul qilinadi —
     * aks holda mijoz IP manzilini soxtalashtirib login rate-limit'ni chetlab o'tishi mumkin.
     * app.security.trust-forwarded-headers (standart: false).
     */
    private static volatile boolean trustForwardedHeaders = false;

    private ClientIp() {}

    public static void setTrustForwardedHeaders(boolean trust) {
        trustForwardedHeaders = trust;
    }

    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    public static String ip(HttpServletRequest request) {
        if (request == null) return null;
        if (!trustForwardedHeaders) {
            return truncate(request.getRemoteAddr(), 64);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    public static String ip() {
        return ip(currentRequest());
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getHeader("User-Agent"), 500);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
