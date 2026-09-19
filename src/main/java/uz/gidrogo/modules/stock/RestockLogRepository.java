package uz.gidrogo.modules.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface RestockLogRepository extends JpaRepository<RestockLog, Long> {
    List<RestockLog> findAllByCourierIdOrderByCreatedAtDesc(Long courierId);

    Page<RestockLog> findAllByCourierIdOrderByCreatedAtDesc(Long courierId, Pageable pageable);

    Page<RestockLog> findAllByCourierIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long courierId, Instant start, Instant end, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM RestockLog r WHERE r.courierId = :courierId AND r.createdAt >= :since")
    BigDecimal sumQuantityByCourierIdAndCreatedAtAfter(@Param("courierId") Long courierId, @Param("since") Instant since);
}
