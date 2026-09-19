package uz.gidrogo.modules.stock;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "restock_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false)
    private Long courierId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(length = 255)
    @Builder.Default
    private String location = "Markaziy baza";

    @Column(name = "warehouse_manager_name", length = 255)
    private String warehouseManagerName;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
