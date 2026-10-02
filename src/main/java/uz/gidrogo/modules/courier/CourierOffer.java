package uz.gidrogo.modules.courier;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "courier_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "courier_id", nullable = false)
    private Long courierId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;
}
