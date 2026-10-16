package uz.gidrogo.modules.cash;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface CashLedgerRepository extends JpaRepository<CashLedger, Long> {

    List<CashLedger> findAllByCourierIdOrderByCreatedAtDesc(Long courierId, Pageable pageable);

    boolean existsByOrderIdAndType(Long orderId, String type);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashLedger c WHERE c.courierId = :courierId AND c.type = :type")
    BigDecimal sumAmountByCourierIdAndType(@Param("courierId") Long courierId, @Param("type") String type);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashLedger c WHERE c.courierId = :courierId AND c.type = :type AND c.createdAt >= :since")
    BigDecimal sumAmountByCourierIdAndTypeSince(@Param("courierId") Long courierId, @Param("type") String type, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashLedger c WHERE c.farmId = :farmId AND c.type = :type")
    BigDecimal sumAmountByFarmIdAndType(@Param("farmId") Long farmId, @Param("type") String type);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashLedger c WHERE c.farmId = :farmId AND c.type = :type AND c.createdAt BETWEEN :from AND :to")
    BigDecimal sumAmountByFarmIdAndTypeBetween(@Param("farmId") Long farmId, @Param("type") String type, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT MIN(c.createdAt) FROM CashLedger c WHERE c.courierId = :courierId AND c.type = 'COLLECTED'")
    Instant findOldestCollectedAt(@Param("courierId") Long courierId);

    @Query("SELECT MAX(c.createdAt) FROM CashLedger c WHERE c.courierId = :courierId AND c.type = 'HANDOVER'")
    Instant findLatestHandoverAt(@Param("courierId") Long courierId);
}
