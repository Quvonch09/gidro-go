package uz.gidrogo.modules.superadmin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuperAdminAuditLogRepository extends JpaRepository<SuperAdminAuditLog, Long> {
    List<SuperAdminAuditLog> findAllByOrderByCreatedAtDesc();
}
