package uz.gidrogo.modules.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryPhotoRepository extends JpaRepository<DeliveryPhoto, Long> {
    List<DeliveryPhoto> findAllByOrderId(Long orderId);
}
