package uz.gidrogo.modules.bottle;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "client_bottle_balances")
@IdClass(ClientBottleBalanceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientBottleBalance {

    @Id
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Id
    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    @Builder.Default
    private Integer balance = 0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
