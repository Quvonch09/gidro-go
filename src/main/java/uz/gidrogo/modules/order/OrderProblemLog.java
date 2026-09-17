package uz.gidrogo.modules.order;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "order_problem_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderProblemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode; // CLIENT_UNREACHABLE, ADDRESS_NOT_FOUND, VEHICLE_ISSUE, PRODUCT_ISSUE, FUEL_EMPTY, OTHER

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    @Column(name = "reported_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant reportedAt = Instant.now();
}
