package uz.gidrogo.modules.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUserId(Long userId);
    java.util.List<Client> findAllByFarmId(Long farmId);
    long countByFarmId(Long farmId);
}
