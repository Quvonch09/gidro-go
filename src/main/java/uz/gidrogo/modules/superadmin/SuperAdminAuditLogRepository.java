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

    @Query("""
        SELECT COUNT(a) FROM SuperAdminAuditLog a
        WHERE (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt < :to)
    """)
    long countTotal(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        SELECT COUNT(a) FROM SuperAdminAuditLog a
        WHERE (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt < :to)
          AND (UPPER(a.entityType) = 'FARM' OR a.action LIKE 'FARM_%')
    """)
    long countFarmActions(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        SELECT COUNT(a) FROM SuperAdminAuditLog a
        WHERE (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt < :to)
          AND (UPPER(a.entityType) IN ('BOSS', 'USER', 'CLIENT') 
               OR a.action LIKE 'BOSS_%' 
               OR a.action LIKE 'CLIENT_%')
    """)
    long countUserActions(@Param("from") Instant from, @Param("to") Instant to);
}
