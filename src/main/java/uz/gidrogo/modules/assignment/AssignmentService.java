package uz.gidrogo.modules.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.order.*;
import uz.gidrogo.modules.stock.StockService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Executes automatic courier assignment algorithm (BR-01...BR-12)
     */
    @Transactional
    public boolean assignOrderToCourier(Order order) {
        return assignOrderToCourier(order, null);
    }

    @Transactional
    public boolean assignOrderToCourier(Order order, Long excludeCourierId) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        if (items.isEmpty()) {
            return false;
        }

        // 1. Shu fermaga tegishli, faol holatdagi barcha dastavkachilar (users)
        List<User> farmCouriers = userRepository.findAllByFarmIdAndRole(order.getFarmId(), Role.COURIER).stream()
                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .filter(u -> excludeCourierId == null || !u.getId().equals(excludeCourierId))
                .toList();

        if (farmCouriers.isEmpty()) {
            transitionToPreparing(order, "Fermada boshqa faol dastavkachilar mavjud emas");
            return false;
        }

        // 2. Bo'sh dastavkachilar (faqat hozirda aktiv yo'ldagi buyurtmasi yo'qlar)
        List<User> idleCouriers = farmCouriers.stream()
                .filter(c -> isCourierIdle(c.getId()))
                .toList();

        // 3. Har bir nomzodning mashinasidagi zaxirasi tekshiriladi (BR-01)
        List<User> qualifiedCouriers = idleCouriers.stream()
                .filter(c -> items.stream().allMatch(item ->
                        stockService.hasSufficientStock(c.getId(), item.getProductId(), item.getQuantity())))
                .toList();

        if (qualifiedCouriers.isEmpty()) {
            log.info("Order {}: mos zaxiraga ega dastavkachi topilmadi. PREPARING ga o'tkazildi.", order.getOrderNumber());
            transitionToPreparing(order, "Yetarli zaxiraga ega dastavkachi topilmadi");
            return false;
        }

        // 4. Eng yaqin dastavkachini tanlash (BR-02)
        User bestCourier = qualifiedCouriers.stream()
                .min(Comparator.comparingDouble(c -> getDistanceToClient(c.getId(), order)))
                .orElse(null);

        if (bestCourier != null) {
            order.setCourierId(bestCourier.getId());
            order.setStatus(OrderStatus.ASSIGNED);
            order.setAssignedAt(Instant.now());
            orderRepository.save(order);

            statusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .fromStatus(OrderStatus.NEW)
                    .toStatus(OrderStatus.ASSIGNED)
                    .changedBy(null) // Tizim avtomatik
                    .build());

            log.info("Order {} -> Courier {} ga biriktirildi (ASSIGNED)", order.getOrderNumber(), bestCourier.getId());
            return true;
        }

        transitionToPreparing(order, "Mos dastavkachi topilmadi");
        return false;
    }

    private boolean isCourierIdle(Long courierId) {
        List<OrderStatus> activeStatuses = List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY);
        return orderRepository.findAllByCourierIdAndStatusIn(courierId, activeStatuses).isEmpty();
    }

    private double getDistanceToClient(Long courierId, Order order) {
        // Joylashuv Redis GEO dan olinadi (agar yo'q bo'lsa default 0.0)
        try {
            String loc = redisTemplate.opsForValue().get("courier:loc:" + courierId);
            if (loc != null && loc.contains(",")) {
                String[] parts = loc.split(",");
                double courierLat = Double.parseDouble(parts[0]);
                double courierLon = Double.parseDouble(parts[1]);
                return GeoUtils.calculateDistanceMeters(
                        courierLat, courierLon,
                        order.getLatitude().doubleValue(), order.getLongitude().doubleValue()
                );
            }
        } catch (Exception e) {
            log.warn("Redis lokatsiya o'qishda xatolik: {}", e.getMessage());
        }
        return 999999.0;
    }

    private void transitionToPreparing(Order order, String reason) {
        OrderStatus prevStatus = order.getStatus();
        order.setStatus(OrderStatus.PREPARING);
        orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prevStatus)
                .toStatus(OrderStatus.PREPARING)
                .changedBy(null)
                .build());
    }

    /**
     * BR-12: 30 soniya ichida qabul qilinmagan buyurtmalarni qayta taqsimlash scheduler'i
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processAssignmentTimeouts() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.SECONDS);
        List<Order> timedOutOrders = orderRepository.findAllByStatusAndAssignedAtBefore(OrderStatus.ASSIGNED, cutoff);

        for (Order order : timedOutOrders) {
            Long prevCourierId = order.getCourierId();
            log.warn("Order {} 30s timeout bo'ldi (Courier {} qabul qilmadi). Qayta taqsimlanmoqda...",
                    order.getOrderNumber(), prevCourierId);

            order.setCourierId(null);
            order.setStatus(OrderStatus.SEARCHING);
            orderRepository.save(order);

            boolean assigned = assignOrderToCourier(order, prevCourierId);
            if (!assigned) {
                transitionToPreparing(order, "Kuryer qabul qilmadi va boshqa bo'sh kuryer topilmadi");
            }
        }
    }
}
