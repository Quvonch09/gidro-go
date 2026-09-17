package uz.gidrogo.modules.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByFarmIdAndActiveTrue(Long farmId);
    List<Product> findAllByFarmId(Long farmId);
    Optional<Product> findByIdAndFarmId(Long id, Long farmId);
}
