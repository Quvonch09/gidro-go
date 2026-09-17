package uz.gidrogo.modules.product;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false, length = 100)
    private String name; // '19L suv', '5L suv'

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price; // yetkazib berish narxi ichida

    @Column(name = "volume_liters", precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal volumeLiters = BigDecimal.valueOf(19.0);

    @Column(name = "deposit_price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal depositPrice = BigDecimal.ZERO;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
