package uz.gidrogo.modules.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {

    Page<Feedback> findAllByAuthorUserIdOrderByCreatedAtDesc(Long authorUserId, Pageable pageable);

    long countByFarmIdAndStatus(Long farmId, String status);
}
