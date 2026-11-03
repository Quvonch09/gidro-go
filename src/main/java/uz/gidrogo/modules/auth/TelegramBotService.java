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
    private final TelegramAccountRepository telegramAccountRepository;
    private final TelegramPhoneVerificationRepository phoneVerificationRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.bot-username:GidroGoBot}")
    private String botUsername;

    @Value("${telegram.bot.mock-mode:false}")
    private boolean mockMode;

    @Value("${telegram.bot.webhook.enabled:true}")
    private boolean webhookEnabled;

    @Value("${telegram.bot.webhook.url:https://169.58.215.170/api/telegram/webhook}")
    private String webhookUrl;

    @Value("${telegram.miniapp.url:https://gidrogo-miniapp.pages.dev}")
    private String miniAppUrl;

    @Value("${telegram.bot.secret-token:${app.telegram.webhook-secret:GidroGo_TgSecret_2026_SecureWebHookToken_x91A}}")
    private String webhookSecretToken;

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

        // Band 23: hamma chatlar uchun standart "GidroGo" menyu tugmasi (idempotent)
        if (!mockMode) {
            setChatMenuButton(null);
        }

        if (webhookEnabled && webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                String setWebhookUrl = "https://api.telegram.org/bot" + botToken + "/setWebhook";
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

                org.springframework.util.MultiValueMap<String, String> map = new org.springframework.util.LinkedMultiValueMap<>();
                map.add("url", webhookUrl);
                if (webhookSecretToken != null && !webhookSecretToken.isBlank()) {
                    map.add("secret_token", webhookSecretToken);
                }
                map.add("allowed_updates", "[\"message\"]");

                org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> request =
                        new org.springframework.http.HttpEntity<>(map, headers);
                ResponseEntity<String> res = restTemplate.postForEntity(setWebhookUrl, request, String.class);
                log.info("[TELEGRAM BOT] Webhook registered at {}: {}", webhookUrl, res.getBody());
            } catch (Exception e) {
                log.warn("[TELEGRAM BOT] Could not set webhook: {}", e.getMessage());
            }
            log.info("[TELEGRAM BOT] Operating in Webhook mode for @{} (polling disabled).", botUsername);
            return;
        }

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

        // 1. Foydalanuvchi kontakt yuborgan holat (request_contact - B-TG-3)
        if (message.has("contact")) {
            JsonNode contact = message.get("contact");
            long contactUserId = contact.path("user_id").asLong(0);

            // Anti-spoofing tekshiruvi: kontakt yuboruvchining o'ziga tegishli bo'lishi shart
            if (contactUserId != 0 && contactUserId != fromId) {
                log.warn("[TELEGRAM BOT] Contact spoofing attempt: fromId={}, contactUserId={}", fromId, contactUserId);
                sendMessage(chatId, "⚠️ <b>Faqat o'zingizning telefon raqamingizni yuborishingiz mumkin!</b>", null);
                return;
            }

            String rawPhone = contact.path("phone_number").asText();
            String normalizedPhone = OtpService.normalizePhone(rawPhone);

            // 1. telegram_phone_verifications ga yozish (30 kun amal qiladi)
            TelegramPhoneVerification verif = phoneVerificationRepository
                    .findTopByTelegramIdAndPhoneAndExpiresAtAfter(fromId, normalizedPhone, Instant.now())
                    .orElseGet(() -> TelegramPhoneVerification.builder()
                            .telegramId(fromId)
                            .phone(normalizedPhone)
                            .build());
            verif.setVerifiedAt(Instant.now());
            verif.setExpiresAt(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
            phoneVerificationRepository.save(verif);

            // 2. telegram_accounts ga bog'lash va mavjud User'ni tekshirish
            TelegramAccount account = telegramAccountRepository.findByTelegramId(fromId)
                    .orElseGet(() -> TelegramAccount.builder().telegramId(fromId).build());
            account.setPhone(normalizedPhone);
            account.setFirstName(firstName);
            account.setLastName(lastName);
            account.setUsername(username);
            account.setUpdatedAt(Instant.now());

            Optional<User> existingUserOpt = userRepository.findByPhone(normalizedPhone);
            existingUserOpt.ifPresent(u -> account.setUserId(u.getId()));
            telegramAccountRepository.save(account);

            // 3. Eski telegram_users jadvali bilan ham moslik saqlanadi
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

            log.info("[TELEGRAM BOT] Linked phone {} to Telegram chatId {} (userId: {})",
                    normalizedPhone, chatId, account.getUserId());

            // Agar ushbu raqam uchun kutilayotgan faol OTP kod mavjud bo'lsa, zudlik bilan jo'natamiz
            String activeCode = null;
            try {
                activeCode = redisTemplate.opsForValue().get("otp:code:" + normalizedPhone);
            } catch (Exception ignored) {}

            // Band 23: tasdiq xabari reply klaviaturani olib tashlaydi, keyin inline tugma va menyu tugmasi
            boolean ru = isRussian(message);
            String confirmed = ru
                    ? "✅ <b>Номер телефона успешно подтверждён!</b> (" + normalizedPhone + ")"
                    : "✅ <b>Telefon raqamingiz muvaffaqiyatli tasdiqlandi!</b> (" + normalizedPhone + ")";
            if (activeCode != null && !activeCode.isBlank()) {
                confirmed += ru
                        ? "\n\n🔐 Ваш код подтверждения GidroGo: <code>" + activeCode + "</code>\n\n"
                          + "⏱ Код действует 2 минуты.\n⚠️ Никому не сообщайте этот код!"
                        : "\n\n🔐 GidroGo tasdiqlash kodingiz: <code>" + activeCode + "</code>\n\n"
                          + "⏱ Kod 2 daqiqa davomida amal qiladi.\n⚠️ Ushbu kodni hech kimga bermang!";
            }
            sendMessage(chatId, confirmed, Map.of("remove_keyboard", true));
            sendMessage(chatId, ru
                    ? "💧 Теперь вы можете заказывать воду через <b>GidroGo</b>."
                    : "💧 Endi <b>GidroGo</b> orqali bemalol buyurtma berishingiz mumkin.", openAppButton(ru));
            setChatMenuButton(chatId);
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

            boolean ru = isRussian(message);
            if (activeCode != null && !activeCode.isBlank()) {
                String msg = ru
                        ? "🔐 Ваш действующий код подтверждения: <code>" + activeCode + "</code>\n\n"
                          + "⏱ Код действует 2 минуты.\n⚠️ Никому не сообщайте этот код!"
                        : "🔐 Sizning faol tasdiqlash kodingiz: <code>" + activeCode + "</code>\n\n"
                          + "⏱ Kod 2 daqiqa davomida amal qiladi.\n⚠️ Ushbu kodni hech kimga bermang!";
                sendMessage(chatId, msg, openAppButton(ru));
            } else {
                // Band 23: raqami tasdiqlangan mijozdan kontakt qayta so'ralmaydi
                String greeting = firstName.isBlank() ? (ru ? "Пользователь" : "Foydalanuvchi") : firstName;
                String msg = ru
                        ? "👋 Здравствуйте, <b>" + greeting + "</b>!\n\nВаш Telegram привязан к номеру <b>" + phone + "</b>."
                        : "👋 Assalomu alaykum, <b>" + greeting + "</b>!\n\nTelegram hisobingiz <b>" + phone + "</b> raqamiga bog'langan.";
                sendMessage(chatId, msg, openAppButton(ru));
            }
            setChatMenuButton(chatId);
        } else {
            // Foydalanuvchi hali telefon raqamini yubormagan -> Kontakt yuborish tugmasini chiqaramiz
            boolean ru = isRussian(message);
            String welcomeMsg = ru
                    ? "👋 <b>Здравствуйте! Добро пожаловать в GidroGo.</b>\n\n"
                      + "Для регистрации подтвердите номер телефона кнопкой <b>«📱 Отправить номер телефона»</b> ниже:"
                    : "👋 <b>Assalomu alaykum! GidroGo tizimiga xush kelibsiz.</b>\n\n"
                      + "💧 GidroGo ilovasida ro'yxatdan o'tish va kirish uchun tasdiqlash kodlarini ushbu bot orqali bepul qabul qilishingiz mumkin.\n\n"
                      + "Iltimos, pastdagi <b>'📱 Telefon raqamni yuborish'</b> tugmasini bosing:";

            Map<String, Object> contactButton = Map.of(
                    "text", ru ? "📱 Отправить номер телефона" : "📱 Telefon raqamni yuborish",
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

    private boolean isRussian(JsonNode message) {
        return message.path("from").path("language_code").asText("").toLowerCase().startsWith("ru");
    }

    private Map<String, Object> openAppButton(boolean ru) {
        Map<String, Object> button = Map.of(
                "text", ru ? "💧 Открыть GidroGo" : "💧 GidroGo'ni ochish",
                "web_app", Map.of("url", miniAppUrl));
        return Map.of("inline_keyboard", List.of(List.of(button)));
    }

    /** chatId null bo'lsa — bot uchun standart menyu tugmasi, aks holda shu chat uchun */
    void setChatMenuButton(Long chatId) {
        if (botToken == null || botToken.isBlank() || mockMode || restTemplate == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            if (chatId != null) {
                payload.put("chat_id", chatId);
            }
            payload.put("menu_button", Map.of(
                    "type", "web_app",
                    "text", "GidroGo",
                    "web_app", Map.of("url", miniAppUrl)));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> res = restTemplate.postForEntity(
                    "https://api.telegram.org/bot" + botToken + "/setChatMenuButton",
                    new HttpEntity<>(payload, headers), String.class);
            log.info("[TELEGRAM BOT] Menu button set (chat={}): {}", chatId != null ? chatId : "default", res.getBody());
        } catch (Exception e) {
            log.warn("[TELEGRAM BOT] setChatMenuButton xatolik (chat={}): {}", chatId, e.getMessage());
        }
    }

    private boolean sendMessage(long chatId, String text, Object replyMarkup) {
        if (botToken == null || botToken.isBlank()) {
            return false;
        }
        if (mockMode || restTemplate == null) {
            log.info("[TELEGRAM BOT][MOCK] chat={} text={} markup={}", chatId, text, replyMarkup);
            return true;
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
