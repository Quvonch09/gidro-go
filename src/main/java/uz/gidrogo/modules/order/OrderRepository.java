package uz.gidrogo.modules.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findAllByClientIdOrderByCreatedAtDesc(Long clientId);
    List<Order> findAllByCourierIdOrderByCreatedAtDesc(Long courierId);
    List<Order> findAllByCourierIdAndStatusIn(Long courierId, List<OrderStatus> statuses);
    List<Order> findAllByFarmIdOrderByCreatedAtDesc(Long farmId);
    List<Order> findAllByFarmIdAndStatus(Long farmId, OrderStatus status);
    List<Order> findAllByCartGroupId(UUID cartGroupId);
    List<Order> findAllByStatusAndAssignedAtBefore(OrderStatus status, Instant timestamp);
    long countByFarmId(Long farmId);
    long countByFarmIdAndStatus(Long farmId, OrderStatus status);
    long countByFarmIdAndStatusIn(Long farmId, List<OrderStatus> statuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.farmId = :farmId AND o.createdAt >= :since")
    long countByFarmIdSince(@Param("farmId") Long farmId, @Param("since") Instant since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.farmId = :farmId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED AND o.completedAt >= :since")
    long countCompletedByFarmSince(@Param("farmId") Long farmId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi JOIN Order o ON oi.orderId = o.id WHERE o.farmId = :farmId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED AND o.completedAt >= :since")
    BigDecimal sumDeliveredBottlesByFarmSince(@Param("farmId") Long farmId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi JOIN Order o ON oi.orderId = o.id WHERE o.courierId = :courierId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED AND o.completedAt >= :since")
    BigDecimal sumDeliveredBottlesByCourierSince(@Param("courierId") Long courierId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(o.totalSum), 0) FROM Order o WHERE o.courierId = :courierId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED AND o.paymentMethod = uz.gidrogo.modules.order.PaymentMethod.CASH AND o.completedAt >= :since")
    BigDecimal sumCashCollectedByCourierSince(@Param("courierId") Long courierId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(o.totalSum), 0) FROM Order o WHERE o.courierId = :courierId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED AND o.paymentMethod = uz.gidrogo.modules.order.PaymentMethod.ONLINE AND o.completedAt >= :since")
    BigDecimal sumOnlineCollectedByCourierSince(@Param("courierId") Long courierId, @Param("since") Instant since);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.courierId = :courierId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED AND o.completedAt >= :since")
    long countCompletedByCourierSince(@Param("courierId") Long courierId, @Param("since") Instant since);

    long countByClientIdAndFarmId(Long clientId, Long farmId);

    @Query("SELECT COALESCE(SUM(o.totalSum), 0) FROM Order o WHERE o.clientId = :clientId AND o.farmId = :farmId AND o.status = uz.gidrogo.modules.order.OrderStatus.COMPLETED")
    BigDecimal sumSpentByClientAndFarm(@Param("clientId") Long clientId, @Param("farmId") Long farmId);

    @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.clientId = :clientId AND o.farmId = :farmId")
    Instant findLastOrderDateByClientAndFarm(@Param("clientId") Long clientId, @Param("farmId") Long farmId);

    List<Order> findAllByClientIdAndFarmIdOrderByCreatedAtDesc(Long clientId, Long farmId);
}
