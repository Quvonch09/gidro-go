package uz.gidrogo.modules.stock;

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

    @Query("SELECT COALESCE(SUM(v.quantity), 0) FROM VehicleStock v WHERE v.courierId = :courierId")
    BigDecimal sumQuantityByCourierId(@Param("courierId") Long courierId);
}
