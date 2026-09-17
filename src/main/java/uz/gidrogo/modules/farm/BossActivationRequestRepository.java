package uz.gidrogo.modules.farm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BossActivationRequestRepository extends JpaRepository<BossActivationRequest, Long> {
    Optional<BossActivationRequest> findTopByBossUserIdOrderByRequestedAtDesc(Long bossUserId);
    List<BossActivationRequest> findAllByStatus(ActivationStatus status);
}
