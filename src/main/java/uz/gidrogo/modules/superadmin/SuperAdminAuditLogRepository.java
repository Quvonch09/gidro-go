package uz.gidrogo.modules.superadmin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Faqat o'qish va qo'shish. UPDATE/DELETE endpointlari yo'q; bazada trigger bilan ham taqiqlangan (V12). */
@Repository
public interface SuperAdminAuditLogRepository extends JpaRepository<SuperAdminAuditLog, Long>,
        JpaSpecificationExecutor<SuperAdminAuditLog> {
    List<SuperAdminAuditLog> findAllByOrderByCreatedAtDesc();
    List<SuperAdminAuditLog> findAllByActionAndEntityId(String action, Long entityId);
}
