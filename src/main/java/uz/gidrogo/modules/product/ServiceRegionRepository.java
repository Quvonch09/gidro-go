package uz.gidrogo.modules.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRegionRepository extends JpaRepository<ServiceRegion, Long> {
    List<ServiceRegion> findAllByFarmId(Long farmId);
}
