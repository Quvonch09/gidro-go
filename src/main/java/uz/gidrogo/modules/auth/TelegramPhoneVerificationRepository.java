package uz.gidrogo.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface TelegramPhoneVerificationRepository extends JpaRepository<TelegramPhoneVerification, Long> {
    Optional<TelegramPhoneVerification> findTopByTelegramIdAndExpiresAtAfterOrderByVerifiedAtDesc(Long telegramId, Instant now);
    Optional<TelegramPhoneVerification> findTopByTelegramIdAndPhoneAndExpiresAtAfter(Long telegramId, String phone, Instant now);
    Optional<TelegramPhoneVerification> findTopByTelegramIdOrderByVerifiedAtDesc(Long telegramId);
    void deleteAllByExpiresAtBefore(Instant now);
}
