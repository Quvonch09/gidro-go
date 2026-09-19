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
public interface CourierNotificationRepository extends JpaRepository<CourierNotification, Long> {

    Page<CourierNotification> findAllByCourierIdOrderByCreatedAtDesc(Long courierId, Pageable pageable);

    Page<CourierNotification> findAllByCourierIdAndIsReadFalseOrderByCreatedAtDesc(Long courierId, Pageable pageable);

    long countByCourierIdAndIsReadFalse(Long courierId);

    Optional<CourierNotification> findByIdAndCourierId(Long id, Long courierId);

    @Modifying
    @Query("UPDATE CourierNotification n SET n.isRead = true WHERE n.courierId = :courierId AND n.isRead = false")
    int markAllAsReadByCourierId(@Param("courierId") Long courierId);
}
