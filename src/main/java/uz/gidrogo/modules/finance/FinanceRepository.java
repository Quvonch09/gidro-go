package uz.gidrogo.modules.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface FinanceRepository extends JpaRepository<FinanceTransaction, Long> {
    List<FinanceTransaction> findAllByFarmIdOrderByCreatedAtDesc(Long farmId);
    List<FinanceTransaction> findAllByFarmIdAndCourierIdOrderByCreatedAtDesc(Long farmId, Long courierId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinanceTransaction f WHERE f.farmId = :farmId AND f.type = :type AND f.createdAt >= :since")
    BigDecimal sumByFarmIdAndTypeSince(@Param("farmId") Long farmId, @Param("type") String type, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FinanceTransaction f WHERE f.farmId = :farmId AND f.type = :type")
    BigDecimal sumByFarmIdAndType(@Param("farmId") Long farmId, @Param("type") String type);
}
