package uz.gidrogo.modules.banner;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "badge_text", length = 100)
    private String badgeText;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "gradient_start", length = 50)
    @Builder.Default
    private String gradientStart = "#0284C7";

    @Column(name = "gradient_end", length = 50)
    @Builder.Default
    private String gradientEnd = "#1D61F2";

    @Column(name = "icon_name", length = 100)
    @Builder.Default
    private String iconName = "water_drop";

    @Column(name = "action_type", length = 50)
    @Builder.Default
    private String actionType = "NONE";

    @Column(name = "action_value", length = 500)
    private String actionValue;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "farm_id")
    private Long farmId;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String type = "HOME_SLIDER";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
