package uz.gidrogo.modules.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUserId(Long userId);
    java.util.List<Client> findAllByFarmId(Long farmId);
    long countByFarmId(Long farmId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Client c WHERE c.farmId = :farmId AND c.createdAt >= :from AND c.createdAt < :to")
    long countByFarmIdAndCreatedAtBetween(@org.springframework.data.repository.query.Param("farmId") Long farmId,
                                          @org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                          @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}
