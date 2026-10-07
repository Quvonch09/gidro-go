package uz.gidrogo.modules.courier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourierOfferRepository extends JpaRepository<CourierOffer, Long> {

    List<CourierOffer> findAllByCourierIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
            Long courierId, String status, Instant now);

    Optional<CourierOffer> findByOrderIdAndCourierId(Long orderId, Long courierId);

    Optional<CourierOffer> findFirstByOrderIdAndStatus(Long orderId, String status);

    List<CourierOffer> findAllByOrderId(Long orderId);

    List<CourierOffer> findAllByOrderIdAndStatus(Long orderId, String status);

    List<CourierOffer> findAllByExpiresAtBeforeAndStatus(Instant now, String status);

    @Modifying
    @Query("UPDATE CourierOffer co SET co.status = :newStatus WHERE co.orderId = :orderId AND co.status = 'PENDING'")
    int cancelPendingOffersForOrder(@Param("orderId") Long orderId, @Param("newStatus") String newStatus);
}
