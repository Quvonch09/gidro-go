package uz.gidrogo.modules.feedback;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 20)
    private String source; // CLIENT, COURIER

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String category = "OTHER";

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "NEW"; // NEW, SEEN, RESOLVED

    @Column(name = "manager_note", length = 1000)
    private String managerNote;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
