package uz.gidrogo.modules.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientAddressRepository extends JpaRepository<ClientAddress, Long> {
    List<ClientAddress> findAllByClientId(Long clientId);
    Optional<ClientAddress> findByClientIdAndIsDefaultTrue(Long clientId);
}
