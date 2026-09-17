package uz.gidrogo.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramOtpRepository extends JpaRepository<TelegramOtpSession, Long> {
    Optional<TelegramOtpSession> findTopByPhoneOrderByCreatedAtDesc(String phone);
    Optional<TelegramOtpSession> findTopByPhoneAndVerifiedTrueOrderByCreatedAtDesc(String phone);
}
