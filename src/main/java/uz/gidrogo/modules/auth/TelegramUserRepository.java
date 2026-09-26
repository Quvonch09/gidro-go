package uz.gidrogo.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    Optional<TelegramUser> findByPhone(String phone);
    Optional<TelegramUser> findByTelegramId(Long telegramId);
    Optional<TelegramUser> findByChatId(String chatId);
}
