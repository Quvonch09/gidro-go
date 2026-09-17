package uz.gidrogo.modules.rating;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "target_type", nullable = false, length = 10)
    private String targetType; // COURIER / FARM

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private int stars; // 1 to 5

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
