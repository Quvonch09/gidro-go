package uz.gidrogo.modules.superadmin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Faqat o'qish va qo'shish. UPDATE/DELETE endpointlari yo'q; bazada trigger bilan ham taqiqlangan (V12). */
@Repository
public interface SuperAdminAuditLogRepository extends JpaRepository<SuperAdminAuditLog, Long>,
        JpaSpecificationExecutor<SuperAdminAuditLog> {
    List<SuperAdminAuditLog> findAllByOrderByCreatedAtDesc();
    List<SuperAdminAuditLog> findAllByActionAndEntityId(String action, Long entityId);

    @Query("SELECT DISTINCT a.action FROM SuperAdminAuditLog a WHERE a.action IS NOT NULL ORDER BY a.action")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT a.entityType FROM SuperAdminAuditLog a WHERE a.entityType IS NOT NULL ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();
}
