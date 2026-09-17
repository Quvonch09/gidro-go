package uz.gidrogo.modules.courier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.courier.CourierDtos.*;
import uz.gidrogo.modules.finance.FinanceService;
import uz.gidrogo.modules.order.*;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
import uz.gidrogo.modules.stock.RestockLogRepository;
import uz.gidrogo.modules.stock.StockService;
import uz.gidrogo.modules.stock.VehicleStockRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderProblemLogRepository problemLogRepository;
    private final DeliveryPhotoRepository photoRepository;
    private final OrderService orderService;
    private final StockService stockService;
    private final FinanceService financeService;
    private final UserRepository userRepository;
    private final RestockLogRepository restockLogRepository;
    private final VehicleStockRepository vehicleStockRepository;
    private final StringRedisTemplate redisTemplate;

    public List<OrderResponse> getCourierOrders() {
        Long courierId = SecurityUtils.getCurrentUserId();
        List<OrderStatus> activeStatuses = List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY);
        return orderRepository.findAllByCourierIdAndStatusIn(courierId, activeStatuses).stream()
                .map(o -> orderService.mapToResponse(o, true))
                .toList();
    }

    @Transactional
    public OrderResponse acceptOrder(Long orderId) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new BadRequestException("Buyurtma taklif holatida emas");
        }

        // BR-03: Dastavkachi qabul qildi
        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(order.getStatus())
                .toStatus(OrderStatus.ASSIGNED)
                .changedBy(courierId)
                .build());

        return orderService.mapToResponse(order, true);
    }

    @Transactional
    public OrderResponse startDelivery(Long orderId) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.ON_THE_WAY);
        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prev)
                .toStatus(OrderStatus.ON_THE_WAY)
                .changedBy(courierId)
                .build());

        return orderService.mapToResponse(order, true);
    }

    @Transactional
    public void updateLocation(LocationUpdateRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        String locStr = request.getLatitude() + "," + request.getLongitude() + "," + System.currentTimeMillis();
        try {
            redisTemplate.opsForValue().set("courier:loc:" + courierId, locStr);
        } catch (Exception e) {
            log.warn("Redis lokatsiya saqlashda xatolik: {}", e.getMessage());
        }

        // NEARBY geofence tekshiruvi: Masofa <= 500 metr bo'lsa avtomatik NEARBY holatiga o'tadi
        List<Order> onTheWayOrders = orderRepository.findAllByCourierIdAndStatusIn(courierId, List.of(OrderStatus.ON_THE_WAY));
        for (Order order : onTheWayOrders) {
            double distance = GeoUtils.calculateDistanceMeters(
                    request.getLatitude(), request.getLongitude(),
                    order.getLatitude().doubleValue(), order.getLongitude().doubleValue()
            );

            if (distance <= 500.0) {
                order.setStatus(OrderStatus.NEARBY);
                orderRepository.save(order);

                statusHistoryRepository.save(OrderStatusHistory.builder()
                        .orderId(order.getId())
                        .fromStatus(OrderStatus.ON_THE_WAY)
                        .toStatus(OrderStatus.NEARBY)
                        .changedBy(null) // Tizim avtomatik
                        .build());

                log.info("Order {} mijozga 500m yaqinlashdi -> NEARBY", order.getOrderNumber());
            }
        }
    }

    @Transactional
    public OrderResponse deliverOrder(Long orderId, DeliverRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.ON_THE_WAY && order.getStatus() != OrderStatus.NEARBY) {
            throw new BadRequestException("Buyurtma yo'lda holatida bo'lishi kerak");
        }

        // BR-09: 1 ta foto MAJBURIY
        if (request.getPhotoUrl() == null || request.getPhotoUrl().isBlank()) {
            throw new BadRequestException("Yetkazib berilganlik rasmi yuklanishi shart");
        }

        photoRepository.save(DeliveryPhoto.builder()
                .orderId(order.getId())
                .photoUrl(request.getPhotoUrl())
                .build());

        // BR-05: Mahsulot qoldig'i YETKAZILGAN buyurtma bo'yicha avtomatik kamayadi
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        for (OrderItem item : items) {
            stockService.deductVehicleStock(courierId, item.getProductId(), item.getQuantity());
        }

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());

        // Agar online to'langan bo'lsa, to'g'ridan-to'g'ri COMPLETED bo'ladi
        if (order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(Instant.now());

            // Avtomatik moliya kirim yozuvi
            financeService.recordIncome(order.getFarmId(), order.getTotalSum(), order.getId(), courierId, "Online buyurtma to'lovi");
        }

        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prev)
                .toStatus(order.getStatus())
                .changedBy(courierId)
                .build());

        return orderService.mapToResponse(order, true);
    }

    @Transactional
    public OrderResponse confirmCashCollected(Long orderId) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Avval buyurtma rasm bilan yetkazilishi kerak");
        }

        // BR-07: Naqd buyurtmada "Pul olindi" tasdig'i talab qilinadi
        order.setPaymentStatus(PaymentStatus.CASH_COLLECTED);
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(OrderStatus.DELIVERED)
                .toStatus(OrderStatus.COMPLETED)
                .changedBy(courierId)
                .build());

        // Ferma moliyasiga kirim yozish
        financeService.recordIncome(order.getFarmId(), order.getTotalSum(), order.getId(), courierId, "Naqd buyurtma to'lovi");

        return orderService.mapToResponse(order, true);
    }

    @Transactional
    public OrderResponse reportProblem(Long orderId, ProblemReportRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if ("OTHER".equalsIgnoreCase(request.getReasonCode()) &&
                (request.getReasonText() == null || request.getReasonText().isBlank())) {
            throw new BadRequestException("'Boshqa' sababi tanlanganda matn kiritilishi majburiy");
        }

        problemLogRepository.save(OrderProblemLog.builder()
                .orderId(order.getId())
                .reasonCode(request.getReasonCode())
                .reasonText(request.getReasonText())
                .reportedBy(courierId)
                .build());

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.PROBLEM);
        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prev)
                .toStatus(OrderStatus.PROBLEM)
                .changedBy(courierId)
                .build());

        return orderService.mapToResponse(order, true);
    }

    private Order findCourierOrder(Long orderId, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        if (order.getCourierId() == null || !order.getCourierId().equals(courierId)) {
            throw new BadRequestException("Ushbu buyurtma sizga biriktirilmagan");
        }
        return order;
    }

    public List<CourierDailySummaryResponse> getCourierDailySummary(Long farmId, LocalDate targetDate) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();
        Instant since = date.atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<User> couriers = userRepository.findAllByFarmIdAndRole(farmId, Role.COURIER);
        List<CourierDailySummaryResponse> summaries = new ArrayList<>();

        for (User courier : couriers) {
            BigDecimal loadedToday = restockLogRepository.sumQuantityByCourierIdAndCreatedAtAfter(courier.getId(), since);
            BigDecimal soldToday = orderRepository.sumDeliveredBottlesByCourierSince(courier.getId(), since);
            BigDecimal remainingInVehicle = vehicleStockRepository.sumQuantityByCourierId(courier.getId());
            long completedOrdersCount = orderRepository.countCompletedByCourierSince(courier.getId(), since);
            BigDecimal cashCollected = orderRepository.sumCashCollectedByCourierSince(courier.getId(), since);
            BigDecimal onlineCollected = orderRepository.sumOnlineCollectedByCourierSince(courier.getId(), since);
            BigDecimal totalRevenue = cashCollected.add(onlineCollected);

            ParsedLocation loc = parseCourierLocation(courier.getId());
            String status = determineCourierStatus(courier.getId(), loc);

            summaries.add(CourierDailySummaryResponse.builder()
                    .courierId(courier.getId())
                    .fullName(courier.getFullName())
                    .phone(courier.getPhone())
                    .currentStatus(status)
                    .date(date)
                    .loadedBottlesToday(loadedToday)
                    .soldBottlesToday(soldToday)
                    .remainingBottlesInVehicle(remainingInVehicle)
                    .completedOrdersCount(completedOrdersCount)
                    .cashCollected(cashCollected)
                    .onlineCollected(onlineCollected)
                    .totalRevenue(totalRevenue)
                    .latitude(loc != null ? loc.lat : null)
                    .longitude(loc != null ? loc.lon : null)
                    .lastSeenAt(loc != null ? loc.timestamp : null)
                    .build());
        }

        return summaries;
    }

    public List<CourierTrackingResponse> getLiveTracking(Long farmId) {
        List<User> couriers = userRepository.findAllByFarmIdAndRole(farmId, Role.COURIER);
        List<CourierTrackingResponse> trackingList = new ArrayList<>();

        for (User courier : couriers) {
            ParsedLocation loc = parseCourierLocation(courier.getId());
            List<Order> activeOrders = orderRepository.findAllByCourierIdAndStatusIn(
                    courier.getId(),
                    List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY)
            );

            Order activeOrder = activeOrders.isEmpty() ? null : activeOrders.get(0);
            String status = determineCourierStatus(courier.getId(), loc);

            trackingList.add(CourierTrackingResponse.builder()
                    .courierId(courier.getId())
                    .fullName(courier.getFullName())
                    .phone(courier.getPhone())
                    .currentStatus(status)
                    .latitude(loc != null ? loc.lat : null)
                    .longitude(loc != null ? loc.lon : null)
                    .lastSeenAt(loc != null ? loc.timestamp : null)
                    .activeOrderId(activeOrder != null ? activeOrder.getId() : null)
                    .activeOrderNumber(activeOrder != null ? activeOrder.getOrderNumber() : null)
                    .activeOrderAddress(activeOrder != null ? activeOrder.getDeliveryAddress() : null)
                    .build());
        }

        return trackingList;
    }

    private ParsedLocation parseCourierLocation(Long courierId) {
        try {
            String val = redisTemplate.opsForValue().get("courier:loc:" + courierId);
            if (val != null && !val.isBlank()) {
                String[] parts = val.split(",");
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);
                Instant time = parts.length > 2 ? Instant.ofEpochMilli(Long.parseLong(parts[2])) : Instant.now();
                return new ParsedLocation(lat, lon, time);
            }
        } catch (Exception e) {
            log.debug("Lokatsiya o'qishda xatolik courier {}: {}", courierId, e.getMessage());
        }
        return null;
    }

    private String determineCourierStatus(Long courierId, ParsedLocation loc) {
        List<Order> activeOrders = orderRepository.findAllByCourierIdAndStatusIn(
                courierId,
                List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY)
        );

        if (!activeOrders.isEmpty()) {
            return activeOrders.get(0).getStatus().name();
        }

        if (loc != null && loc.timestamp != null) {
            if (Instant.now().minusSeconds(900).isBefore(loc.timestamp)) {
                return "IDLE";
            }
        }
        return "OFFLINE";
    }

    private record ParsedLocation(double lat, double lon, Instant timestamp) {}
}
