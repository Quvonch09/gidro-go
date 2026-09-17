package uz.gidrogo.modules.finance;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "finance_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 10)
    private String type; // INCOME / EXPENSE

    @Column(nullable = false, length = 50)
    private String category; // ORDER_PAYMENT, FUEL, SALARY, TRANSPORT, MAINTENANCE, OTHER

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "courier_id")
    private Long courierId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
