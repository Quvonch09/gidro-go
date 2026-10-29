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
public interface ClientNotificationRepository extends JpaRepository<ClientNotification, Long> {

    Page<ClientNotification> findAllByClientIdOrderByCreatedAtDesc(Long clientId, Pageable pageable);

    Page<ClientNotification> findAllByClientIdAndIsReadFalseOrderByCreatedAtDesc(Long clientId, Pageable pageable);

    long countByClientIdAndIsReadFalse(Long clientId);

    Optional<ClientNotification> findByIdAndClientId(Long id, Long clientId);

    @Modifying
    @Query("UPDATE ClientNotification n SET n.isRead = true WHERE n.clientId = :clientId AND n.isRead = false")
    int markAllAsReadByClientId(@Param("clientId") Long clientId);
}

