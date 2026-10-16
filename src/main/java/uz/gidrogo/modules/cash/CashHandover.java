package uz.gidrogo.modules.cash;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_handovers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false)
    private Long courierId;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "declared_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal declaredAmount;

    @Column(name = "confirmed_amount", precision = 14, scale = 2)
    private BigDecimal confirmedAmount;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, CONFIRMED, REJECTED

    @Column(name = "initiated_by", nullable = false, length = 30)
    @Builder.Default
    private String initiatedBy = "COURIER"; // COURIER, MANAGER

    @Column(name = "manager_id")
    private Long managerId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
