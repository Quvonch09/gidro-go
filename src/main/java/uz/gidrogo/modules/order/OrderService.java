package uz.gidrogo.modules.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.assignment.AssignmentService;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientAddress;
import uz.gidrogo.modules.client.ClientAddressRepository;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.farm.FarmStatus;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
import uz.gidrogo.modules.product.Product;
import uz.gidrogo.modules.product.ProductRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final ProductRepository productRepository;
    private final FarmRepository farmRepository;
    private final ClientRepository clientRepository;
    private final ClientAddressRepository clientAddressRepository;
    private final UserRepository userRepository;
    private final AssignmentService assignmentService;
    private final uz.gidrogo.websocket.WebSocketEventPublisher eventPublisher;

    /**
     * 6.4: Ko'p-fermali savatni fermalar bo'yicha alohida buyurtmalarga ajratish va rasmiylashtirish
     */
    @Transactional
    public List<OrderResponse> checkout(CartCheckoutRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz profili topilmadi"));

        ClientAddress address = clientAddressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Yetkazib berish manzili topilmadi"));

        if (!address.getClientId().equals(client.getId())) {
            throw new BadRequestException("Ushbu manzil mijozga tegishli emas");
        }

        // 1. Mahsulotlarni tekshirish va fermaning farm_id si bo'yicha guruhlash
        Map<Long, List<CartItemDto>> itemsByFarm = new HashMap<>();
        Map<Long, Product> productCache = new HashMap<>();

        for (CartItemDto item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Mahsulot topilmadi: ID " + item.getProductId()));

            if (!product.isActive()) {
                throw new BadRequestException("Mahsulot hozirda nofaol: " + product.getName());
            }

            if (client.getFarmId() != null && !product.getFarmId().equals(client.getFarmId())) {
                Farm clientFarm = farmRepository.findById(client.getFarmId()).orElse(null);
                String farmName = clientFarm != null ? clientFarm.getName() : "tanlangan suv firmasi";
                throw new BadRequestException("Siz faqat o'zingiz biriktirilgan suv firmasi (" + farmName + ") dan buyurtma bera olasiz");
            }

            productCache.put(product.getId(), product);
            itemsByFarm.computeIfAbsent(product.getFarmId(), k -> new ArrayList<>()).add(item);
        }

        // Agar mijozda hali farmId belgilanmagan bo'lsa, buyurtma bergan fermasiga avtomatik biriktiriladi
        if (client.getFarmId() == null && !itemsByFarm.isEmpty()) {
            Long selectedFarmId = itemsByFarm.keySet().iterator().next();
            client.setFarmId(selectedFarmId);
            clientRepository.save(client);
        }

        UUID cartGroupId = UUID.randomUUID();
        List<Order> createdOrders = new ArrayList<>();

        // 2. Har bir ferma uchun alohida buyurtma yaratish
        for (Map.Entry<Long, List<CartItemDto>> entry : itemsByFarm.entrySet()) {
            Long farmId = entry.getKey();
            List<CartItemDto> farmItems = entry.getValue();

            Farm farm = farmRepository.findById(farmId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ferma topilmadi: ID " + farmId));

            if (farm.getStatus() == FarmStatus.BLOCKED) {
                throw new BadRequestException("Kechirasiz, " + farm.getName() + " vaqtincha yangi buyurtmalarni qabul qilmaydi");
            }

            BigDecimal totalSum = BigDecimal.ZERO;
            BigDecimal depositAmount = BigDecimal.ZERO;
            int totalWaterBottles = 0;
            BigDecimal bottleDepositUnit = BigDecimal.ZERO;

            for (CartItemDto ci : farmItems) {
                Product p = productCache.get(ci.getProductId());
                totalSum = totalSum.add(p.getPrice().multiply(ci.getQuantity()));
                if (p.getDepositPrice() != null && p.getDepositPrice().compareTo(BigDecimal.ZERO) > 0) {
                    totalWaterBottles += ci.getQuantity().intValue();
                    bottleDepositUnit = p.getDepositPrice();
                }
            }

            int emptyReturned = request.getEmptyBottlesReturned() != null ? request.getEmptyBottlesReturned() : 0;
            int newBottlesNeeded = Math.max(0, totalWaterBottles - emptyReturned);
            if (newBottlesNeeded > 0 && bottleDepositUnit.compareTo(BigDecimal.ZERO) > 0) {
                depositAmount = bottleDepositUnit.multiply(BigDecimal.valueOf(newBottlesNeeded));
                totalSum = totalSum.add(depositAmount);
            }

            String orderNumber = "ORD-" + System.currentTimeMillis() % 10000000 + "-" + farmId;

            Order order = Order.builder()
                    .orderNumber(orderNumber)
                    .cartGroupId(cartGroupId)
                    .farmId(farmId)
                    .clientId(client.getId())
                    .status(OrderStatus.NEW)
                    .paymentMethod(request.getPaymentMethod())
                    .paymentStatus(request.getPaymentMethod() == PaymentMethod.ONLINE ? PaymentStatus.PAID : PaymentStatus.PENDING)
                    .totalSum(totalSum)
                    .depositAmount(depositAmount)
                    .emptyBottlesReturned(emptyReturned)
                    .deliverySlot(request.getDeliverySlot())
                    .deliveryAddress(address.getAddress())
                    .latitude(address.getLatitude())
                    .longitude(address.getLongitude())
                    .clientComment(request.getClientComment())
                    .build();

            order = orderRepository.save(order);

            for (CartItemDto ci : farmItems) {
                Product p = productCache.get(ci.getProductId());
                OrderItem orderItem = OrderItem.builder()
                        .orderId(order.getId())
                        .productId(p.getId())
                        .quantity(ci.getQuantity())
                        .unitPrice(p.getPrice())
                        .build();
                orderItemRepository.save(orderItem);
            }

            statusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .toStatus(OrderStatus.NEW)
                    .changedBy(userId)
                    .build());

            // Avtomatik dastavkachiga biriktirish algoritmini ishga tushirish (BR-01...BR-12)
            assignmentService.assignOrderToCourier(order);

            createdOrders.add(order);
            try {
                eventPublisher.publishOrderCreated(farmId, mapToResponse(order, false));
            } catch (Exception ignored) {}
        }

        return createdOrders.stream()
                .map(o -> mapToResponse(o, false))
                .toList();
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        // Bekor qilish (CANCELLED): mijoz FAQAT dastavkachi hali yo'lga chiqmagan bo'lsa bekor qila oladi
        if (order.getStatus() == OrderStatus.ON_THE_WAY ||
                order.getStatus() == OrderStatus.NEARBY ||
                order.getStatus() == OrderStatus.DELIVERED ||
                order.getStatus() == OrderStatus.COMPLETED) {
            throw new BadRequestException("Dastavkachi allaqachon yo'lga chiqqan, buyurtmani bekor qilib bo'lmaydi");
        }

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prev)
                .toStatus(OrderStatus.CANCELLED)
                .changedBy(userId)
                .build());

        OrderResponse response = mapToResponse(order, false);
        try {
            eventPublisher.publishOrderStatusChanged(order.getFarmId(), order.getCourierId(), order.getClientId(), response);
        } catch (Exception ignored) {}

        return response;
    }

    @Transactional
    public OrderResponse reassignOrder(Long orderId, Long newCourierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        User courier = userRepository.findById(newCourierId)
                .orElseThrow(() -> new ResourceNotFoundException("Dastavkachi topilmadi"));

        if (!order.getFarmId().equals(courier.getFarmId())) {
            throw new BadRequestException("Dastavkachi ushbu fermaga tegishli emas");
        }

        OrderStatus prev = order.getStatus();
        order.setCourierId(newCourierId);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setAssignedAt(Instant.now());
        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prev)
                .toStatus(OrderStatus.ASSIGNED)
                .changedBy(SecurityUtils.getCurrentUserId())
                .build());

        OrderResponse response = mapToResponse(order, false);
        try {
            eventPublisher.publishOrderAssigned(order.getFarmId(), newCourierId, response);
        } catch (Exception ignored) {}

        return response;
    }

    public List<OrderResponse> getClientOrders() {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz profili topilmadi"));

        return orderRepository.findAllByClientIdOrderByCreatedAtDesc(client.getId()).stream()
                .map(o -> mapToResponse(o, false))
                .toList();
    }

    public List<OrderResponse> getManagerOrders(OrderStatus status) {
        return getManagerOrders(status, true, null, null, null, false);
    }

    public List<OrderResponse> getManagerOrders(OrderStatus status, Boolean todayOnly, LocalDate date, String startDateStr, String endDateStr, boolean all) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma topilmadi");
        }

        ZoneId zone = ZoneId.of("Asia/Tashkent");
        List<Order> orders;

        if (all || Boolean.FALSE.equals(todayOnly)) {
            // "Barcha buyurtmalar" (tarixiy barcha buyurtmalar yoki sana filtri bilan)
            if (startDateStr != null || endDateStr != null) {
                Instant start = parseStartDate(startDateStr);
                Instant end = parseEndDate(endDateStr);
                if (start != null && end != null) {
                    orders = status != null ?
                            orderRepository.findAllByFarmIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(farmId, status, start, end) :
                            orderRepository.findAllByFarmIdAndCreatedAtBetweenOrderByCreatedAtDesc(farmId, start, end);
                } else if (start != null) {
                    orders = status != null ?
                            orderRepository.findAllByFarmIdAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(farmId, status, start) :
                            orderRepository.findAllByFarmIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(farmId, start);
                } else {
                    orders = status != null ?
                            orderRepository.findAllByFarmIdAndStatus(farmId, status) :
                            orderRepository.findAllByFarmIdOrderByCreatedAtDesc(farmId);
                }
            } else {
                orders = status != null ?
                        orderRepository.findAllByFarmIdAndStatus(farmId, status) :
                        orderRepository.findAllByFarmIdOrderByCreatedAtDesc(farmId);
            }
        } else if (date != null) {
            // Berilgan aniq sana bo'yicha buyurtmalar
            Instant start = date.atStartOfDay(zone).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
            orders = status != null ?
                    orderRepository.findAllByFarmIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(farmId, status, start, end) :
                    orderRepository.findAllByFarmIdAndCreatedAtBetweenOrderByCreatedAtDesc(farmId, start, end);
        } else {
            // Default: "Buyurtmalar" bo'limida FAQAT BUGUNGI buyurtmalar ko'rinadi!
            Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
            orders = status != null ?
                    orderRepository.findAllByFarmIdAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(farmId, status, startOfToday) :
                    orderRepository.findAllByFarmIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(farmId, startOfToday);
        }

        return orders.stream().map(o -> mapToResponse(o, false)).toList();
    }

    private Instant parseStartDate(String str) {
        if (str == null || str.isBlank()) return null;
        str = str.trim();
        if (str.length() == 10) {
            LocalDate date = LocalDate.parse(str);
            return date.atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant();
        }
        return Instant.parse(str);
    }

    private Instant parseEndDate(String str) {
        if (str == null || str.isBlank()) return null;
        str = str.trim();
        if (str.length() == 10) {
            LocalDate date = LocalDate.parse(str);
            return date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant().minusNanos(1);
        }
        return Instant.parse(str);
    }

    public List<OrderResponse> getClientOrdersForManager(Long clientId) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma topilmadi");
        }
        return orderRepository.findAllByClientIdAndFarmIdOrderByCreatedAtDesc(clientId, farmId).stream()
                .map(o -> mapToResponse(o, false))
                .toList();
    }

    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));
        return mapToResponse(order, false);
    }

    public OrderResponse mapToResponse(Order order, boolean hideTotalForCourier) {
        Farm farm = farmRepository.findById(order.getFarmId()).orElse(null);
        Client client = clientRepository.findById(order.getClientId()).orElse(null);
        User clientUser = client != null ? userRepository.findById(client.getUserId()).orElse(null) : null;
        User courierUser = order.getCourierId() != null ? userRepository.findById(order.getCourierId()).orElse(null) : null;

        List<OrderItemResponse> itemResponses = orderItemRepository.findAllByOrderId(order.getId()).stream()
                .map(oi -> {
                    Product p = productRepository.findById(oi.getProductId()).orElse(null);
                    return OrderItemResponse.builder()
                            .id(oi.getId())
                            .productId(oi.getProductId())
                            .productName(p != null ? p.getName() : null)
                            .quantity(oi.getQuantity())
                            .unitPrice(oi.getUnitPrice())
                            .subtotal(oi.getUnitPrice().multiply(oi.getQuantity()))
                            .build();
                }).toList();

        // BR-08: Online buyurtmada summa dastavkachiga KO'RSATILMAYDI
        BigDecimal displayedTotal = hideTotalForCourier && order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() == PaymentStatus.PAID
                ? null
                : order.getTotalSum();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .cartGroupId(order.getCartGroupId())
                .farmId(order.getFarmId())
                .farmName(farm != null ? farm.getName() : null)
                .clientId(order.getClientId())
                .clientName(clientUser != null ? clientUser.getFullName() : null)
                .clientPhone(clientUser != null ? clientUser.getPhone() : null)
                .courierId(order.getCourierId())
                .courierName(courierUser != null ? courierUser.getFullName() : null)
                .courierPhone(courierUser != null ? courierUser.getPhone() : null)
                .courierVehicleModel(courierUser != null ? courierUser.getVehicleModel() : null)
                .courierVehiclePlateNumber(courierUser != null ? courierUser.getVehiclePlateNumber() : null)
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .totalSum(displayedTotal)
                .depositAmount(order.getDepositAmount())
                .emptyBottlesReturned(order.getEmptyBottlesReturned())
                .deliverySlot(order.getDeliverySlot())
                .deliveryAddress(order.getDeliveryAddress())
                .latitude(order.getLatitude())
                .longitude(order.getLongitude())
                .clientComment(order.getClientComment())
                .items(itemResponses)
                .assignedAt(order.getAssignedAt())
                .deliveredAt(order.getDeliveredAt())
                .completedAt(order.getCompletedAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
