package uz.gidrogo.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.dto.AuthDtos.AuthResponse;
import uz.gidrogo.modules.auth.dto.TelegramMiniAppDtos.*;
import uz.gidrogo.modules.client.*;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.farm.FarmStatus;
import uz.gidrogo.security.JwtTokenProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMiniAppService {

    private final TelegramAccountRepository telegramAccountRepository;
    private final TelegramPhoneVerificationRepository phoneVerificationRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final ClientAddressRepository clientAddressRepository;
    private final FarmRepository farmRepository;
    private final uz.gidrogo.modules.order.OrderRepository orderRepository;
    private final uz.gidrogo.modules.bottle.ClientBottleBalanceRepository bottleBalanceRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.mock-mode:false}")
    private boolean mockMode;

    public record ParsedTelegramUser(Long id, String firstName, String lastName, String username, String languageCode) {}

    /**
     * 1. initData HMAC-SHA256 validatsiyasi (B-TG-2).
     */
    public ParsedTelegramUser validateAndParseInitData(String initData) {
        if (initData == null || initData.isBlank()) {
            throw ApiException.badRequest("INVALID_INIT_DATA", "initData kiritilishi shart");
        }

        Map<String, String> params = new HashMap<>();
        String providedHash = null;

        String[] pairs = initData.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                if ("hash".equals(key)) {
                    providedHash = val;
                } else {
                    params.put(key, val);
                }
            }
        }

        if (providedHash == null) {
            throw ApiException.badRequest("INVALID_INIT_DATA", "initData tarkibida hash topilmadi");
        }

        // Mock-mode yoki test tokeni bo'lsa tekshiruvdan o'tkazish
        if (!mockMode && botToken != null && !botToken.isBlank()) {
            List<String> sortedKeys = new ArrayList<>(params.keySet());
            Collections.sort(sortedKeys);

            StringBuilder checkString = new StringBuilder();
            for (int i = 0; i < sortedKeys.size(); i++) {
                String k = sortedKeys.get(i);
                checkString.append(k).append("=").append(params.get(k));
                if (i < sortedKeys.size() - 1) {
                    checkString.append("\n");
                }
            }

            try {
                Mac hmacSecret = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKey = new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                hmacSecret.init(secretKey);
                byte[] secret = hmacSecret.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

                Mac hmacData = Mac.getInstance("HmacSHA256");
                hmacData.init(new SecretKeySpec(secret, "HmacSHA256"));
                byte[] rawHash = hmacData.doFinal(checkString.toString().getBytes(StandardCharsets.UTF_8));

                StringBuilder hex = new StringBuilder();
                for (byte b : rawHash) {
                    hex.append(String.format("%02x", b));
                }

                if (!hex.toString().equalsIgnoreCase(providedHash)) {
                    log.warn("Telegram initData hash mismatch: expected={}, provided={}", hex, providedHash);
                    throw ApiException.badRequest("INVALID_INIT_DATA", "Telegram initData imzosi noto'g'ri yoki buzilgan");
                }
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                log.error("Telegram initData HMAC error: {}", e.getMessage());
                throw ApiException.badRequest("INVALID_INIT_DATA", "Telegram ma'lumotlarini tekshirishda xatolik");
            }
        }

        // Foydalanuvchi ma'lumotlarini o'qish
        String userJson = params.get("user");
        if (userJson == null) {
            throw ApiException.badRequest("INVALID_INIT_DATA", "initData tarkibida user ma'lumoti topilmadi");
        }

        try {
            JsonNode node = objectMapper.readTree(userJson);
            Long id = node.path("id").asLong();
            String firstName = node.path("first_name").asText(null);
            String lastName = node.path("last_name").asText(null);
            String username = node.path("username").asText(null);
            String languageCode = node.path("language_code").asText(null);

            return new ParsedTelegramUser(id, firstName, lastName, username, languageCode);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_INIT_DATA", "User JSON format noto'g'ri");
        }
    }

    /**
     * POST /api/client/telegram/session (B-TG-2)
     */
    @Transactional
    public TelegramSessionResponse getOrCreateSession(TelegramSessionRequest request) {
        ParsedTelegramUser tgUser = validateAndParseInitData(request.getInitData());
        Long telegramId = tgUser.id();

        // 1. telegram_accounts jadvalidan qidirish
        Optional<TelegramAccount> accountOpt = telegramAccountRepository.findByTelegramId(telegramId);

        if (accountOpt.isPresent() && accountOpt.get().getUserId() != null) {
            User user = userRepository.findById(accountOpt.get().getUserId()).orElse(null);
            if (user != null && !"DELETED".equalsIgnoreCase(user.getStatus())) {
                if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
                    throw ApiException.forbidden("CLIENT_BLOCKED", "Hisobingiz bloklangan");
                }

                // Ro'yxatdan o'tgan mijozga darhol token berish
                Long farmId = user.getFarmId();
                if (farmId == null) {
                    clientRepository.findByUserId(user.getId()).ifPresent(c -> user.setFarmId(c.getFarmId()));
                    farmId = user.getFarmId();
                }

                String identifier = user.getUsername() != null ? user.getUsername() : user.getPhone();
                String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), identifier, user.getRole(), farmId);
                String refreshToken = refreshTokenService.issue(user.getId());
                String farmName = farmId != null ? farmRepository.findById(farmId).map(Farm::getName).orElse(null) : null;

                AuthResponse authUser = AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .role(user.getRole())
                        .farmId(farmId)
                        .farmName(farmName)
                        .status(user.getStatus())
                        .build();

                return TelegramSessionResponse.builder()
                        .registered(true)
                        .phoneVerified(true)
                        .phone(user.getPhone())
                        .telegramId(telegramId)
                        .firstName(tgUser.firstName())
                        .lastName(tgUser.lastName())
                        .username(tgUser.username())
                        .token(accessToken)
                        .refreshToken(refreshToken)
                        .user(authUser)
                        .build();
            }
        }

        // 2. Agar foydalanuvchi hali bog'lanmagan bo'lsa, telefon tasdiqlanganini tekshirish
        Optional<TelegramPhoneVerification> verifOpt = phoneVerificationRepository
                .findTopByTelegramIdAndExpiresAtAfterOrderByVerifiedAtDesc(telegramId, Instant.now());

        boolean phoneVerified = verifOpt.isPresent();
        String verifiedPhone = verifOpt.map(TelegramPhoneVerification::getPhone).orElse(null);

        // Agar telefon tasdiqlangan bo'lsa va shu telefonli User bazada allaqachon mavjud bo'lsa, avtomatik bog'lash
        if (verifiedPhone != null) {
            Optional<User> existingUserOpt = userRepository.findByPhone(verifiedPhone);
            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();
                TelegramAccount account = accountOpt.orElseGet(() -> TelegramAccount.builder()
                        .telegramId(telegramId)
                        .build());
                account.setUserId(existingUser.getId());
                account.setPhone(verifiedPhone);
                account.setFirstName(tgUser.firstName());
                account.setLastName(tgUser.lastName());
                account.setUsername(tgUser.username());
                account.setLanguageCode(tgUser.languageCode());
                account.setUpdatedAt(Instant.now());
                telegramAccountRepository.save(account);

                Long farmId = existingUser.getFarmId();
                String identifier = existingUser.getUsername() != null ? existingUser.getUsername() : existingUser.getPhone();
                String accessToken = jwtTokenProvider.generateAccessToken(existingUser.getId(), identifier, existingUser.getRole(), farmId);
                String refreshToken = refreshTokenService.issue(existingUser.getId());
                String farmName = farmId != null ? farmRepository.findById(farmId).map(Farm::getName).orElse(null) : null;

                AuthResponse authUser = AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(existingUser.getId())
                        .fullName(existingUser.getFullName())
                        .phone(existingUser.getPhone())
                        .role(existingUser.getRole())
                        .farmId(farmId)
                        .farmName(farmName)
                        .status(existingUser.getStatus())
                        .build();

                return TelegramSessionResponse.builder()
                        .registered(true)
                        .phoneVerified(true)
                        .phone(verifiedPhone)
                        .telegramId(telegramId)
                        .firstName(tgUser.firstName())
                        .lastName(tgUser.lastName())
                        .username(tgUser.username())
                        .token(accessToken)
                        .refreshToken(refreshToken)
                        .user(authUser)
                        .build();
            }
        }

        // 3. Ro'yxatdan o'tmagan mijoz uchun 15 daqiqalik vaqtinchalik ro'yxatdan o'tish tokeni (tempToken)
        String tempToken = UUID.randomUUID().toString();
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("telegramId", telegramId);
        sessionData.put("firstName", tgUser.firstName());
        sessionData.put("lastName", tgUser.lastName());
        sessionData.put("username", tgUser.username());
        sessionData.put("phone", verifiedPhone);

        try {
            redisTemplate.opsForValue().set("tg:session:" + tempToken, objectMapper.writeValueAsString(sessionData), 15, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis session error: {}", e.getMessage());
        }

        return TelegramSessionResponse.builder()
                .registered(false)
                .phoneVerified(phoneVerified)
                .phone(verifiedPhone)
                .telegramId(telegramId)
                .firstName(tgUser.firstName())
                .lastName(tgUser.lastName())
                .username(tgUser.username())
                .tempToken(tempToken)
                .build();
    }

    /**
     * POST /api/client/telegram/register (B-TG-4: Parolsiz va OTPsiz ro'yxatdan o'tish)
     */
    @Transactional
    public AuthResponse registerTelegramClient(TelegramRegisterRequest request) {
        Long telegramId = null;
        String phone = null;
        String firstName = null;
        String lastName = null;
        String username = null;

        // tempToken orqali sessiya ma'lumotlarini tiklash
        if (request.getTempToken() != null && !request.getTempToken().isBlank()) {
            String cached = redisTemplate.opsForValue().get("tg:session:" + request.getTempToken());
            if (cached != null) {
                try {
                    JsonNode node = objectMapper.readTree(cached);
                    telegramId = node.path("telegramId").asLong();
                    phone = node.path("phone").asText(null);
                    firstName = node.path("firstName").asText(null);
                    lastName = node.path("lastName").asText(null);
                    username = node.path("username").asText(null);
                } catch (Exception ignored) {}
            }
        }

        // Agar tempToken bo'lmasa, initData'dan olish
        if (telegramId == null && request.getInitData() != null && !request.getInitData().isBlank()) {
            ParsedTelegramUser tgUser = validateAndParseInitData(request.getInitData());
            telegramId = tgUser.id();
            firstName = tgUser.firstName();
            lastName = tgUser.lastName();
            username = tgUser.username();
        }

        if (telegramId == null) {
            throw ApiException.badRequest("SESSION_EXPIRED", "Sessiya eskirgan. Iltimos, Mini Appni qayta oching.");
        }

        // Telefonni tekshirish
        if (phone == null && request.getPhone() != null && !request.getPhone().isBlank()) {
            phone = OtpService.normalizePhone(request.getPhone());
        }

        if (phone == null) {
            // telegram_phone_verifications dan qidirish
            phone = phoneVerificationRepository.findTopByTelegramIdAndExpiresAtAfterOrderByVerifiedAtDesc(telegramId, Instant.now())
                    .map(TelegramPhoneVerification::getPhone)
                    .orElse(null);
        }

        if (phone == null) {
            throw ApiException.badRequest("PHONE_NOT_VERIFIED",
                    "Telefon raqamingiz tasdiqlanmagan. Iltimos, Telegram bot (@GidroGoBot) orqali kontaktingizni yuboring.");
        }

        // Ferma tekshiruvi
        Farm farm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Tanlangan ferma topilmadi"));

        if (farm.getStatus() == FarmStatus.BLOCKED || farm.getStatus() == FarmStatus.INACTIVE) {
            throw ApiException.conflict("FARM_NOT_ACTIVE", "Tanlangan ferma vaqtincha yangi mijozlarni qabul qilmaydi");
        }

        // Foydalanuvchi yaratish (passwordHash = null!)
        String fullName = request.getFullName();
        if (fullName == null || fullName.isBlank()) {
            fullName = (firstName != null ? firstName : "") + (lastName != null ? " " + lastName : "");
            fullName = fullName.trim().isEmpty() ? "Telegram Foydalanuvchi" : fullName.trim();
        }

        User user;
        Optional<User> existingUserOpt = userRepository.findByPhone(phone);
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (user.getFarmId() == null) {
                user.setFarmId(farm.getId());
            }
        } else {
            user = User.builder()
                    .farmId(farm.getId())
                    .fullName(fullName)
                    .phone(phone)
                    .passwordHash(null) // Parolsiz! (B-TG-1)
                    .role(Role.CLIENT)
                    .status("ACTIVE")
                    .build();
            user = userRepository.save(user);
        }

        // Client entity
        Client client = clientRepository.findByUserId(user.getId()).orElse(null);
        if (client == null) {
            client = Client.builder()
                    .userId(user.getId())
                    .farmId(farm.getId())
                    .build();
            client = clientRepository.save(client);
        }

        // ClientAddress
        clientAddressRepository.save(ClientAddress.builder()
                .clientId(client.getId())
                .label("Asosiy manzil")
                .address(request.getAddress())
                .latitude(request.getLatitude() != null ? BigDecimal.valueOf(request.getLatitude()) : BigDecimal.ZERO)
                .longitude(request.getLongitude() != null ? BigDecimal.valueOf(request.getLongitude()) : BigDecimal.ZERO)
                .isDefault(true)
                .build());

        // telegram_accounts ga bog'lash
        TelegramAccount account = telegramAccountRepository.findByTelegramId(telegramId).orElse(null);
        if (account == null) {
            account = TelegramAccount.builder().telegramId(telegramId).build();
        }
        account.setUserId(user.getId());
        account.setPhone(phone);
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setUsername(username);
        account.setUpdatedAt(Instant.now());
        telegramAccountRepository.save(account);

        // JWT tokenlar yaratish
        String identifier = user.getUsername() != null ? user.getUsername() : user.getPhone();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), identifier, user.getRole(), farm.getId());
        String refreshToken = refreshTokenService.issue(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .farmId(farm.getId())
                .farmName(farm.getName())
                .status(user.getStatus())
                .build();
    }

    /**
     * POST /api/client/ws-ticket (B-TG-6: Qisqa muddatli WebSocket ticket)
     */
    public WsTicketResponse createWsTicket() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Avtorizatsiyadan o'tilmagan");
        }

        String ticket = UUID.randomUUID().toString().replace("-", "");
        // 60 soniya yashash muddati
        redisTemplate.opsForValue().set("ws:ticket:" + ticket, String.valueOf(currentUserId), 60, TimeUnit.SECONDS);

        return WsTicketResponse.builder()
                .ticket(ticket)
                .expiresInSeconds(60)
                .build();
    }

    /**
     * PUT /api/client/farm (B-TG-9: Ferma almashtirish)
     */
    @Transactional
    public void changeClientFarm(ChangeFarmRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Avtorizatsiyadan o'tilmagan");
        }
        Client client = clientRepository.findByUserId(currentUserId)
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "Mijoz topilmadi"));

        // 1. Yangi ferma mavjudligi va faolligi
        Farm newFarm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Tanlangan ferma topilmadi"));

        if (newFarm.getStatus() != FarmStatus.ACTIVE) {
            throw ApiException.conflict("FARM_NOT_ACTIVE", "Tanlangan ferma faol emas");
        }

        if (newFarm.getId().equals(client.getFarmId())) {
            return; // O'zgarmagan
        }

        // 2. Faol buyurtmalar tekshiruvi (ASSIGNED, ON_THE_WAY, NEARBY, PENDING, DELIVERED)
        boolean hasActiveOrders = orderRepository.findAllByClientIdOrderByCreatedAtDesc(client.getId()).stream()
                .anyMatch(o -> o.getStatus() != uz.gidrogo.modules.order.OrderStatus.COMPLETED
                        && o.getStatus() != uz.gidrogo.modules.order.OrderStatus.CANCELLED
                        && o.getStatus() != uz.gidrogo.modules.order.OrderStatus.RETURNED);
        if (hasActiveOrders) {
            throw ApiException.conflict("ACTIVE_ORDERS_EXIST",
                    "Faol buyurtmalaringiz mavjud bo'lgan paytda boshqa fermaga o'tib bo'lmaydi");
        }

        // 3. Idishlar balansi (idish qarzi yoki depoziti 0 bo'lishi kerak)
        int bottleBalance = bottleBalanceRepository.findAllByClientId(client.getId()).stream()
                .mapToInt(b -> b.getBalance() != null ? b.getBalance() : 0)
                .sum();
        if (bottleBalance != 0) {
            throw ApiException.conflict("BOTTLE_BALANCE_NOT_ZERO",
                    "Idish balansingiz 0 bo'lishi kerak (joriy balans: " + bottleBalance + " ta). Avval amaldagi ferma bilan idish hisob-kitobini yakunlang");
        }

        // Fermani almashtirish
        client.setFarmId(newFarm.getId());
        clientRepository.save(client);

        User user = userRepository.findById(currentUserId).orElse(null);
        if (user != null) {
            user.setFarmId(newFarm.getId());
            userRepository.save(user);
        }
    }

    /**
     * GET /api/client/farms/{id} (B-TG-10: Ochiq ferma ma'lumotlari, boss maxfiy ma'lumotlarisiz)
     */
    public FarmPublicDetailsResponse getFarmPublicDetails(Long farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Ferma topilmadi: ID=" + farmId));

        return FarmPublicDetailsResponse.builder()
                .id(farm.getId())
                .name(farm.getName())
                .farmCode(String.format("farm-%03d", farm.getId()))
                .address(farm.getAddress())
                .phone(farm.getPhone())
                .status(farm.getStatus() != null ? farm.getStatus().name() : "ACTIVE")
                .latitude(farm.getLatitude() != null ? farm.getLatitude().doubleValue() : null)
                .longitude(farm.getLongitude() != null ? farm.getLongitude().doubleValue() : null)
                .workingHours("08:00 - 20:00")
                .logoUrl(farm.getLogoUrl())
                .description(farm.getCoverageAreas() != null ? farm.getCoverageAreas() : "")
                .build();
    }
}
