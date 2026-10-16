package uz.gidrogo.modules.superadmin;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * SuperAdmin boshqaruv amallari jurnali. Yozuvlar o'zgarmaydi va o'chirilmaydi
 * (Hibernate @Immutable + bazada trigger, V12 migratsiya).
 */
@Entity
@Immutable
@Table(name = "superadmin_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 100)
    private String action; // AuditAction nomlari

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** JSON: {"old": {...}, "new": {...}, ...}. Parol, token va boshqa sirlar yozilmaydi. */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
