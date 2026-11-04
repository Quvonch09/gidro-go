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

    long countByFarmIdAndStatus(Long farmId, String status);

    org.springframework.data.domain.Page<CashHandover> findAllByCourierIdOrderByCreatedAtDesc(Long courierId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<CashHandover> findAllByCourierIdAndStatusOrderByCreatedAtDesc(Long courierId, String status, org.springframework.data.domain.Pageable pageable);

    /** Tasdiq, rad va bekor qilish bir vaqtda kelsa ikki marta ishlanmasin */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT h FROM CashHandover h WHERE h.id = :id")
    Optional<CashHandover> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
