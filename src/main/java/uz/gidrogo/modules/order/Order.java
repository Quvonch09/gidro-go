package uz.gidrogo.modules.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    @Column(name = "cart_group_id")
    private UUID cartGroupId;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "courier_id")
    private Long courierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "total_sum", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSum;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal longitude;

    @Column(name = "client_comment", length = 500)
    private String clientComment;

    @Column(name = "delivery_slot", length = 50)
    private String deliverySlot;

    @Column(name = "empty_bottles_returned")
    @Builder.Default
    private Integer emptyBottlesReturned = 0;

    @Column(name = "client_note", columnDefinition = "TEXT")
    private String clientNote;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
