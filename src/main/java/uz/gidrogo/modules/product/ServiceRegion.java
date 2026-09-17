package uz.gidrogo.modules.product;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "service_regions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName; // 'Qarshi', 'Shahrisabz'

    @Column(name = "polygon_json", nullable = false, columnDefinition = "TEXT")
    private String polygonJson; // [[lat, lon], [lat, lon], ...]

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
