package uz.gidrogo.modules.bottle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BottleLedgerRepository extends JpaRepository<BottleLedger, Long> {
    List<BottleLedger> findAllByClientIdAndFarmIdOrderByCreatedAtDesc(Long clientId, Long farmId);
    List<BottleLedger> findAllByOrderId(Long orderId);
}
