package uz.gidrogo.modules.farm;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "farm_dispatch_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmDispatchSettings {

    @Id
    @Column(name = "farm_id")
    private Long farmId;

    @Column(name = "max_active_orders", nullable = false)
    @Builder.Default
    private Integer maxActiveOrders = 2;

    @Column(name = "second_order_radius_m", nullable = false)
    @Builder.Default
    private Integer secondOrderRadiusM = 1500;

    @Column(name = "service_time_min", nullable = false)
    @Builder.Default
    private Integer serviceTimeMin = 5;

    @Column(name = "preparing_alert_min", nullable = false)
    @Builder.Default
    private Integer preparingAlertMin = 15;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
