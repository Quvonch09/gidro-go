package uz.gidrogo.modules.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleStockRepository extends JpaRepository<VehicleStock, Long> {
    List<VehicleStock> findAllByCourierId(Long courierId);
    Optional<VehicleStock> findByCourierIdAndProductId(Long courierId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VehicleStock v WHERE v.courierId = :courierId")
    List<VehicleStock> findAllWithLockByCourierId(@Param("courierId") Long courierId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VehicleStock v WHERE v.courierId = :courierId AND v.productId = :productId")
    Optional<VehicleStock> findWithLockByCourierIdAndProductId(@Param("courierId") Long courierId, @Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(v.quantity), 0) FROM VehicleStock v WHERE v.courierId = :courierId")
    BigDecimal sumQuantityByCourierId(@Param("courierId") Long courierId);
}
