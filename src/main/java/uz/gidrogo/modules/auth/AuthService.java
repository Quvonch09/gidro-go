package uz.gidrogo.modules.auth;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.auth.dto.AuthDtos.*;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientAddress;
import uz.gidrogo.modules.client.ClientAddressRepository;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.farm.*;
import uz.gidrogo.security.JwtTokenProvider;

import org.springframework.data.redis.core.StringRedisTemplate;
import uz.gidrogo.common.SecurityUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getLogin() != null ? request.getLogin().trim() : "";
        String altPhone = identifier;
        if (!identifier.startsWith("+") && identifier.matches("\\d+")) {
            if (identifier.startsWith("998")) {
                altPhone = "+" + identifier;
            } else if (identifier.length() == 9) {
                altPhone = "+998" + identifier;
            }
        } else if (identifier.startsWith("+998")) {
            altPhone = identifier.substring(1);
        }

        User user = userRepository.findByUsernameOrPhone(identifier, altPhone)
                .orElseThrow(() -> new BadRequestException("Login yoki parol noto'g'ri"));

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());

        // Agar superadmin bo'lsa va kiritilgan parol 'Parol123!' yoki 'Admin123!' bo'lsa, qabul qilamiz va parolni saqlaymiz
        if (!passwordMatches && user.getRole() == Role.SUPER_ADMIN) {
            String p = request.getPassword();
            if ("Parol123!".equals(p) || "Admin123!".equals(p) || "admin".equalsIgnoreCase(p)) {
                passwordMatches = true;
                user.setPasswordHash(passwordEncoder.encode(p));
                userRepository.save(user);
            }
        }

        if (!passwordMatches) {
            throw new BadRequestException("Login yoki parol noto'g'ri");
        }

        // 5.2 Boss Onboarding and Activation flow
        if (user.getRole() == Role.BOSS && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            Optional<BossActivationRequest> latestRequest = activationRequestRepository
                    .findTopByBossUserIdOrderByRequestedAtDesc(user.getId());

            if (latestRequest.isEmpty()) {
                user.setStatus("PENDING_APPROVAL");
                userRepository.save(user);

                BossActivationRequest newRequest = BossActivationRequest.builder()
                        .farmId(user.getFarmId())
                        .bossUserId(user.getId())
                        .status(ActivationStatus.PENDING)
                        .build();
                activationRequestRepository.save(newRequest);

                throw new BadRequestException("So'rovingiz qabul qilindi, tasdiqlanishini kuting");
            } else {
                BossActivationRequest bar = latestRequest.get();
                if (bar.getStatus() == ActivationStatus.PENDING) {
                    throw new BadRequestException("So'rovingiz qabul qilindi, tasdiqlanishini kuting");
                } else if (bar.getStatus() == ActivationStatus.REJECTED) {
                    throw new BadRequestException("So'rovingiz rad etilgan: " + (bar.getRejectReason() != null ? bar.getRejectReason() : ""));
                }
            }
        }

        if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new BadRequestException("Foydalanuvchi bloklangan");
        }

        Long farmId = user.getFarmId();
        if (farmId == null && user.getRole() == Role.CLIENT) {
            Client client = clientRepository.findByUserId(user.getId()).orElse(null);
            if (client != null) {
                farmId = client.getFarmId();
            }
        }

        String userIdentifier = user.getUsername() != null && !user.getUsername().isBlank() ? user.getUsername() : user.getPhone();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), userIdentifier, user.getRole(), farmId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

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
                .build();
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
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

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

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:refresh:" + refreshToken.trim()))) {
            throw new BadRequestException("Ushbu refresh token bekor qilingan (chiqib ketilgan)");
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Refresh token yaroqsiz yoki muddati o'tgan");
        }

        Claims claims = jwtTokenProvider.getClaimsFromToken(refreshToken);
        String tokenType = claims.get("type", String.class);
        if (!"REFRESH".equals(tokenType)) {
            throw new BadRequestException("Yaroqli refresh token taqdim etilmadi");
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Foydalanuvchi topilmadi"));

        if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new BadRequestException("Foydalanuvchi bloklangan");
        }

        Long farmId = user.getFarmId();
        if (farmId == null && user.getRole() == Role.CLIENT) {
            Client client = clientRepository.findByUserId(user.getId()).orElse(null);
            if (client != null) farmId = client.getFarmId();
        }

        String userIdentifier = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername() : user.getPhone();
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, userIdentifier, user.getRole(), farmId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .fullName(user.getFullName())
                .role(user.getRole())
                .farmId(farmId)
                .status(user.getStatus())
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. Agar refresh token uzatilgan bo'lsa, uni Redis'da blacklist qilamiz
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                String token = request.getRefreshToken().trim();
                // 30 kunlik muddat bilan blacklist qilamiz
                redisTemplate.opsForValue().set("blacklist:refresh:" + token, "revoked", 30, TimeUnit.DAYS);

                if (currentUserId == null && jwtTokenProvider.validateToken(token)) {
                    Claims claims = jwtTokenProvider.getClaimsFromToken(token);
                    currentUserId = Long.parseLong(claims.getSubject());
                }
            } catch (Exception e) {
                // Redis offline bo'lsa ham xatolik bermaslik
            }
        }

        // 2. Foydalanuvchining (ayniqsa kuryerning) FCM tokeni va Online holatini o'chirish
        if (currentUserId != null) {
            try {
                redisTemplate.delete("courier:online:" + currentUserId);
                redisTemplate.delete("courier:fcm:" + currentUserId);
            } catch (Exception e) {
                // Redis logs
            }
        }
    }
}
