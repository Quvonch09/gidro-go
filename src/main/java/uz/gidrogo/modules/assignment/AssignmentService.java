package uz.gidrogo.modules.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.modules.courier.CourierDispatchService;
import uz.gidrogo.modules.order.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final OrderRepository orderRepository;
    private final @org.springframework.context.annotation.Lazy CourierDispatchService courierDispatchService;

    /**
     * Yangi buyurtma yaratilganda avtomatik eng yaqin kuryerga PENDING taklif yuborish
     * State machine: NEW -> SEARCHING -> PENDING offer (TTL 40s) -> accept (ASSIGNED) / reject / expired
     */
    @Transactional
    public boolean assignOrderToCourier(Order order) {
        return assignOrderToCourier(order, null);
    }

    @Transactional
    public boolean assignOrderToCourier(Order order, Long excludeCourierId) {
        if (order == null) return false;
        order.setStatus(OrderStatus.SEARCHING);
        orderRepository.save(order);
        log.info("Order {} avtomatik dispatch navbatiga kiritildi (SEARCHING)", order.getOrderNumber());
        courierDispatchService.dispatchOrderToNextCandidate(order);
        return true;
    }
}
