package uz.gidrogo.modules.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id")
    private Long farmId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(unique = true, length = 100)
    private String username;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 25)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, BLOCKED, PENDING_APPROVAL

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "vehicle_model", length = 100)
    private String vehicleModel;

    @Column(name = "vehicle_plate_number", length = 50)
    private String vehiclePlateNumber;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Column(name = "driver_license_number", length = 50)
    private String driverLicenseNumber;

    @Column(name = "passport_serial", length = 50)
    private String passportSerial;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
