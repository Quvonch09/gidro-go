package uz.gidrogo.modules.bottle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientBottleBalanceRepository extends JpaRepository<ClientBottleBalance, ClientBottleBalanceId> {
    Optional<ClientBottleBalance> findByClientIdAndFarmIdAndProductId(Long clientId, Long farmId, Long productId);
    List<ClientBottleBalance> findAllByClientId(Long clientId);
    List<ClientBottleBalance> findAllByClientIdAndFarmId(Long clientId, Long farmId);
}
