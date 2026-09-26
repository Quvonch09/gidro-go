package uz.gidrogo.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramUserRepository telegramUserRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.bot-username:GidroGoBot}")
    private String botUsername;

    @Value("${telegram.bot.mock-mode:false}")
    private boolean mockMode;

    private volatile boolean running = false;
    private Thread pollingThread;
    private RestTemplate restTemplate;

    @PostConstruct
    public void start() {
        if (botToken == null || botToken.isBlank()) {
            log.info("[TELEGRAM BOT] Bot token is not configured. Telegram bot disabled.");
            return;
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);

        // Delete any existing webhook so long-polling receives all updates
        try {
            String deleteUrl = "https://api.telegram.org/bot" + botToken + "/deleteWebhook?drop_pending_updates=false";
            ResponseEntity<String> res = restTemplate.getForEntity(deleteUrl, String.class);
            log.info("[TELEGRAM BOT] Webhook cleanup: {}", res.getBody());
        } catch (Exception e) {
            log.warn("[TELEGRAM BOT] Could not delete webhook: {}", e.getMessage());
        }

        running = true;
        pollingThread = Thread.ofVirtual().name("telegram-bot-polling").start(this::pollLoop);
        log.info("[TELEGRAM BOT] Started polling for @{}", botUsername);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        log.info("[TELEGRAM BOT] Polling stopped.");
    }

    private void pollLoop() {
        long offset = 0;
        while (running) {
            try {
                String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + offset + "&timeout=20";
                String responseStr = restTemplate.getForObject(url, String.class);
                if (responseStr == null) {
                    continue;
                }

                JsonNode root = objectMapper.readTree(responseStr);
                if (root.path("ok").asBoolean(false) && root.has("result")) {
                    for (JsonNode update : root.get("result")) {
                        long updateId = update.get("update_id").asLong();
                        offset = Math.max(offset, updateId + 1);
                        try {
                            processUpdate(update);
                        } catch (Exception ex) {
                            log.error("[TELEGRAM BOT] Error processing update {}: {}", updateId, ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("[TELEGRAM BOT] Polling error: {}. Waiting 3s...", e.getMessage());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        }
    }

    public void processUpdate(JsonNode update) {
        if (!update.has("message")) {
            return;
        }

        JsonNode message = update.get("message");
        long chatId = message.path("chat").path("id").asLong();
        long fromId = message.path("from").path("id").asLong();
        String firstName = message.path("from").path("first_name").asText("");
        String lastName = message.path("from").path("last_name").asText("");
        String username = message.path("from").path("username").asText("");

        // 1. Foydalanuvchi kontakt yuborgan holat (request_contact)
        if (message.has("contact")) {
            JsonNode contact = message.get("contact");
            String rawPhone = contact.path("phone_number").asText();
            String normalizedPhone = OtpService.normalizePhone(rawPhone);

            // Bazada saqlash yoki yangilash
            TelegramUser user = telegramUserRepository.findByTelegramId(fromId)
                    .orElseGet(() -> TelegramUser.builder().telegramId(fromId).build());
            user.setChatId(String.valueOf(chatId));
            user.setPhone(normalizedPhone);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUsername(username);
            user.setUpdatedAt(Instant.now());
            telegramUserRepository.save(user);

            // Redis keshiga saqlash (30 kun)
            try {
                redisTemplate.opsForValue().set("telegram:chat:" + normalizedPhone, String.valueOf(chatId), 30, TimeUnit.DAYS);
            } catch (Exception ignored) {}

            log.info("[TELEGRAM BOT] Linked phone {} to Telegram chatId {}", normalizedPhone, chatId);

            // Agar ushbu raqam uchun kutilayotgan faol OTP kod mavjud bo'lsa, zudlik bilan jo'natamiz
            String activeCode = null;
            try {
                activeCode = redisTemplate.opsForValue().get("otp:code:" + normalizedPhone);
            } catch (Exception ignored) {}

            if (activeCode != null && !activeCode.isBlank()) {
                String text = "✅ <b>Telefon raqamingiz muvaffaqiyatli bog'landi!</b> (" + normalizedPhone + ")\n\n" +
                        "🔐 GidroGo tasdiqlash kodingiz: <code>" + activeCode + "</code>\n\n" +
                        "⏱ Kod 2 daqiqa davomida amal qiladi.\n" +
                        "⚠️ Ushbu kodni hech kimga bermang!";
                sendMessage(chatId, text, null);
            } else {
                String text = "✅ <b>Telefon raqamingiz muvaffaqiyatli bog'landi!</b> (" + normalizedPhone + ")\n\n" +
                        "Endi GidroGo ilovasida ro'yxatdan o'tish yoki kirishda tasdiqlash kodlari shu yerga yuboriladi.";
                sendMessage(chatId, text, null);
            }
            return;
        }

        // 2. Oddiy matnli xabarlar (/start yoki boshqa)
        String text = message.path("text").asText("").trim();
        Optional<TelegramUser> existingUser = telegramUserRepository.findByTelegramId(fromId);

        if (existingUser.isPresent() && existingUser.get().getPhone() != null && !existingUser.get().getPhone().isBlank()) {
            String phone = existingUser.get().getPhone();
            // Agar foydalanuvchida kutayotgan faol OTP kod bo'lsa
            String activeCode = null;
            try {
                activeCode = redisTemplate.opsForValue().get("otp:code:" + phone);
            } catch (Exception ignored) {}

            if (activeCode != null && !activeCode.isBlank()) {
                String msg = "🔐 Sizning faol tasdiqlash kodingiz: <code>" + activeCode + "</code>\n\n" +
                        "⏱ Kod 2 daqiqa davomida amal qiladi.\n" +
                        "⚠️ Ushbu kodni hech kimga bermang!";
                sendMessage(chatId, msg, null);
            } else {
                String greeting = firstName.isBlank() ? "Foydalanuvchi" : firstName;
                String msg = "👋 Assalomu alaykum, <b>" + greeting + "</b>!\n\n" +
                        "Sizning Telegram hisobingiz <b>" + phone + "</b> raqamiga bog'langan.\n" +
                        "GidroGo mobil ilovasidan so'ralgan tasdiqlash kodlari shu yerga yuboriladi.";
                sendMessage(chatId, msg, null);
            }
        } else {
            // Foydalanuvchi hali telefon raqamini yubormagan -> Kontakt yuborish tugmasini chiqaramiz
            String welcomeMsg = "👋 <b>Assalomu alaykum! GidroGo tizimiga xush kelibsiz.</b>\n\n" +
                    "💧 GidroGo ilovasida ro'yxatdan o'tish va kirish uchun tasdiqlash kodlarini ushbu bot orqali bepul qabul qilishingiz mumkin.\n\n" +
                    "Iltimos, pastdagi <b>'📱 Telefon raqamni yuborish'</b> tugmasini bosing:";

            Map<String, Object> contactButton = Map.of(
                    "text", "📱 Telefon raqamni yuborish",
                    "request_contact", true
            );
            Map<String, Object> keyboard = Map.of(
                    "keyboard", List.of(List.of(contactButton)),
                    "resize_keyboard", true,
                    "one_time_keyboard", true
            );
            sendMessage(chatId, welcomeMsg, keyboard);
        }
    }

    public boolean sendOtpMessage(String chatId, String code) {
        if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
            return false;
        }

        String text = "🔐 <b>GidroGo tasdiqlash kodingiz:</b> <code>" + code + "</code>\n\n" +
                "⏱ Kod 2 daqiqa davomida amal qiladi.\n" +
                "⚠️ Ushbu kodni hech kimga bermang!";

        try {
            return sendMessage(Long.parseLong(chatId), text, null);
        } catch (Exception e) {
            log.error("[TELEGRAM BOT] Failed to send OTP to chatId {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    public String getChatIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = OtpService.normalizePhone(phone);

        // 1. Check Redis
        try {
            String cached = redisTemplate.opsForValue().get("telegram:chat:" + normalized);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        } catch (Exception ignored) {}

        // 2. Check Database
        return telegramUserRepository.findByPhone(normalized)
                .map(TelegramUser::getChatId)
                .orElse(null);
    }

    private boolean sendMessage(long chatId, String text, Object replyMarkup) {
        if (botToken == null || botToken.isBlank()) {
            return false;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            payload.put("parse_mode", "HTML");
            if (replyMarkup != null) {
                payload.put("reply_markup", replyMarkup);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("[TELEGRAM BOT] sendMessage error for chat {}: {}", chatId, e.getMessage());
            return false;
        }
    }
}
