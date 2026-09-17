package uz.gidrogo.modules.farm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FarmRepository extends JpaRepository<Farm, Long> {
    Optional<Farm> findByName(String name);
    Optional<Farm> findByPhone(String phone);
    List<Farm> findAllByStatus(FarmStatus status);
}
