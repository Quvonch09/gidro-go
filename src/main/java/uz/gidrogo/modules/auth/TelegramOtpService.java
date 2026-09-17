package uz.gidrogo.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import uz.gidrogo.common.BadRequestException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramOtpService {

    private final TelegramOtpRepository otpRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom random = new SecureRandom();

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.mock-mode:true}")
    private boolean mockMode;

    @Transactional
    public String sendOtp(String phone, String chatId) {
        String code = String.format("%04d", random.nextInt(10000));
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        TelegramOtpSession session = TelegramOtpSession.builder()
                .phone(phone)
                .code(code)
                .chatId(chatId)
                .expiresAt(expiresAt)
                .verified(false)
                .build();

        otpRepository.save(session);

        log.info("[TELEGRAM OTP GENERATED] Phone: {}, Code: {}, ChatId: {}", phone, code, chatId);

        if (!mockMode && botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank()) {
            try {
                String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                Map<String, String> body = Map.of(
                        "chat_id", chatId,
                        "text", "GidroGo tasdiqlash kodingiz: " + code + "\nKod 5 daqiqa amal qiladi."
                );
                ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
                log.info("[TELEGRAM OTP SENT] Response: {}", response.getStatusCode());
            } catch (Exception e) {
                log.warn("[TELEGRAM OTP ERROR] Failed to send telegram message: {}. Falling back to log.", e.getMessage());
            }
        }

        return code;
    }

    @Transactional
    public boolean verifyOtp(String phone, String code) {
        TelegramOtpSession session = otpRepository.findTopByPhoneOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new BadRequestException("Tasdiqlash kodi so'ralmagan"));

        if (session.isVerified()) {
            throw new BadRequestException("Ushbu kod allaqachon tasdiqlangan");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Tasdiqlash kodining amal qilish muddati tugagan");
        }

        if (!session.getCode().equals(code)) {
            throw new BadRequestException("Tasdiqlash kodi noto'g'ri kiritildi");
        }

        session.setVerified(true);
        otpRepository.save(session);
        return true;
    }

    public boolean isPhoneVerified(String phone) {
        return otpRepository.findTopByPhoneAndVerifiedTrueOrderByCreatedAtDesc(phone).isPresent();
    }

    public boolean isMockMode() {
        return mockMode;
    }
}
