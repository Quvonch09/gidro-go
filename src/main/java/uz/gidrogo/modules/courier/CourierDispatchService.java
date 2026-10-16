package uz.gidrogo.modules.courier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.courier.CourierOfferDtos.*;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmDispatchSettings;
import uz.gidrogo.modules.farm.FarmDispatchSettingsRepository;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.notification.NotificationService;
import uz.gidrogo.modules.order.*;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
import uz.gidrogo.modules.stock.VehicleStock;
import uz.gidrogo.modules.stock.VehicleStockRepository;
import uz.gidrogo.websocket.CourierWebSocketHandler;
import uz.gidrogo.websocket.WebSocketEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierDispatchService {

    private final CourierOfferRepository courierOfferRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final FarmDispatchSettingsRepository farmDispatchSettingsRepository;
    private final VehicleStockRepository vehicleStockRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderProblemLogRepository problemLogRepository;
    private final ApplicationContext applicationContext;
    private final CourierWebSocketHandler courierWebSocketHandler;
    private final WebSocketEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;

    // Platform constants
    private static final int OFFER_TTL_SEC = 30;
    private static final int ACCEPT_GRACE_SEC = 5;
    private static final int ETA_TIE_SEC = 60;
    private static final int LOCATION_STALE_SEC = 120;
    private static final double ROAD_FACTOR = 1.3;
    private static final double AVG_SPEED_KMH = 25.0;
    private static final int REOFFER_COOLDOWN_MIN = 10;

    private OrderService getOrderService() {
        return applicationContext.getBean(OrderService.class);
    }

    /**
     * Kuryer uchun faol takliflar ro'yxati (2.1 GET /api/courier/orders/offers)
     */
    @Transactional(readOnly = true)
    public List<CourierOfferResponse> getOffersForCourier(Long courierId, Double lat, Double lon) {
        if (courierId == null || !isCourierOnline(courierId)) {
            return List.of();
        }

        Instant now = Instant.now();
        List<CourierOffer> pendingOffers = courierOfferRepository
                .findAllByCourierIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(courierId, "PENDING", now);

        List<CourierOfferResponse> responses = new ArrayList<>();
        for (CourierOffer offer : pendingOffers) {
            Order order = orderRepository.findById(offer.getOrderId()).orElse(null);
            if (order == null) continue;

            if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.SEARCHING && order.getStatus() != OrderStatus.PREPARING) {
                continue;
            }

            Farm farm = farmRepository.findById(order.getFarmId()).orElse(null);
            long ttlSeconds = Math.max(0, Duration.between(now, offer.getExpiresAt()).getSeconds());

            Integer distanceMeters = offer.getDistanceMeters();
            if (distanceMeters == null || distanceMeters == 0) {
                Double courierLat = lat;
                Double courierLon = lon;
                if (courierLat == null || courierLon == null) {
                    ParsedLocation loc = parseCourierLocation(courierId);
                    if (loc != null) {
                        courierLat = loc.lat();
                        courierLon = loc.lon();
                    }
                }

                if (courierLat != null && courierLon != null && order.getLatitude() != null && order.getLongitude() != null) {
                    distanceMeters = (int) Math.round(GeoUtils.calculateDistanceMeters(
                            courierLat, courierLon, order.getLatitude().doubleValue(), order.getLongitude().doubleValue()));
                }
            }

            OrderBriefDto orderBrief = OrderBriefDto.builder()
                    .id(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .status(order.getStatus().name())
                    .farmId(order.getFarmId())
                    .farmName(farm != null ? farm.getName() : "GidroGo")
                    .farmLatitude(farm != null && farm.getLatitude() != null ? farm.getLatitude().doubleValue() : null)
                    .farmLongitude(farm != null && farm.getLongitude() != null ? farm.getLongitude().doubleValue() : null)
                    .deliveryAddress(order.getDeliveryAddress())
                    .latitude(order.getLatitude() != null ? order.getLatitude().doubleValue() : null)
                    .longitude(order.getLongitude() != null ? order.getLongitude().doubleValue() : null)
                    .totalSum(order.getTotalSum())
                    .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "CASH")
                    .itemsCount(orderItemRepository.findAllByOrderId(order.getId()).size())
                    .deliverySlot(order.getDeliverySlot())
                    .createdAt(order.getCreatedAt())
                    .build();

            responses.add(CourierOfferResponse.builder()
                    .offerId(offer.getId())
                    .orderId(offer.getOrderId())
                    .status(offer.getStatus())
                    .expiresAt(offer.getExpiresAt())
                    .ttlSeconds(ttlSeconds)
                    .distanceMeters(distanceMeters)
                    .distanceBasis(offer.getDistanceBasis() != null ? offer.getDistanceBasis() : "CURRENT_LOCATION")
                    .anchorOrderId(offer.getAnchorOrderId())
                    .etaSeconds(offer.getEtaSeconds())
                    .order(orderBrief)
                    .build());
        }

        return responses;
    }

    /**
     * Atomik qabul qilish (2.3 POST /api/courier/orders/{id}/accept — 409 Race Guard, Capacity, Stock Lock)
     */
    @Transactional
    public OrderResponse acceptOffer(Long orderId, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        // 403 Forbidden tekshiruvi: faqat shu kuryerga berilgan taklif qabul qilinishi mumkin
        CourierOffer offer = courierOfferRepository.findByOrderIdAndCourierId(orderId, courierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Ushbu buyurtma sizga taklif qilinmagan"));

        if (!"PENDING".equalsIgnoreCase(offer.getStatus())) {
            if ("EXPIRED".equalsIgnoreCase(offer.getStatus())) {
                throw new ResponseStatusException(HttpStatus.GONE, "OFFER_EXPIRED: Taklif muddati tugadi");
            }
            if ("ACCEPTED".equalsIgnoreCase(offer.getStatus()) || "CANCELLED".equalsIgnoreCase(offer.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyurtma allaqachon qabul qilingan");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ushbu taklif faol emas: " + offer.getStatus());
        }

        // 3-talab: Taklif TTL 30s + 5s grace period
        Instant graceExpiry = offer.getExpiresAt().plusSeconds(ACCEPT_GRACE_SEC);
        if (Instant.now().isAfter(graceExpiry)) {
            offer.setStatus("EXPIRED");
            courierOfferRepository.save(offer);
            throw new ResponseStatusException(HttpStatus.GONE, "OFFER_EXPIRED: Taklif muddati tugadi");
        }

        // 1-talab: SELECT FOR UPDATE orqali dastavkachi zaxirasini qulflash
        vehicleStockRepository.findAllWithLockByCourierId(courierId);

        // 2-talab: N = 2 va max_active_orders tekshiruvi
        FarmDispatchSettings settings = farmDispatchSettingsRepository.findById(order.getFarmId()).orElse(null);
        int maxActiveOrders = settings != null && settings.getMaxActiveOrders() != null ? settings.getMaxActiveOrders() : 2;

        List<Order> activeOrders = orderRepository.findAllByCourierIdAndStatusIn(
                courierId,
                List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY, OrderStatus.PROBLEM)
        );
        if (activeOrders.size() >= maxActiveOrders) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COURIER_AT_CAPACITY: Dastavkachida maksimal buyurtmalar soniga (" + maxActiveOrders + ") yetgan");
        }

        // 1-talab: Erkin zaxirani buyurtmadagi har bir mahsulot bo'yicha tekshirish
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);
        for (OrderItem oi : orderItems) {
            BigDecimal freeStock = calculateFreeStock(courierId, oi.getProductId(), activeOrders);
            if (freeStock.compareTo(oi.getQuantity()) < 0) {
                log.warn("Kuryer {} da mahsulot {} uchun yetarli erkin zaxira yo'q (erkin: {}, kerak: {})",
                        courierId, oi.getProductId(), freeStock, oi.getQuantity());
                offer.setStatus("CANCELLED");
                courierOfferRepository.save(offer);
                dispatchOrderToNextCandidate(order);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK: Mashinada erkin zaxira yetarli emas");
            }
        }

        // DB darajasidagi atomik yangilash (Race guard)
        Instant now = Instant.now();
        int updatedRows = orderRepository.atomicAssignCourier(orderId, courierId, now);

        if (updatedRows == 0) {
            log.warn("Buyurtma {} kuryer {} tomonidan olinmadi (allaqachon biriktirilgan), 409 berildi", orderId, courierId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyurtma allaqachon boshqa kuryer tomonidan qabul qilingan yoki biriktirilgan");
        }

        // Muvaffaqiyatli qabul qilindi (200 OK)
        offer.setStatus("ACCEPTED");
        offer.setRespondedAt(now);
        courierOfferRepository.save(offer);

        // Qolgan kuryerlarning PENDING takliflarini CANCELLED qilish va ularga WS OFFER_CANCELLED yuborish
        List<CourierOffer> otherOffers = courierOfferRepository.findAllByOrderIdAndStatus(orderId, "PENDING");
        for (CourierOffer rival : otherOffers) {
            if (!rival.getCourierId().equals(courierId)) {
                rival.setStatus("CANCELLED");
                courierOfferRepository.save(rival);

                Map<String, Object> cancelEvent = Map.of(
                        "type", "OFFER_CANCELLED",
                        "data", Map.of(
                                "offerId", rival.getId(),
                                "orderId", orderId,
                                "reason", "ACCEPTED_BY_OTHER"
                        ),
                        "timestamp", Instant.now().toString()
                );
                courierWebSocketHandler.sendToCourier(rival.getCourierId(), cancelEvent);
                try {
                    notificationService.createNotification(
                            rival.getCourierId(),
                            "Taklif bekor qilindi",
                            "Buyurtma #" + order.getOrderNumber() + " boshqa kuryer tomonidan qabul qilindi",
                            "OFFER_CANCELLED",
                            orderId
                    );
                } catch (Exception ignored) {}
            }
        }

        // Delivery sequence va anchor o'rnatish
        if (activeOrders.isEmpty()) {
            order.setDeliverySequence(1);
            order.setAnchorOrderId(null);
        } else {
            Order anchor = activeOrders.get(0);
            order.setDeliverySequence(2);
            order.setAnchorOrderId(anchor.getId());
        }

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(order.getStatus())
                .toStatus(OrderStatus.ASSIGNED)
                .changedBy(courierId)
                .build());

        log.info("Kuryer {} buyurtmani {} atomik qabul qildi (seq={}, anchor={})",
                courierId, orderId, order.getDeliverySequence(), order.getAnchorOrderId());

        order.setCourierId(courierId);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setAssignedAt(now);
        order = orderRepository.save(order);

        OrderResponse resp = getOrderService().mapToResponse(order, true);
        eventPublisher.publishOrderStatusChanged(order.getFarmId(), courierId, order.getClientId(), resp);
        return resp;
    }

    /**
     * Rad etish (2.4 POST /api/courier/orders/{id}/reject — qayta navbatga o'tkazish)
     */
    @Transactional
    public OrderResponse rejectOffer(Long orderId, Long courierId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        courierOfferRepository.findByOrderIdAndCourierId(orderId, courierId).ifPresent(offer -> {
            offer.setStatus("REJECTED");
            offer.setRejectReason(reason);
            offer.setRespondedAt(Instant.now());
            courierOfferRepository.save(offer);
        });

        if (courierId.equals(order.getCourierId())) {
            order.setStatus(OrderStatus.SEARCHING);
            order.setCourierId(null);
            order.setAssignedAt(null);
            order = orderRepository.save(order);

            statusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .fromStatus(OrderStatus.ASSIGNED)
                    .toStatus(OrderStatus.SEARCHING)
                    .changedBy(courierId)
                    .build());
        }

        problemLogRepository.save(OrderProblemLog.builder()
                .orderId(order.getId())
                .reasonCode("COURIER_REJECTED")
                .reasonText(reason)
                .reportedBy(courierId)
                .build());

        log.info("Kuryer {} buyurtmani {} rad etdi, sabab: {}", courierId, orderId, reason);

        // Keyingi nomzodga o'tkazish
        dispatchOrderToNextCandidate(order);

        OrderResponse resp = getOrderService().mapToResponse(order, true);
        eventPublisher.publishOrderStatusChanged(order.getFarmId(), courierId, order.getClientId(), resp);
        return resp;
    }

    /**
     * Yangi buyurtmani eng yaqin / mos kuryerga avtomatik taklif qilish (ETA, N=2, R-radius, Erkin zaxira)
     */
    @Transactional
    public void dispatchOrderToNextCandidate(Order order) {
        if (order == null || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.SEARCHING && order.getStatus() != OrderStatus.PREPARING)) {
            return;
        }

        Farm farm = farmRepository.findById(order.getFarmId()).orElse(null);
        // BE-14: bloklangan yoki nofaol fermaga yangi taklif berilmaydi
        if (farm == null || farm.getStatus() != uz.gidrogo.modules.farm.FarmStatus.ACTIVE) return;

        List<CourierOffer> activeOffers = courierOfferRepository.findAllByOrderIdAndStatus(order.getId(), "PENDING");
        if (!activeOffers.isEmpty()) {
            return; // Allaqachon faol taklif bor
        }

        Instant cooldownThreshold = Instant.now().minus(Duration.ofMinutes(REOFFER_COOLDOWN_MIN));
        Set<Long> cooldownCouriers = new HashSet<>();
        courierOfferRepository.findAllByOrderId(order.getId()).stream()
                .filter(o -> ("REJECTED".equalsIgnoreCase(o.getStatus()) || "EXPIRED".equalsIgnoreCase(o.getStatus()))
                        && o.getCreatedAt().isAfter(cooldownThreshold))
                .forEach(o -> cooldownCouriers.add(o.getCourierId()));

        FarmDispatchSettings settings = farmDispatchSettingsRepository.findById(order.getFarmId()).orElse(null);
        int maxActiveOrders = settings != null && settings.getMaxActiveOrders() != null ? settings.getMaxActiveOrders() : 2;
        int secondOrderRadiusM = settings != null && settings.getSecondOrderRadiusM() != null ? settings.getSecondOrderRadiusM() : 1500;
        int serviceTimeMin = settings != null && settings.getServiceTimeMin() != null ? settings.getServiceTimeMin() : 5;

        List<User> farmCouriers = userRepository.findAllByFarmIdAndRole(order.getFarmId(), Role.COURIER);
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());

        List<CandidateEta> candidateList = new ArrayList<>();

        boolean anyOnline = false;
        boolean anyHasStock = false;

        for (User courier : farmCouriers) {
            if (!isCourierOnline(courier.getId())) continue;
            anyOnline = true;

            if (cooldownCouriers.contains(courier.getId())) continue;

            // Bitta dastavkachiga bir vaqtda faqat bitta PENDING taklif
            if (!courierOfferRepository.findAllByCourierIdAndStatus(courier.getId(), "PENDING").isEmpty()) {
                continue;
            }

            // Faol buyurtmalar soni < N
            List<Order> activeOrders = orderRepository.findAllByCourierIdAndStatusIn(
                    courier.getId(),
                    List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY, OrderStatus.PROBLEM)
            );
            if (activeOrders.size() >= maxActiveOrders) {
                continue;
            }

            // Erkin zaxira tekshiruvi
            boolean hasStock = true;
            for (OrderItem oi : orderItems) {
                BigDecimal freeStock = calculateFreeStock(courier.getId(), oi.getProductId(), activeOrders);
                if (freeStock.compareTo(oi.getQuantity()) < 0) {
                    hasStock = false;
                    break;
                }
            }
            if (!hasStock) continue;
            anyHasStock = true;

            ParsedLocation loc = parseCourierLocation(courier.getId());
            double courierLat = loc != null ? loc.lat() : (farm.getLatitude() != null ? farm.getLatitude().doubleValue() : 39.774);
            double courierLon = loc != null ? loc.lon() : (farm.getLongitude() != null ? farm.getLongitude().doubleValue() : 64.428);

            double targetLat = order.getLatitude() != null ? order.getLatitude().doubleValue() : (farm.getLatitude() != null ? farm.getLatitude().doubleValue() : 39.774);
            double targetLon = order.getLongitude() != null ? order.getLongitude().doubleValue() : (farm.getLongitude() != null ? farm.getLongitude().doubleValue() : 64.428);

            if (activeOrders.isEmpty()) {
                // Bo'sh dastavkachi: ETA = t(joriy joy -> mijoz)
                double dist = GeoUtils.calculateDistanceMeters(courierLat, courierLon, targetLat, targetLon);
                double eta = travelTimeSec(courierLat, courierLon, targetLat, targetLon);
                candidateList.add(new CandidateEta(courier, eta, (int) Math.round(dist), "CURRENT_LOCATION", null, true));
            } else {
                // Band dastavkachi (activeOrders.size() == 1): yangi mijoz yakor manzilidan R ichida bo'lishi shart
                Order anchor = activeOrders.get(0);
                if (anchor.getLatitude() == null || anchor.getLongitude() == null) continue;

                double anchorLat = anchor.getLatitude().doubleValue();
                double anchorLon = anchor.getLongitude().doubleValue();

                double distAnchorToNew = GeoUtils.calculateDistanceMeters(anchorLat, anchorLon, targetLat, targetLon);
                if (distAnchorToNew > secondOrderRadiusM) {
                    continue; // R radiusidan tashqarida!
                }

                double t1 = travelTimeSec(courierLat, courierLon, anchorLat, anchorLon);
                double t2 = travelTimeSec(anchorLat, anchorLon, targetLat, targetLon);
                double eta = t1 + (serviceTimeMin * 60.0) + t2;

                candidateList.add(new CandidateEta(courier, eta, (int) Math.round(distAnchorToNew), "ANCHOR_ORDER", anchor.getId(), false));
            }
        }

        if (!candidateList.isEmpty()) {
            // ETA bo'yicha saralash
            candidateList.sort(Comparator.comparingDouble(CandidateEta::etaSec));

            CandidateEta bestCandidate = candidateList.get(0);

            // Tie-break: agar g'olib band bo'lsa, lekin bo'sh dastavkachining ETA'si 60 soniya ichida bo'lsa, bo'sh dastavkachi oldinga o'tadi
            if (!bestCandidate.isFree()) {
                final double bestEta = bestCandidate.etaSec();
                Optional<CandidateEta> freeOpt = candidateList.stream()
                        .filter(CandidateEta::isFree)
                        .filter(c -> c.etaSec() - bestEta <= ETA_TIE_SEC)
                        .findFirst();
                if (freeOpt.isPresent()) {
                    bestCandidate = freeOpt.get();
                }
            }

            int ttl = OFFER_TTL_SEC; // 30 s
            Instant expiresAt = Instant.now().plusSeconds(ttl);

            CourierOffer offer = CourierOffer.builder()
                    .orderId(order.getId())
                    .courierId(bestCandidate.courier().getId())
                    .status("PENDING")
                    .distanceMeters(bestCandidate.distanceMeters())
                    .distanceBasis(bestCandidate.distanceBasis())
                    .anchorOrderId(bestCandidate.anchorOrderId())
                    .etaSeconds((int) Math.round(bestCandidate.etaSec()))
                    .expiresAt(expiresAt)
                    .build();
            offer = courierOfferRepository.save(offer);

            log.info("Buyurtma {} kuryerga ({}, ETA: {}s, basis: {}) taklif qilindi, offerId={}",
                    order.getId(), bestCandidate.courier().getFullName(), Math.round(bestCandidate.etaSec()), bestCandidate.distanceBasis(), offer.getId());

            // Agar buyurtma PREPARING holatida bo'lsa, uni SEARCHING ga o'tkazamiz
            if (order.getStatus() == OrderStatus.PREPARING) {
                order.setStatus(OrderStatus.SEARCHING);
                orderRepository.save(order);
            }

            OrderBriefDto brief = OrderBriefDto.builder()
                    .id(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .status(order.getStatus().name())
                    .farmId(order.getFarmId())
                    .farmName(farm.getName())
                    .farmLatitude(farm.getLatitude() != null ? farm.getLatitude().doubleValue() : null)
                    .farmLongitude(farm.getLongitude() != null ? farm.getLongitude().doubleValue() : null)
                    .deliveryAddress(order.getDeliveryAddress())
                    .latitude(order.getLatitude() != null ? order.getLatitude().doubleValue() : null)
                    .longitude(order.getLongitude() != null ? order.getLongitude().doubleValue() : null)
                    .totalSum(order.getTotalSum())
                    .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "CASH")
                    .itemsCount(orderItems.size())
                    .deliverySlot(order.getDeliverySlot())
                    .createdAt(order.getCreatedAt())
                    .build();

            CourierOfferResponse offerResp = CourierOfferResponse.builder()
                    .offerId(offer.getId())
                    .orderId(order.getId())
                    .status("PENDING")
                    .expiresAt(expiresAt)
                    .ttlSeconds((long) ttl)
                    .distanceMeters(bestCandidate.distanceMeters())
                    .distanceBasis(bestCandidate.distanceBasis())
                    .anchorOrderId(bestCandidate.anchorOrderId())
                    .etaSeconds((int) Math.round(bestCandidate.etaSec()))
                    .order(brief)
                    .build();

            Map<String, Object> newOfferEvent = Map.of(
                    "type", "NEW_OFFER",
                    "data", offerResp,
                    "timestamp", Instant.now().toString()
            );

            courierWebSocketHandler.sendToCourier(bestCandidate.courier().getId(), newOfferEvent);

            try {
                notificationService.createNotification(
                        bestCandidate.courier().getId(),
                        "Yangi buyurtma taklifi",
                        "Buyurtma #" + order.getOrderNumber() + " sizga taklif qilindi (ETA: " + Math.round(bestCandidate.etaSec() / 60.0) + " daq)",
                        "NEW_OFFER",
                        order.getId()
                );
            } catch (Exception ignored) {}

        } else {
            // Nomzod topilmadi => 4-talab: PREPARING holatiga o'tish
            String reason;
            if (!anyOnline) {
                reason = "NO_FREE_COURIER";
            } else if (!anyHasStock) {
                reason = "NO_STOCK";
            } else if (!cooldownCouriers.isEmpty()) {
                reason = "ALL_DECLINED";
            } else {
                reason = "NO_FREE_COURIER";
            }

            OrderStatus prev = order.getStatus();
            if (order.getStatus() != OrderStatus.PREPARING) {
                order.setStatus(OrderStatus.PREPARING);
                order.setPreparingSince(Instant.now());
            }
            order.setPreparingReason(reason);
            order.setDispatchAttempts(order.getDispatchAttempts() != null ? order.getDispatchAttempts() + 1 : 1);
            order = orderRepository.save(order);

            statusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .fromStatus(prev)
                    .toStatus(OrderStatus.PREPARING)
                    .build());

            log.info("Buyurtma {} PREPARING ga o'tdi, sabab: {}, urinishlar: {}", order.getId(), reason, order.getDispatchAttempts());
        }
    }

    /**
     * Menejer tomonidan "Qayta qidirish" bosilganda (POST /api/manager/orders/{id}/retry-dispatch)
     */
    @Transactional
    public OrderResponse retryDispatch(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        if (order.getStatus() != OrderStatus.PREPARING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Faqat PREPARING holatidagi buyurtmani qayta qidirish mumkin");
        }

        order.setStatus(OrderStatus.SEARCHING);
        orderRepository.save(order);

        dispatchOrderToNextCandidate(order);
        return getOrderService().mapToResponse(order, false);
    }

    /**
     * Takliflar muddati o'tishini har 3 soniyada tekshirib keyingi kuryerga o'tkazish
     */
    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void cleanupExpiredOffers() {
        Instant now = Instant.now();
        List<CourierOffer> pendingList = courierOfferRepository.findAllByStatus("PENDING");

        for (CourierOffer offer : pendingList) {
            // 30s + 5s grace muddati o'tdimi?
            if (offer.getExpiresAt().plusSeconds(ACCEPT_GRACE_SEC).isBefore(now)) {
                offer.setStatus("EXPIRED");
                courierOfferRepository.save(offer);

                Map<String, Object> cancelEvent = Map.of(
                        "type", "OFFER_CANCELLED",
                        "data", Map.of(
                                "offerId", offer.getId(),
                                "orderId", offer.getOrderId(),
                                "reason", "EXPIRED"
                        ),
                        "timestamp", now.toString()
                );
                courierWebSocketHandler.sendToCourier(offer.getCourierId(), cancelEvent);

                log.info("Taklif {} muddati tugadi (EXPIRED), buyurtma {} keyingi nomzodga o'tkazilmoqda",
                        offer.getId(), offer.getOrderId());

                orderRepository.findById(offer.getOrderId()).ifPresent(this::dispatchOrderToNextCandidate);
            }
        }
    }

    /**
     * PREPARING va SEARCHING buyurtmalarni har 60 soniyada qayta taqsimlash (4-talab)
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processPreparingOrders() {
        List<Order> unassigned = orderRepository.findAllByStatus(OrderStatus.PREPARING);
        unassigned.sort(Comparator.comparing(o -> o.getPreparingSince() != null ? o.getPreparingSince() : o.getCreatedAt()));

        for (Order order : unassigned) {
            List<CourierOffer> offers = courierOfferRepository.findAllByOrderIdAndStatus(order.getId(), "PENDING");
            if (offers.isEmpty()) {
                dispatchOrderToNextCandidate(order);
            }
        }
    }

    public void onCourierRestocked(Long courierId, Long farmId) {
        log.info("Kuryer {} restock qildi, farm {} PREPARING buyurtmalar qayta tekshiriladi", courierId, farmId);
        List<Order> unassigned = orderRepository.findAllByFarmIdAndStatus(farmId, OrderStatus.PREPARING);
        unassigned.sort(Comparator.comparing(o -> o.getPreparingSince() != null ? o.getPreparingSince() : o.getCreatedAt()));
        for (Order o : unassigned) {
            dispatchOrderToNextCandidate(o);
        }
    }

    public void onCourierOnline(Long courierId, Long farmId) {
        log.info("Kuryer {} online bo'ldi, farm {} PREPARING buyurtmalar qayta tekshiriladi", courierId, farmId);
        List<Order> unassigned = orderRepository.findAllByFarmIdAndStatus(farmId, OrderStatus.PREPARING);
        for (Order o : unassigned) {
            dispatchOrderToNextCandidate(o);
        }
    }

    public void onOrderDeliveredOrCompleted(Order order, Long courierId) {
        if (order == null) return;
        // Farmning PREPARING buyurtmalarini tekshirish
        List<Order> unassigned = orderRepository.findAllByFarmIdAndStatus(order.getFarmId(), OrderStatus.PREPARING);
        for (Order o : unassigned) {
            dispatchOrderToNextCandidate(o);
        }
    }

    public void onOrderCancelled(Order order) {
        onOrderCancelledOrReassigned(order);
    }

    public void onOrderCancelledOrReassigned(Order cancelledOrder) {
        if (cancelledOrder == null || cancelledOrder.getCourierId() == null) return;

        // Agar bu buyurtma 1-buyurtma (yakor) bo'lsa, 2-buyurtmani 1-o'ringa surish
        List<Order> secondOrders = orderRepository.findAllByCourierIdAndStatusIn(
                cancelledOrder.getCourierId(),
                List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY)
        );

        for (Order sec : secondOrders) {
            if (cancelledOrder.getId().equals(sec.getAnchorOrderId())) {
                sec.setDeliverySequence(1);
                sec.setAnchorOrderId(null);
                orderRepository.save(sec);

                Map<String, Object> event = Map.of(
                        "type", "ORDER_SEQUENCE_CHANGED",
                        "data", Map.of(
                                "orderId", sec.getId(),
                                "deliverySequence", 1
                        ),
                        "timestamp", Instant.now().toString()
                );
                courierWebSocketHandler.sendToCourier(sec.getCourierId(), event);
                log.info("Buyurtma {} 1-ketma-ketlikka o'tdi (yakor bekor qilindi)", sec.getId());
            }
        }
    }

    public BigDecimal calculateFreeStock(Long courierId, Long productId, List<Order> activeOrders) {
        BigDecimal totalStock = vehicleStockRepository.findByCourierIdAndProductId(courierId, productId)
                .map(VehicleStock::getQuantity)
                .orElse(BigDecimal.ZERO);

        BigDecimal reserved = BigDecimal.ZERO;
        for (Order o : activeOrders) {
            List<OrderItem> items = orderItemRepository.findAllByOrderId(o.getId());
            for (OrderItem oi : items) {
                if (oi.getProductId().equals(productId) && oi.getQuantity() != null) {
                    reserved = reserved.add(oi.getQuantity());
                }
            }
        }
        return totalStock.subtract(reserved).max(BigDecimal.ZERO);
    }

    private double travelTimeSec(double lat1, double lon1, double lat2, double lon2) {
        double dist = GeoUtils.calculateDistanceMeters(lat1, lon1, lat2, lon2);
        double speedMps = (AVG_SPEED_KMH * 1000.0) / 3600.0;
        return (dist * ROAD_FACTOR) / speedMps;
    }

    private boolean isCourierOnline(Long courierId) {
        if (courierId == null) return false;
        try {
            String val = redisTemplate.opsForValue().get("courier:online:" + courierId);
            return "true".equalsIgnoreCase(val);
        } catch (Exception e) {
            return false;
        }
    }

    private ParsedLocation parseCourierLocation(Long courierId) {
        try {
            String val = redisTemplate.opsForValue().get("courier:loc:" + courierId);
            if (val != null && !val.isBlank()) {
                String[] parts = val.split(",");
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);
                Instant time = parts.length > 4 ? Instant.ofEpochMilli(Long.parseLong(parts[4])) : Instant.now();
                return new ParsedLocation(lat, lon, time);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private record ParsedLocation(double lat, double lon, Instant timestamp) {}
    private record CandidateEta(User courier, double etaSec, int distanceMeters, String distanceBasis, Long anchorOrderId, boolean isFree) {}
}
