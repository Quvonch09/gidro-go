package uz.gidrogo.modules.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashHandoverRepository extends JpaRepository<CashHandover, Long> {
    List<CashHandover> findAllByCourierIdOrderByCreatedAtDesc(Long courierId);
    List<CashHandover> findAllByFarmIdOrderByCreatedAtDesc(Long farmId);
    List<CashHandover> findAllByFarmIdAndStatusOrderByCreatedAtDesc(Long farmId, String status);
    Optional<CashHandover> findFirstByCourierIdAndStatus(Long courierId, String status);
    boolean existsByCourierIdAndStatus(Long courierId, String status);
}
