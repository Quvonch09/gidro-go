package uz.gidrogo.modules.farm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmDispatchSettingsRepository extends JpaRepository<FarmDispatchSettings, Long> {
}
