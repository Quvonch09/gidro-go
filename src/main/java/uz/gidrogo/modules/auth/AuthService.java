package uz.gidrogo.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ClientIp;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.dto.AuthDtos.*;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientAddress;
import uz.gidrogo.modules.client.ClientAddressRepository;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.farm.*;
import uz.gidrogo.security.JwtTokenProvider;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TelegramOtpService telegramOtpService;
    private final FarmRepository farmRepository;
    private final BossActivationRequestRepository activationRequestRepository;
    private final ClientRepository clientRepository;
    private final ClientAddressRepository clientAddressRepository;
    private final StringRedisTemplate redisTemplate;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;

    public CheckPhoneResponse checkPhone(CheckPhoneRequest request) {
        String phone = OtpService.normalizePhone(request.getPhone());
        Optional<User> userOpt = userRepository.findByUsernameOrPhone(phone, phone);
        if (userOpt.isEmpty()) {
            return CheckPhoneResponse.builder()
                    .status("NEW")
                    .message("Raqam ro'yxatdan o'tmagan, OTP yuborish mumkin")
                    .build();
        }

        User user = userOpt.get();
        if (user.getRole() == Role.COURIER) {
            return CheckPhoneResponse.builder()
                    .status("COURIER")
                    .message("Kuryer sifatida ro'yxatdan o'tgan")
                    .build();
        } else if (user.getRole() == Role.CLIENT) {
            return CheckPhoneResponse.builder()
                    .status("CLIENT")
                    .message("Mijoz sifatida ro'yxatdan o'tgan")
                    .build();
        } else {
            return CheckPhoneResponse.builder()
                    .status(user.getRole().name())
                    .message(user.getRole().name() + " sifatida ro'yxatdan o'tgan")
                    .build();
        }
    }

    /**
     * Yagona login (barcha rollar). Xatolar:
     * 401 INVALID_CREDENTIALS (umumiy xabar — qaysi biri noto'g'ri ekani aytilmaydi),
     * 429 TOO_MANY_LOGIN_ATTEMPTS, 403 USER_BLOCKED / CLIENT_BLOCKED,
     * Boss uchun 403 ACTIVATION_PENDING / ACTIVATION_REJECTED (token berilmaydi).
     * noRollbackFor: faollashtirish so'rovi 403 javob bilan birga saqlanib qolishi kerak.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        String rawIdentifier = null;
        if (request.getLogin() != null && !request.getLogin().isBlank() && !request.getLogin().equalsIgnoreCase("string")) {
            rawIdentifier = request.getLogin().trim();
        } else if (request.getUsername() != null && !request.getUsername().isBlank() && !request.getUsername().equalsIgnoreCase("string")) {
            rawIdentifier = request.getUsername().trim();
        } else if (request.getPhone() != null && !request.getPhone().isBlank() && !request.getPhone().equalsIgnoreCase("string")) {
            rawIdentifier = request.getPhone().trim();
        }

        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw ApiException.unprocessable("IDENTIFIER_REQUIRED", "phone", "Telefon raqam yoki login kiritilishi shart");
        }

        String identifier = rawIdentifier;
        String normalizedPhone = OtpService.normalizePhone(rawIdentifier);
        String altPhone = (normalizedPhone != null && normalizedPhone.startsWith("+")) ? normalizedPhone.substring(1) : normalizedPhone;

        String rateKey = (normalizedPhone != null && normalizedPhone.startsWith("+998")) ? normalizedPhone : identifier;
        String ip = ClientIp.ip();
        loginRateLimiter.checkAllowed(rateKey, ip);

        Optional<User> userOpt = userRepository.findByUsernameOrPhone(identifier, altPhone, normalizedPhone);
        boolean passwordMatches = userOpt.isPresent()
                && passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash());

        // O'chirilgan hisob — mavjud bo'lmagan hisob kabi javob oladi (holat oshkor qilinmaydi)
        if (!passwordMatches || "DELETED".equalsIgnoreCase(userOpt.get().getStatus())) {
            loginRateLimiter.recordFailure(rateKey, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Telefon yoki parol noto'g'ri");
        }
        loginRateLimiter.recordSuccess(rateKey);
        User user = userOpt.get();

        if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw ApiException.forbidden(user.getRole() == Role.CLIENT ? "CLIENT_BLOCKED" : "USER_BLOCKED",
                    "Hisobingiz bloklangan");
        }

        // TZ §7.2–7.3: Boss faollashtirish oqimi
        if (user.getRole() == Role.BOSS) {
            enforceBossActivation(user);
        }

        Long farmId = resolveFarmId(user);
        String userIdentifier = user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : user.getPhone();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), userIdentifier, user.getRole(), farmId);
        String refreshToken = refreshTokenService.issue(user.getId());

        String farmName = null;
        if (farmId != null) {
            farmName = farmRepository.findById(farmId).map(Farm::getName).orElse(null);
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .farmId(farmId)
                .farmName(farmName)
                .status(user.getStatus())
                .activationStatus(user.getActivationStatus())
                .build();
    }

    /**
     * activationStatus ≠ APPROVED bo'lsa token berilmaydi:
     * NOT_REQUESTED/PENDING → PENDING so'rov yaratiladi yoki mavjudi qaytariladi, 403 ACTIVATION_PENDING;
     * REJECTED → 403 ACTIVATION_REJECTED + sabab; keyingi kirishda yangi PENDING so'rov yaratiladi (TZ §7.3).
     */
    private void enforceBossActivation(User user) {
        BossActivationStatus status = user.getActivationStatus() != null
                ? user.getActivationStatus() : BossActivationStatus.NOT_REQUESTED;
        if (status == BossActivationStatus.APPROVED) {
            return;
        }

        if (status == BossActivationStatus.REJECTED) {
            BossActivationRequest last = activationRequestRepository
                    .findTopByBossUserIdOrderByRequestedAtDesc(user.getId()).orElse(null);
            // Keyingi kirish qayta so'rov hisoblanadi
            user.setActivationStatus(BossActivationStatus.NOT_REQUESTED);
            userRepository.save(user);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("activationStatus", BossActivationStatus.REJECTED);
            details.put("rejectReason", last != null ? last.getRejectReason() : null);
            details.put("decidedAt", last != null ? last.getDecidedAt() : null);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACTIVATION_REJECTED",
                    "So'rovingiz rad etilgan. Qayta kirsangiz, yangi so'rov yuboriladi", List.of(), details);
        }

        Long farmId = farmRepository.findByBossUserId(user.getId()).map(Farm::getId).orElse(null);
        if (farmId == null) {
            throw ApiException.forbidden("BOSS_HAS_NO_FARM", "Hisobingiz hech qaysi fermaga biriktirilmagan");
        }
        BossActivationRequest pending = activationRequestRepository
                .findFirstByBossUserIdAndStatus(user.getId(), ActivationStatus.PENDING)
                .orElseGet(() -> activationRequestRepository.save(BossActivationRequest.builder()
                        .farmId(farmId)
                        .bossUserId(user.getId())
                        .status(ActivationStatus.PENDING)
                        .build()));
        if (user.getActivationStatus() != BossActivationStatus.PENDING) {
            user.setActivationStatus(BossActivationStatus.PENDING);
            userRepository.save(user);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activationStatus", BossActivationStatus.PENDING);
        details.put("requestId", pending.getId());
        details.put("requestedAt", pending.getRequestedAt());
        throw new ApiException(HttpStatus.FORBIDDEN, "ACTIVATION_PENDING",
                "So'rovingiz yuborildi, SuperAdmin tasdiqlashini kuting", List.of(), details);
    }

    /** BOSS uchun ferma yagona manbadan (farms.boss_user_id) olinadi. */
    private Long resolveFarmId(User user) {
        if (user.getRole() == Role.BOSS) {
            return farmRepository.findByBossUserId(user.getId()).map(Farm::getId).orElse(null);
        }
        Long farmId = user.getFarmId();
        if (farmId == null && user.getRole() == Role.CLIENT) {
            Client client = clientRepository.findByUserId(user.getId()).orElse(null);
            if (client != null) {
                farmId = client.getFarmId();
            }
        }
        return farmId;
    }

    @Transactional
    public AuthResponse registerClient(ClientRegisterRequest request) {
        String phone = OtpService.normalizePhone(request.getPhone());

        boolean verified = otpService.validateAndConsumeVerificationToken(phone, request.getVerificationToken())
                || telegramOtpService.isPhoneVerified(phone);

        if (!verified) {
            throw new BadRequestException("Raqam tasdiqlanmagan yoki tasdiqlash tokeni eskirgan");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new uz.gidrogo.common.ConflictException("Bu raqam bilan allaqachon foydalanuvchi mavjud");
        }

        Farm farm = farmRepository.findById(request.getFarmId())
                .orElseThrow(() -> new BadRequestException("Tanlangan firma topilmadi"));

        if (farm.getStatus() == FarmStatus.BLOCKED || farm.getStatus() == FarmStatus.INACTIVE) {
            throw new BadRequestException("Tanlangan firma vaqtincha yangi mijozlarni qabul qilmaydi");
        }

        User user = User.builder()
                .farmId(farm.getId())
                .fullName(request.getFullName())
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CLIENT)
                .status("ACTIVE")
                .build();

        user = userRepository.save(user);

        Client client = Client.builder()
                .userId(user.getId())
                .farmId(farm.getId())
                .ratingAvg(new BigDecimal("5.00"))
                .build();
        client = clientRepository.save(client);

        BigDecimal lat = request.getLatitude() != null ? BigDecimal.valueOf(request.getLatitude()) : BigDecimal.ZERO;
        BigDecimal lon = request.getLongitude() != null ? BigDecimal.valueOf(request.getLongitude()) : BigDecimal.ZERO;

        ClientAddress address = ClientAddress.builder()
                .clientId(client.getId())
                .label("Asosiy manzil")
                .address(request.getAddress())
                .latitude(lat)
                .longitude(lon)
                .isDefault(true)
                .build();
        clientAddressRepository.save(address);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole(), farm.getId());
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

    /** Refresh: token bazada bo'lishi va bekor qilinmagan bo'lishi kerak; eski token bekor qilinadi (rotatsiya). */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Long userId = refreshTokenService.consume(request.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Foydalanuvchi topilmadi"));

        if ("BLOCKED".equalsIgnoreCase(user.getStatus()) || "DELETED".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_BLOCKED", "Hisob bloklangan yoki o'chirilgan");
        }
        if (user.getRole() == Role.BOSS && user.getActivationStatus() != BossActivationStatus.APPROVED) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACTIVATION_PENDING", "Hisob hali tasdiqlanmagan");
        }

        Long farmId = resolveFarmId(user);
        String userIdentifier = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername() : user.getPhone();
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, userIdentifier, user.getRole(), farmId);
        String newRefreshToken = refreshTokenService.issue(userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .farmId(farmId)
                .status(user.getStatus())
                .activationStatus(user.getActivationStatus())
                .build();
    }

    /**
     * Logout (BE-07): refresh token bazada bekor qilinadi, takroriy chaqiruv xatosiz o'tadi.
     * Foydalanuvchi aniqlansa — qurilma (FCM) tokeni va online holati o'chiriladi.
     */
    @Transactional
    public void logout(LogoutRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            Optional<Long> owner = refreshTokenService.revoke(request.getRefreshToken());
            if (currentUserId == null) {
                currentUserId = owner.orElse(null);
            }
        }

        if (currentUserId != null) {
            try {
                redisTemplate.delete("courier:online:" + currentUserId);
                redisTemplate.delete("courier:fcm:" + currentUserId);
            } catch (Exception e) {
                log.warn("Logout: Redis'dan qurilma tokenini o'chirib bo'lmadi: {}", e.getMessage());
            }
        }
    }
}
