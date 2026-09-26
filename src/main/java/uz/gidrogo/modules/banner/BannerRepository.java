package uz.gidrogo.modules.banner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query("""
        SELECT b FROM Banner b
        WHERE b.type = :type
          AND b.isActive = true
          AND (:farmId IS NULL OR b.farmId IS NULL OR b.farmId = :farmId)
        ORDER BY b.sortOrder ASC, b.id ASC
    """)
    List<Banner> findActiveBanners(@Param("type") String type, @Param("farmId") Long farmId);

    List<Banner> findAllByOrderBySortOrderAscIdAsc();
}
