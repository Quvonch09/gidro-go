package uz.gidrogo.modules.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findAllByTargetTypeAndTargetId(String targetType, Long targetId);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.targetType = :targetType AND r.targetId = :targetId")
    Double getAverageStars(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
