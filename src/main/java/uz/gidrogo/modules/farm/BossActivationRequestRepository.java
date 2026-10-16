package uz.gidrogo.modules.farm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BossActivationRequestRepository extends JpaRepository<BossActivationRequest, Long>,
        JpaSpecificationExecutor<BossActivationRequest> {
    Optional<BossActivationRequest> findTopByBossUserIdOrderByRequestedAtDesc(Long bossUserId);
    Optional<BossActivationRequest> findFirstByBossUserIdAndStatus(Long bossUserId, ActivationStatus status);
    List<BossActivationRequest> findAllByStatus(ActivationStatus status);
    long countByStatus(ActivationStatus status);

    /** SELECT … FOR UPDATE: ikki SuperAdmin bir so'rovga bir vaqtda qaror qila olmaydi. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BossActivationRequest r WHERE r.id = :id")
    Optional<BossActivationRequest> findByIdForUpdate(@Param("id") Long id);
}
