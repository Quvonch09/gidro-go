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
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.finance.FinanceService;
import uz.gidrogo.modules.order.*;
import uz.gidrogo.modules.order.dto.OrderDtos.*;
import uz.gidrogo.modules.product.Product;
import uz.gidrogo.modules.product.ProductRepository;
import uz.gidrogo.modules.stock.RestockLogRepository;
import uz.gidrogo.modules.stock.StockService;
import uz.gidrogo.modules.stock.VehicleStockRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import uz.gidrogo.modules.stock.RestockLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final FarmRepository farmRepository;
    private final ProductRepository productRepository;
    private final RestockLogRepository restockLogRepository;
    private final VehicleStockRepository vehicleStockRepository;
    private final StringRedisTemplate redisTemplate;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final uz.gidrogo.modules.rating.RatingRepository ratingRepository;

    // ── Muammo sabablari ro'yxati ────────────────────────────────────────────

    public List<ProblemReasonItem> getProblemReasons() {
        return List.of(
                new ProblemReasonItem("CLIENT_UNREACHABLE", "Mijoz bilan bog'lanib bo'lmadi"),
                new ProblemReasonItem("ADDRESS_NOT_FOUND",  "Manzil topilmadi"),
                new ProblemReasonItem("VEHICLE_ISSUE",      "Avtomobil nosozligi"),
                new ProblemReasonItem("PRODUCT_ISSUE",      "Mahsulot muammosi"),
                new ProblemReasonItem("FUEL_EMPTY",         "Yoqilg'i tugadi"),
                new ProblemReasonItem("OTHER",              "Boshqa sabab")
        );
    }

    // ── Buyurtmalar ro'yxati ─────────────────────────────────────────────────

    public List<OrderResponse> getCourierOrders(String statusFilter) {
        Long courierId = SecurityUtils.getCurrentUserId();
        List<OrderStatus> statuses;

        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                statuses = List.of(OrderStatus.valueOf(statusFilter.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Noto'g'ri status: " + statusFilter);
            }
        } else {
            // Default: Faol (aktiv) buyurtmalar
            statuses = List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY);
        }

        return orderRepository.findAllByCourierIdAndStatusIn(courierId, statuses).stream()
                .map(o -> orderService.mapToResponse(o, true))
                .toList();
    }

    public OrderPageResponse getCourierOrdersPaged(String statusFilter, String startDateStr, String endDateStr, int page, int size) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("courierId"), courierId));

            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                try {
                    OrderStatus st = OrderStatus.valueOf(statusFilter.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), st));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Noto'g'ri status: " + statusFilter);
                }
            }

            Instant start = parseStartDate(startDateStr);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }

            Instant end = parseEndDate(endDateStr);
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Order> orderPage = orderRepository.findAll(spec, pageable);
        List<OrderResponse> content = orderPage.getContent().stream()
                .map(o -> orderService.mapToResponse(o, true))
                .toList();

        return OrderPageResponse.builder()
                .content(content)
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .currentPage(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .build();
    }

    // ── Bitta buyurtma detali (kuryer uchun) ────────────────────────────────

    public OrderResponse getOrderDetail(Long orderId) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);
        return orderService.mapToResponse(order, true);
    }

    // ── Buyurtmani qabul qilish ──────────────────────────────────────────────

    @Transactional
    public OrderResponse acceptOrder(Long orderId) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new BadRequestException("Buyurtma taklif holatida emas");
        }

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(order.getStatus())
                .toStatus(OrderStatus.ASSIGNED)
                .changedBy(courierId)
                .build());

        return orderService.mapToResponse(order, true);
    }

    // ── Buyurtmani rad etish ─────────────────────────────────────────────────

    @Transactional
    public OrderResponse rejectOrder(Long orderId, RejectOrderRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new BadRequestException("Faqat ASSIGNED holatidagi buyurtma rad etilishi mumkin");
        }

        // Buyurtmani SEARCHING holatiga qaytarib, kuryer birikmini olib tashlaymiz
        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.SEARCHING);
        order.setCourierId(null);
        order.setAssignedAt(null);
        order = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(prev)
                .toStatus(OrderStatus.SEARCHING)
                .changedBy(courierId)
                .build());

        problemLogRepository.save(OrderProblemLog.builder()
                .orderId(order.getId())
                .reasonCode("COURIER_REJECTED")
                .reasonText(request.getReason())
                .reportedBy(courierId)
                .build());

        log.info("Kuryer {} buyurtmani {} rad etdi: {}", courierId, orderId, request.getReason());
        return orderService.mapToResponse(order, false);
    }

    // ── Yo'lga chiqish ───────────────────────────────────────────────────────

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

    // ── Lokatsiya yangilash ──────────────────────────────────────────────────

    @Transactional
    public LocationUpdateResponse updateLocation(LocationUpdateRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        String locStr = request.getLatitude() + "," + request.getLongitude() + "," + System.currentTimeMillis();
        try {
            redisTemplate.opsForValue().set("courier:loc:" + courierId, locStr);
        } catch (Exception e) {
            log.warn("Redis lokatsiya saqlashda xatolik: {}", e.getMessage());
        }

        // NEARBY geofence tekshiruvi
        List<Order> onTheWayOrders = orderRepository.findAllByCourierIdAndStatusIn(
                courierId, List.of(OrderStatus.ON_THE_WAY));

        Long nearbyOrderId = null;
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
                        .changedBy(null)
                        .build());

                nearbyOrderId = order.getId();
                log.info("Order {} mijozga 500m yaqinlashdi -> NEARBY", order.getOrderNumber());
            }
        }

        return LocationUpdateResponse.builder()
                .status("OK")
                .nearbyTriggered(nearbyOrderId != null)
                .nearbyOrderId(nearbyOrderId)
                .build();
    }

    // ── Yetkazildi ───────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse deliverOrder(Long orderId, DeliverRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.ON_THE_WAY && order.getStatus() != OrderStatus.NEARBY) {
            throw new BadRequestException("Buyurtma yo'lda holatida bo'lishi kerak");
        }

        if (request.getPhotoUrl() == null || request.getPhotoUrl().isBlank()) {
            throw new BadRequestException("Yetkazib berilganlik rasmi yuklanishi shart");
        }

        photoRepository.save(DeliveryPhoto.builder()
                .orderId(order.getId())
                .photoUrl(request.getPhotoUrl())
                .build());

        // Qaytarilgan shishalar ma'lumotini saqlash
        if (request.getEmptyBottlesReturned() != null && request.getEmptyBottlesReturned() > 0) {
            order.setEmptyBottlesReturned(request.getEmptyBottlesReturned());
        }
        if (request.getClientNote() != null && !request.getClientNote().isBlank()) {
            order.setClientNote(request.getClientNote());
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        for (OrderItem item : items) {
            stockService.deductVehicleStock(courierId, item.getProductId(), item.getQuantity());
        }

        OrderStatus prev = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());

        if (order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(Instant.now());
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

    // ── Naqd to'lov tasdig'i ─────────────────────────────────────────────────

    @Transactional
    public OrderResponse confirmCashCollected(Long orderId, CashCollectedRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Order order = findCourierOrder(orderId, courierId);

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Avval buyurtma rasm bilan yetkazilishi kerak");
        }

        if (request != null && request.getAmountCollected() != null) {
            // Olingan summa tekshiruvi (farq qaydlash)
            BigDecimal expected = order.getTotalSum();
            BigDecimal actual = request.getAmountCollected();
            if (actual.compareTo(expected) != 0) {
                log.info("Order {}: Kutilgan summa {} so'm, olingan {} so'm",
                        order.getOrderNumber(), expected, actual);
            }
        }

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

        financeService.recordIncome(order.getFarmId(), order.getTotalSum(), order.getId(), courierId, "Naqd buyurtma to'lovi");

        return orderService.mapToResponse(order, true);
    }

    // ── Muammo haqida xabar ───────────────────────────────────────────────────

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

    // ── Online/Offline holat almashtirish ────────────────────────────────────

    @Transactional
    public Map<String, Object> toggleOnlineStatus(StatusToggleRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        String redisKey = "courier:online:" + courierId;

        boolean isOnline = Boolean.TRUE.equals(request.isOnlineEffective());
        if (isOnline) {
            redisTemplate.opsForValue().set(redisKey, "true");
            log.info("Kuryer {} ONLINE bo'ldi", courierId);
            return Map.of("status", "ONLINE", "message", "Siz endi onlinesiz");
        } else {
            redisTemplate.delete(redisKey);
            log.info("Kuryer {} OFFLINE bo'ldi", courierId);
            return Map.of("status", "OFFLINE", "message", "Siz offline holatga o'tdingiz");
        }
    }

    // ── Kuryer profili ───────────────────────────────────────────────────────

    public CourierProfileResponse getCourierProfile() {
        Long courierId = SecurityUtils.getCurrentUserId();
        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Kuryer topilmadi"));

        Farm farm = courier.getFarmId() != null ?
                farmRepository.findById(courier.getFarmId()).orElse(null) : null;

        LocalDate today = LocalDate.now();
        Instant since = today.atStartOfDay(ZoneId.systemDefault()).toInstant();

        BigDecimal soldToday = orderRepository.sumDeliveredBottlesByCourierSince(courierId, since);
        long completedToday = orderRepository.countCompletedByCourierSince(courierId, since);
        BigDecimal cashToday = orderRepository.sumCashCollectedByCourierSince(courierId, since);
        BigDecimal onlineToday = orderRepository.sumOnlineCollectedByCourierSince(courierId, since);
        BigDecimal vehicleStock = vehicleStockRepository.sumQuantityByCourierId(courierId);

        ParsedLocation loc = parseCourierLocation(courierId);
        Boolean isOnline = Boolean.TRUE.equals(redisTemplate.hasKey("courier:online:" + courierId));

        Double avgRating = ratingRepository.getAverageStars("COURIER", courierId);
        Double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 4.9;

        VehicleInfoDto vehicle = VehicleInfoDto.builder()
                .model(courier.getVehicleModel() != null ? courier.getVehicleModel() : "Chevrolet Damas")
                .plateNumber(courier.getVehiclePlateNumber() != null ? courier.getVehiclePlateNumber() : "01 A 777 BA")
                .maxCapacity(courier.getMaxCapacity() != null ? courier.getMaxCapacity() : 50)
                .licenseNumber(courier.getDriverLicenseNumber() != null ? courier.getDriverLicenseNumber() : "")
                .passportSerial(courier.getPassportSerial() != null ? courier.getPassportSerial() : "")
                .build();

        return CourierProfileResponse.builder()
                .id(courier.getId())
                .fullName(courier.getFullName())
                .phone(courier.getPhone())
                .avatarUrl(courier.getAvatarUrl())
                .status(courier.getStatus())
                .isOnline(isOnline)
                .farmId(courier.getFarmId())
                .farmName(farm != null ? farm.getName() : null)
                .rating(rating)
                .latitude(loc != null ? loc.lat() : null)
                .longitude(loc != null ? loc.lon() : null)
                .lastSeenAt(loc != null ? loc.timestamp() : null)
                .todayCompleted(completedToday)
                .todayCash(cashToday)
                .todayOnline(onlineToday)
                .todayTotal(cashToday.add(onlineToday))
                .vehicleStock(vehicleStock)
                .vehicleModel(vehicle.getModel())
                .vehiclePlateNumber(vehicle.getPlateNumber())
                .maxCapacity(vehicle.getMaxCapacity())
                .driverLicenseNumber(vehicle.getLicenseNumber())
                .passportSerial(vehicle.getPassportSerial())
                .vehicle(vehicle)
                .build();
    }

    // ── Kuryer dashboard (bugungi xulosa) ────────────────────────────────────

    public CourierDashboardResponse getCourierDashboard() {
        Long courierId = SecurityUtils.getCurrentUserId();
        LocalDate today = LocalDate.now();
        Instant since = today.atStartOfDay(ZoneId.systemDefault()).toInstant();

        long assigned = orderRepository.findAllByCourierIdAndStatusIn(courierId,
                List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY)).size();
        long completed = orderRepository.countCompletedByCourierSince(courierId, since);

        List<Order> problemOrders = orderRepository.findAllByCourierIdAndStatusIn(
                courierId, List.of(OrderStatus.PROBLEM));
        long problemCount = problemOrders.size();

        BigDecimal cash = orderRepository.sumCashCollectedByCourierSince(courierId, since);
        BigDecimal online = orderRepository.sumOnlineCollectedByCourierSince(courierId, since);
        BigDecimal sold = orderRepository.sumDeliveredBottlesByCourierSince(courierId, since);
        BigDecimal loaded = restockLogRepository.sumQuantityByCourierIdAndCreatedAtAfter(courierId, since);
        BigDecimal remaining = vehicleStockRepository.sumQuantityByCourierId(courierId);

        // So'nggi 5 ta buyurtma
        List<RecentOrderSummary> recent = orderRepository.findAllByCourierIdOrderByCreatedAtDesc(courierId)
                .stream()
                .limit(5)
                .map(o -> RecentOrderSummary.builder()
                        .id(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .clientName(null) // Client join qilmasdan tezroq
                        .status(o.getStatus().name())
                        .totalSum(o.getTotalSum())
                        .createdAt(o.getCreatedAt())
                        .build())
                .toList();

        return CourierDashboardResponse.builder()
                .date(today)
                .targetOrdersCount(15)
                .targetRevenue(BigDecimal.valueOf(1000000.0))
                .assignedOrders(assigned)
                .completedOrders(completed)
                .problemOrders(problemCount)
                .cashRevenue(cash)
                .onlineRevenue(online)
                .totalRevenue(cash.add(online))
                .loadedBottles(loaded)
                .soldBottles(sold)
                .remainingBottles(remaining)
                .recentOrders(recent)
                .build();
    }

    // ── Kuryer profili ma'lumotlarini yangilash ─────────────────────────────

    @Transactional
    public ProfileUpdateResponse updateCourierProfile(ProfileUpdateRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Kuryer topilmadi"));

        if (request.getPhone() != null && !request.getPhone().isBlank() && !request.getPhone().equals(courier.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone().trim())) {
                throw new BadRequestException("Bu telefon raqam allaqachon ro'yxatdan o'tgan");
            }
            courier.setPhone(request.getPhone().trim());
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new BadRequestException("Yangi parol o'rnatish uchun joriy parolni kiritish shart");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), courier.getPasswordHash())) {
                throw new BadRequestException("Joriy parol noto'g'ri");
            }
            if (request.getNewPassword().length() < 6) {
                throw new BadRequestException("Yangi parol kamida 6 belgidan iborat bo'lishi kerak");
            }
            courier.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        if (request.getVehicleModel() != null) {
            courier.setVehicleModel(request.getVehicleModel());
        }
        if (request.getVehiclePlateNumber() != null) {
            courier.setVehiclePlateNumber(request.getVehiclePlateNumber());
        }
        if (request.getMaxCapacity() != null) {
            courier.setMaxCapacity(request.getMaxCapacity());
        }
        if (request.getDriverLicenseNumber() != null) {
            courier.setDriverLicenseNumber(request.getDriverLicenseNumber());
        }
        if (request.getPassportSerial() != null) {
            courier.setPassportSerial(request.getPassportSerial());
        }
        if (request.getAvatarUrl() != null) {
            courier.setAvatarUrl(request.getAvatarUrl());
        }

        courier = userRepository.save(courier);

        return ProfileUpdateResponse.builder()
                .id(courier.getId())
                .fullName(courier.getFullName())
                .phone(courier.getPhone())
                .avatarUrl(courier.getAvatarUrl())
                .vehicleModel(courier.getVehicleModel())
                .vehiclePlateNumber(courier.getVehiclePlateNumber())
                .maxCapacity(courier.getMaxCapacity())
                .driverLicenseNumber(courier.getDriverLicenseNumber())
                .passportSerial(courier.getPassportSerial())
                .build();
    }

    // ── Mashinaga yuklashlar tarixi ──────────────────────────────────────────

    public StockHistoryPageResponse getCourierStockHistory(int page, int size, String dateStr) {
        Long courierId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));

        Page<RestockLog> logPage;
        if (dateStr != null && !dateStr.isBlank()) {
            LocalDate date = LocalDate.parse(dateStr.trim());
            Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusNanos(1);
            logPage = restockLogRepository.findAllByCourierIdAndCreatedAtBetweenOrderByCreatedAtDesc(courierId, start, end, pageable);
        } else {
            logPage = restockLogRepository.findAllByCourierIdOrderByCreatedAtDesc(courierId, pageable);
        }

        Map<Long, String> productNames = new java.util.HashMap<>();
        List<StockHistoryItem> content = logPage.getContent().stream()
                .map(l -> {
                    String pName = productNames.computeIfAbsent(l.getProductId(), id ->
                            productRepository.findById(id).map(Product::getName).orElse("Noma'lum mahsulot"));
                    return StockHistoryItem.builder()
                            .id(l.getId())
                            .productId(l.getProductId())
                            .productName(pName)
                            .quantity(l.getQuantity())
                            .location(l.getLocation() != null ? l.getLocation() : "Markaziy baza")
                            .warehouseManagerName(l.getWarehouseManagerName() != null ? l.getWarehouseManagerName() : "Akmal Rahimov")
                            .restockedAt(l.getCreatedAt())
                            .build();
                })
                .toList();

        return StockHistoryPageResponse.builder()
                .content(content)
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(logPage.getNumber())
                .build();
    }

    // ── FCM Device token saqlash ─────────────────────────────────────────────

    public void saveDeviceToken(DeviceTokenRequest request) {
        Long courierId = SecurityUtils.getCurrentUserId();
        String redisKey = "courier:fcm:" + courierId;
        try {
            redisTemplate.opsForValue().set(redisKey, request.getFcmToken());
            log.info("Kuryer {} FCM tokeni saqlandi", courierId);
        } catch (Exception e) {
            log.warn("FCM token saqlashda xatolik: {}", e.getMessage());
        }
    }

    // ── Mahsulotlar katalogi (kuryer uchun) ─────────────────────────────────

    public List<Map<String, Object>> getCourierProducts() {
        Long courierId = SecurityUtils.getCurrentUserId();
        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Kuryer topilmadi"));

        if (courier.getFarmId() == null) {
            return List.of();
        }

        return productRepository.findAllByFarmIdAndActiveTrue(courier.getFarmId()).stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "name", p.getName(),
                        "price", p.getPrice(),
                        "volumeLiters", p.getVolumeLiters(),
                        "depositPrice", p.getDepositPrice(),
                        "imageUrl", p.getImageUrl() != null ? p.getImageUrl() : ""
                ))
                .toList();
    }

    // ── Admin uchun: Kuryer kunlik xulosasi ──────────────────────────────────

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
                    .latitude(loc != null ? loc.lat() : null)
                    .longitude(loc != null ? loc.lon() : null)
                    .lastSeenAt(loc != null ? loc.timestamp() : null)
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
                    .latitude(loc != null ? loc.lat() : null)
                    .longitude(loc != null ? loc.lon() : null)
                    .lastSeenAt(loc != null ? loc.timestamp() : null)
                    .activeOrderId(activeOrder != null ? activeOrder.getId() : null)
                    .activeOrderNumber(activeOrder != null ? activeOrder.getOrderNumber() : null)
                    .activeOrderAddress(activeOrder != null ? activeOrder.getDeliveryAddress() : null)
                    .build());
        }

        return trackingList;
    }

    // ── Yordamchi metodlar ────────────────────────────────────────────────────

    private Order findCourierOrder(Long orderId, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        if (order.getCourierId() == null || !order.getCourierId().equals(courierId)) {
            throw new BadRequestException("Ushbu buyurtma sizga biriktirilmagan");
        }
        return order;
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

        if (loc != null && loc.timestamp() != null) {
            if (Instant.now().minusSeconds(900).isBefore(loc.timestamp())) {
                return "IDLE";
            }
        }
        return "OFFLINE";
    }

    private Instant parseStartDate(String str) {
        if (str == null || str.isBlank()) return null;
        str = str.trim();
        if (str.length() == 10) {
            LocalDate date = LocalDate.parse(str);
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        return Instant.parse(str);
    }

    private Instant parseEndDate(String str) {
        if (str == null || str.isBlank()) return null;
        str = str.trim();
        if (str.length() == 10) {
            LocalDate date = LocalDate.parse(str);
            return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusNanos(1);
        }
        return Instant.parse(str);
    }

    private record ParsedLocation(double lat, double lon, Instant timestamp) {}
}
