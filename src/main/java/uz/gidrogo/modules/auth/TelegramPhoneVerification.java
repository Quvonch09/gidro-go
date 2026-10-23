package uz.gidrogo.modules.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "telegram_phone_verifications", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"telegram_id", "phone"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramPhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false)
    private Long telegramId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "verified_at", nullable = false)
    @Builder.Default
    private Instant verifiedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
