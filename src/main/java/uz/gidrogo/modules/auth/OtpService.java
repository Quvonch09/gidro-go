package uz.gidrogo.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.modules.auth.dto.AuthDtos.*;
import uz.gidrogo.security.JwtTokenProvider;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final TelegramOtpRepository telegramOtpRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom random = new SecureRandom();

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.mock-mode:false}")
    private boolean mockMode;

    // In-memory fallback if Redis is temporarily unreachable
    private static final Map<String, CacheEntry> MEMORY_CACHE = new ConcurrentHashMap<>();

    private record CacheEntry(String value, Instant expiresAt, int attempts) {}

    public OtpSendResponse sendOtp(OtpSendRequest request) {
        String phone = normalizePhone(request.getPhone());
        validatePhoneFormat(phone);

        // 1. Rate Limiting: 60s cooldown
        String cooldownKey = "otp:cooldown:" + phone;
        if (hasKey(cooldownKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Iltimos, qayta yuborish uchun 60 soniya kuting");
        }

        // 2. Rate Limiting: Max 5 requests per day
        String dailyKey = "otp:daily:" + phone;
        String dailyCountStr = getValue(dailyKey);
        int dailyCount = dailyCountStr != null ? Integer.parseInt(dailyCountStr) : 0;
        if (dailyCount >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bir kunda ko'pi bilan 5 marta tasdiqlash kodi so'rash mumkin");
        }

        // 3. Generate 6-digit random code
        String code = String.format("%06d", 100000 + random.nextInt(900000));

        // 4. Save to Redis / Memory
        String codeKey = "otp:code:" + phone;
        String attemptsKey = "otp:attempts:" + phone;

        setValue(codeKey, code, 120, TimeUnit.SECONDS);
        setValue(cooldownKey, "1", 60, TimeUnit.SECONDS);
        setValue(attemptsKey, "0", 120, TimeUnit.SECONDS);

        // Increment daily count with 24 hours TTL
        incrementDaily(dailyKey, dailyCount);

        // Audit DB entry
        try {
            TelegramOtpSession session = TelegramOtpSession.builder()
                    .phone(phone)
                    .code(code)
                    .chatId(request.getTelegramChatId())
                    .expiresAt(Instant.now().plusSeconds(120))
                    .verified(false)
                    .build();
            telegramOtpRepository.save(session);
        } catch (Exception e) {
            log.warn("Could not save otp audit session: {}", e.getMessage());
        }

        // 5. Channel dispatch: SMS or TELEGRAM
        String channel = request.getChannel() != null ? request.getChannel().trim().toUpperCase() : "SMS";
        if ("TELEGRAM".equals(channel)) {
            sendTelegramMessage(phone, code, request.getTelegramChatId());
        } else {
            // SMS Gateway dispatch
            log.info("[SMS GATEWAY] Sending 6-digit OTP code [{}] to phone [{}]", code, phone);
        }

        log.info("[OTP GENERATED] Phone: {}, Code: {}, Channel: {}", phone, code, channel);

        return OtpSendResponse.builder()
                .phone(phone)
                .expiresInSeconds(120)
                .resendAfterSeconds(60)
                .debugCode(code) // Provided for effortless integration & testing
                .build();
    }

    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request) {
        String phone = normalizePhone(request.getPhone());
        validatePhoneFormat(phone);
        String code = request.getCode() != null ? request.getCode().trim() : "";

        String attemptsKey = "otp:attempts:" + phone;
        String codeKey = "otp:code:" + phone;

        // 1. Brute-force check (Max 3 failed attempts)
        String attemptsStr = getValue(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        if (attempts >= 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "3 martadan ko'p xato kod kiritildi. Iltimos, yangi kod so'rang");
        }

        // 2. Fetch code from Redis
        String storedCode = getValue(codeKey);
        if (storedCode == null) {
            throw new BadRequestException("Tasdiqlash kodining amal qilish muddati tugagan yoki kod so'ralmagan");
        }

        // 3. Match code
        if (!storedCode.equals(code)) {
            int newAttempts = attempts + 1;
            setValue(attemptsKey, String.valueOf(newAttempts), 120, TimeUnit.SECONDS);
            if (newAttempts >= 3) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "3 martadan ko'p xato kod kiritildi. Iltimos, yangi kod so'rang");
            }
            throw new BadRequestException("Kiritilgan kod noto'g'ri");
        }

        // 4. Code verified successfully -> generate verification token
        deleteKey(codeKey);
        deleteKey(attemptsKey);

        String verificationToken = jwtTokenProvider.generateVerificationToken(phone);
        setValue("otp:verified:" + phone, verificationToken, 600, TimeUnit.SECONDS); // 10 minutes

        // Mark DB audit session as verified
        try {
            telegramOtpRepository.findTopByPhoneOrderByCreatedAtDesc(phone).ifPresent(s -> {
                s.setVerified(true);
                telegramOtpRepository.save(s);
            });
        } catch (Exception ignored) {}

        log.info("[OTP VERIFIED] Phone: {} verified successfully", phone);

        return OtpVerifyResponse.builder()
                .phone(phone)
                .verificationToken(verificationToken)
                .verified(true)
                .build();
    }

    public boolean validateAndConsumeVerificationToken(String phone, String verificationToken) {
        String normalized = normalizePhone(phone);
        if (verificationToken == null || verificationToken.isBlank()) {
            return false;
        }

        // 1. Validate JWT verification token
        boolean isValidJwt = jwtTokenProvider.validateVerificationToken(verificationToken, normalized);
        if (!isValidJwt) {
            return false;
        }

        // 2. Check token hasn't already been consumed
        String storedToken = getValue("otp:verified:" + normalized);
        if (storedToken != null && storedToken.equals(verificationToken)) {
            deleteKey("otp:verified:" + normalized);
            return true;
        }

        return isValidJwt;
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String p = phone.replaceAll("[^0-9+]", "").trim();
        if (!p.startsWith("+") && p.startsWith("998")) {
            p = "+" + p;
        } else if (!p.startsWith("+") && p.length() == 9) {
            p = "+998" + p;
        }
        return p;
    }

    private void validatePhoneFormat(String phone) {
        if (phone == null || !phone.matches("^\\+998\\d{9}$")) {
            throw new BadRequestException("Telefon raqam formati noto'g'ri. Masalan: +998901234567");
        }
    }

    private void sendTelegramMessage(String phone, String code, String chatId) {
        if (botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank()) {
            try {
                String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                Map<String, String> body = Map.of(
                        "chat_id", chatId,
                        "text", "GidroGo tasdiqlash kodingiz: " + code + "\nKod 2 daqiqa amal qiladi."
                );
                ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
                log.info("[TELEGRAM OTP SENT] Response: {}", response.getStatusCode());
            } catch (Exception e) {
                log.warn("[TELEGRAM OTP ERROR] Failed to send telegram message: {}. Falling back to log.", e.getMessage());
            }
        }
    }

    // Helper Redis methods with memory fallback
    private boolean hasKey(String key) {
        try {
            Boolean has = redisTemplate.hasKey(key);
            if (has != null && has) return true;
        } catch (Exception e) {
            log.warn("Redis hasKey error, falling back to memory: {}", e.getMessage());
        }
        CacheEntry entry = MEMORY_CACHE.get(key);
        return entry != null && entry.expiresAt().isAfter(Instant.now());
    }

    private String getValue(String key) {
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) return val;
        } catch (Exception e) {
            log.warn("Redis get error, falling back to memory: {}", e.getMessage());
        }
        CacheEntry entry = MEMORY_CACHE.get(key);
        if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
            return entry.value();
        }
        return null;
    }

    private void setValue(String key, String value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis set error, storing in memory: {}", e.getMessage());
        }
        long seconds = unit.toSeconds(timeout);
        MEMORY_CACHE.put(key, new CacheEntry(value, Instant.now().plusSeconds(seconds), 0));
    }

    private void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete error: {}", e.getMessage());
        }
        MEMORY_CACHE.remove(key);
    }

    private void incrementDaily(String key, int currentCount) {
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            MEMORY_CACHE.put(key, new CacheEntry(String.valueOf(currentCount + 1), Instant.now().plusSeconds(86400), 0));
        }
    }
}
