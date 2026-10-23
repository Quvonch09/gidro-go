package uz.gidrogo.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramAccountRepository extends JpaRepository<TelegramAccount, Long> {
    Optional<TelegramAccount> findByTelegramId(Long telegramId);
    Optional<TelegramAccount> findByUserId(Long userId);
    Optional<TelegramAccount> findByPhone(String phone);
}
