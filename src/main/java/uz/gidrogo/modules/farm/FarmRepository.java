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
public interface FarmRepository extends JpaRepository<Farm, Long>, JpaSpecificationExecutor<Farm> {
    Optional<Farm> findByName(String name);
    Optional<Farm> findByNameIgnoreCase(String name);
    Optional<Farm> findByPhone(String phone);
    List<Farm> findAllByStatus(FarmStatus status);
    long countByStatus(FarmStatus status);

    /** Boss–ferma bog'lanishining yagona manbasi: farms.boss_user_id. */
    Optional<Farm> findByBossUserId(Long bossUserId);

    /** SELECT … FOR UPDATE: boss biriktirish va holat o'zgarishida parallel so'rovlardan himoya. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Farm f WHERE f.id = :id")
    Optional<Farm> findByIdForUpdate(@Param("id") Long id);
}
