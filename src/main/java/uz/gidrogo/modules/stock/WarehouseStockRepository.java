package uz.gidrogo.modules.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    List<WarehouseStock> findAllByFarmId(Long farmId);
    Optional<WarehouseStock> findByFarmIdAndProductId(Long farmId, Long productId);
}
