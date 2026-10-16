package uz.gidrogo.modules.bottle;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "bottle_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BottleLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer delta; // +q or -e

    @Column(nullable = false, length = 40)
    private String reason; // DELIVERED_FULL, EMPTY_RETURNED, MANUAL_ADJUST

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
