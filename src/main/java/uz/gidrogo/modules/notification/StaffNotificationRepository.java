package uz.gidrogo.modules.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffNotificationRepository extends JpaRepository<StaffNotification, Long> {

    Page<StaffNotification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<StaffNotification> findAllByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    Optional<StaffNotification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE StaffNotification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}
