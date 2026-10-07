package uz.gidrogo.modules.courier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.gidrogo.common.ConflictException;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.courier.CourierOfferDtos.*;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.order.*;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
import uz.gidrogo.websocket.CourierWebSocketHandler;
import uz.gidrogo.websocket.WebSocketEventPublisher;

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
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderProblemLogRepository problemLogRepository;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final CourierWebSocketHandler courierWebSocketHandler;
    private final WebSocketEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final uz.gidrogo.modules.notification.NotificationService notificationService;

    private OrderService getOrderService() {
        return applicationContext.getBean(OrderService.class);
    }

    /**
     * Kuryer uchun faol takliflar ro'yxati (2.1 GET /api/courier/orders/offers)
     */
    @Transactional(readOnly = true)
    public List<CourierOfferResponse> getOffersForCourier(Long courierId, Double lat, Double lon) {
        if (courierId == null) {
            return List.of();
        }

        // Kuryer online bo'lmasa bo'sh ro'yxat
        if (!isCourierOnline(courierId)) {
            return List.of();
        }

        Instant now = Instant.now();
        List<CourierOffer> pendingOffers = courierOfferRepository
                .findAllByCourierIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(courierId, "PENDING", now);

        List<CourierOfferResponse> responses = new ArrayList<>();
        for (CourierOffer offer : pendingOffers) {
            Order order = orderRepository.findById(offer.getOrderId()).orElse(null);
            if (order == null) continue;

            // Agar buyurtma allaqachon boshqa holatga o'tgan bo'lsa (masalan CANCELLED yoki DELIVERED)
            if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.SEARCHING && order.getStatus() != OrderStatus.ASSIGNED) {
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

                if (courierLat != null && courierLon != null && farm != null && farm.getLatitude() != null && farm.getLongitude() != null) {
                    distanceMeters = (int) Math.round(GeoUtils.calculateDistanceMeters(
                            courierLat, courierLon, farm.getLatitude().doubleValue(), farm.getLongitude().doubleValue()));
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
                    .order(orderBrief)
                    .build());
        }

        return responses;
    }

    /**
     * Atomik qabul qilish (2.3 POST /api/courier/orders/{id}/accept — 409 Race Guard)
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
                throw new ResponseStatusException(HttpStatus.GONE, "Taklif muddati tugadi");
            }
            if ("ACCEPTED".equalsIgnoreCase(offer.getStatus()) || "CANCELLED".equalsIgnoreCase(offer.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyurtma allaqachon qabul qilingan");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ushbu taklif faol emas: " + offer.getStatus());
        }

        if (offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus("EXPIRED");
            courierOfferRepository.save(offer);
            throw new ResponseStatusException(HttpStatus.GONE, "Taklif muddati tugadi");
        }

        // DB darajasidagi atomik yangilash (Race guard)
        Instant now = Instant.now();
        int updatedRows = orderRepository.atomicAssignCourier(orderId, courierId, now);

        if (updatedRows == 0) {
            // Yangilanmadi => buyurtma holatini tekshirib 409 qaytaramiz (TZ 6.3 kafolati)
            log.warn("Buyurtma {} kuryer {} tomonidan olinmadi (allaqachon biriktirilgan), 409 berildi", orderId, courierId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyurtma allaqachon boshqa kuryer tomonidan qabul qilingan yoki biriktirilgan");
        }

        // Muvaffaqiyatli qabul qilindi (200 OK)
        // 1. Shu kuryer taklifini ACCEPTED ga o'tkazish
        offer.setStatus("ACCEPTED");
        offer.setRespondedAt(now);
        courierOfferRepository.save(offer);

        // 2. Qolgan kuryerlarning PENDING takliflarini CANCELLED qilish va ularga WS OFFER_CANCELLED yuborish
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

        // 3. Status tarixi
        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(order.getStatus())
                .toStatus(OrderStatus.ASSIGNED)
                .changedBy(courierId)
                .build());

        log.info("Kuryer {} buyurtmani {} atomik qabul qildi", courierId, orderId);

        // Biriktirishdan KEYINGI snapshot: JPA keshini yangilash va to'g'ri status/courierId qaytarish
        order.setCourierId(courierId);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setAssignedAt(now);

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

        // Taklif holatini REJECTED qilish
        courierOfferRepository.findByOrderIdAndCourierId(orderId, courierId).ifPresent(offer -> {
            offer.setStatus("REJECTED");
            offer.setRejectReason(reason);
            offer.setRespondedAt(Instant.now());
            courierOfferRepository.save(offer);
        });

        // Agar buyurtma shu kuryerga biriktirilgan bo'lsa, uni SEARCHING holatiga qaytaramiz
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

        // Keyingi nomzod kuryerga taklif yaratish
        dispatchOrderToNextCandidate(order);

        OrderResponse resp = getOrderService().mapToResponse(order, true);
        eventPublisher.publishOrderStatusChanged(order.getFarmId(), courierId, order.getClientId(), resp);
        return resp;
    }

    /**
     * Yangi buyurtmani eng yaqin kuryerga avtomatik taklif qilish (2.5 Avtomatik biriktirish)
     */
    @Transactional
    public void dispatchOrderToNextCandidate(Order order) {
        if (order == null || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.SEARCHING)) {
            return;
        }

        Farm farm = farmRepository.findById(order.getFarmId()).orElse(null);
        if (farm == null) return;

        // Ushbu buyurtmani allaqachon rad etgan kuryerlar ID si
        Set<Long> rejectedCouriers = new HashSet<>();
        List<CourierOffer> existingOffers = courierOfferRepository.findAllByOrderIdAndStatus(order.getId(), "PENDING");
        if (!existingOffers.isEmpty()) {
            return; // Allaqachon faol taklif bor
        }

        courierOfferRepository.findAllByOrderId(order.getId()).stream()
                .filter(o -> "REJECTED".equalsIgnoreCase(o.getStatus()))
                .forEach(o -> rejectedCouriers.add(o.getCourierId()));

        Set<Long> expiredCouriers = new HashSet<>();
        courierOfferRepository.findAllByOrderId(order.getId()).stream()
                .filter(o -> "EXPIRED".equalsIgnoreCase(o.getStatus()))
                .forEach(o -> expiredCouriers.add(o.getCourierId()));

        // Nomzodlarni topish: COURIER, ACTIVE, ONLINE, oxirgi 120s GPS, aktiv buyurtmasi yo'q
        List<User> farmCouriers = userRepository.findAllByFarmIdAndRole(order.getFarmId(), Role.COURIER);

        List<CandidateDistance> candidates = new ArrayList<>();
        List<CandidateDistance> fallbackCandidates = new ArrayList<>();

        for (User courier : farmCouriers) {
            if (rejectedCouriers.contains(courier.getId())) continue;
            if (!isCourierOnline(courier.getId())) continue;

            // Faol buyurtmasi borligini tekshirish (maxConcurrent = 1)
            long activeCount = orderRepository.findAllByCourierIdAndStatusIn(
                    courier.getId(),
                    List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY)
            ).size();
            if (activeCount > 0) continue;

            ParsedLocation loc = parseCourierLocation(courier.getId());
            double dist = 10000.0;
            if (loc != null && farm.getLatitude() != null && farm.getLongitude() != null) {
                dist = GeoUtils.calculateDistanceMeters(loc.lat(), loc.lon(), farm.getLatitude().doubleValue(), farm.getLongitude().doubleValue());
            }

            CandidateDistance cd = new CandidateDistance(courier, dist);
            if (!expiredCouriers.contains(courier.getId())) {
                candidates.add(cd);
            } else {
                fallbackCandidates.add(cd);
            }
        }

        // Agar hali taklif olmagan nomzodlar bo'lmasa, muddati o'tgan (lekin rad etmagan) bo'sh kuryerlarga o'tamiz
        if (candidates.isEmpty() && !fallbackCandidates.isEmpty()) {
            candidates = fallbackCandidates;
        }

        // Masofa bo'yicha saralash
        candidates.sort(Comparator.comparingDouble(CandidateDistance::distanceMeters));

        if (!candidates.isEmpty()) {
            User bestCandidate = candidates.get(0).courier();
            int dist = (int) Math.round(candidates.get(0).distanceMeters());

            // Taklif yaratish (TTL 40 s)
            int ttlSeconds = 40;
            Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

            CourierOffer offer = CourierOffer.builder()
                    .orderId(order.getId())
                    .courierId(bestCandidate.getId())
                    .status("PENDING")
                    .distanceMeters(dist)
                    .expiresAt(expiresAt)
                    .build();
            offer = courierOfferRepository.save(offer);

            log.info("Buyurtma {} eng yaqin kuryerga ({}, masofa: {}m) taklif qilindi, offerId={}",
                    order.getId(), bestCandidate.getFullName(), dist, offer.getId());

            // WS orqali NEW_OFFER yuborish
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
                    .itemsCount(orderItemRepository.findAllByOrderId(order.getId()).size())
                    .deliverySlot(order.getDeliverySlot())
                    .createdAt(order.getCreatedAt())
                    .build();

            CourierOfferResponse offerResp = CourierOfferResponse.builder()
                    .offerId(offer.getId())
                    .orderId(order.getId())
                    .status("PENDING")
                    .expiresAt(expiresAt)
                    .ttlSeconds((long) ttlSeconds)
                    .distanceMeters(dist)
                    .order(brief)
                    .build();

            Map<String, Object> newOfferEvent = Map.of(
                    "type", "NEW_OFFER",
                    "data", offerResp,
                    "timestamp", Instant.now().toString()
            );

            courierWebSocketHandler.sendToCourier(bestCandidate.getId(), newOfferEvent);

            try {
                notificationService.createNotification(
                        bestCandidate.getId(),
                        "Yangi buyurtma taklifi",
                        "Buyurtma #" + order.getOrderNumber() + " sizga taklif qilindi (" + dist + "m)",
                        "NEW_OFFER",
                        order.getId()
                );
            } catch (Exception ex) {
                log.warn("Kuryerga bildirishnoma yaratishda xatolik: {}", ex.getMessage());
            }

            // STOMP ga ham dublyaj
            eventPublisher.publishOrderAssigned(order.getFarmId(), bestCandidate.getId(), getOrderService().mapToResponse(order, false));
        } else {
            log.info("Buyurtma {} uchun bo'sh kuryer topilmadi, status SEARCHING da qoladi (Manager navbati)", order.getId());
        }
    }

    /**
     * SEARCHING holatidagi, ammo aktiv taklifi bo'lmagan buyurtmalarni har 8 soniyada
     * qayta tekshirib, bo'shagan kuryerlarga zudlik bilan yo'naltirish (Fallback Queue)
     */
    @Scheduled(fixedDelay = 8000)
    @Transactional
    public void processUnassignedSearchingOrders() {
        List<Order> searchingOrders = orderRepository.findAllByStatus(OrderStatus.SEARCHING);
        for (Order order : searchingOrders) {
            List<CourierOffer> activeOffers = courierOfferRepository.findAllByOrderIdAndStatus(order.getId(), "PENDING");
            if (activeOffers.isEmpty()) {
                dispatchOrderToNextCandidate(order);
            }
        }
    }

    /**
     * Takliflar muddati o'tishini har 5 soniyada tekshirib keyingi kuryerga o'tkazish
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void cleanupExpiredOffers() {
        Instant now = Instant.now();
        List<CourierOffer> expired = courierOfferRepository.findAllByExpiresAtBeforeAndStatus(now, "PENDING");
        for (CourierOffer offer : expired) {
            offer.setStatus("EXPIRED");
            courierOfferRepository.save(offer);

            // WS OFFER_CANCELLED yuborish
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

            log.info("Taklif {} muddati o'tdi (EXPIRED), buyurtma {} keyingi nomzodga o'tkazilmoqda",
                    offer.getId(), offer.getOrderId());

            // Keyingi kuryerga yo'naltirish
            orderRepository.findById(offer.getOrderId()).ifPresent(this::dispatchOrderToNextCandidate);
        }
    }

    public void onOrderCancelled(Order order) {
        if (order == null) return;
        if (order.getCourierId() != null) {
            Map<String, Object> event = Map.of(
                    "type", "ORDER_CANCELLED",
                    "data", Map.of("orderId", order.getId()),
                    "timestamp", Instant.now().toString()
            );
            courierWebSocketHandler.sendToCourier(order.getCourierId(), event);
        }

        List<CourierOffer> offers = courierOfferRepository.findAllByOrderIdAndStatus(order.getId(), "PENDING");
        for (CourierOffer offer : offers) {
            offer.setStatus("CANCELLED");
            courierOfferRepository.save(offer);

            Map<String, Object> event = Map.of(
                    "type", "OFFER_CANCELLED",
                    "data", Map.of(
                            "offerId", offer.getId(),
                            "orderId", order.getId(),
                            "reason", "ORDER_CANCELLED"
                    ),
                    "timestamp", Instant.now().toString()
            );
            courierWebSocketHandler.sendToCourier(offer.getCourierId(), event);
            try {
                notificationService.createNotification(
                        offer.getCourierId(),
                        "Buyurtma bekor qilindi",
                        "Buyurtma #" + order.getOrderNumber() + " bekor qilindi",
                        "ORDER_CANCELLED",
                        order.getId()
                );
            } catch (Exception ignored) {}
        }
    }

    public void onOrderReassigned(Order order, Long oldCourierId, Long newCourierId) {
        if (order == null) return;
        Map<String, Object> event = Map.of(
                "type", "ORDER_REASSIGNED",
                "data", Map.of(
                        "orderId", order.getId(),
                        "courierId", newCourierId,
                        "status", "ASSIGNED"
                ),
                "timestamp", Instant.now().toString()
        );
        if (oldCourierId != null) {
            courierWebSocketHandler.sendToCourier(oldCourierId, event);
            try {
                notificationService.createNotification(
                        oldCourierId,
                        "Buyurtma boshqa kuryerga o'tkazildi",
                        "Buyurtma #" + order.getOrderNumber() + " menejer tomonidan boshqa kuryerga biriktirildi",
                        "ORDER_REASSIGNED",
                        order.getId()
                );
            } catch (Exception ignored) {}
        }
        if (newCourierId != null) {
            courierWebSocketHandler.sendToCourier(newCourierId, event);
            try {
                notificationService.createNotification(
                        newCourierId,
                        "Yangi buyurtma biriktirildi",
                        "Menejer sizga #" + order.getOrderNumber() + " buyurtmani biriktirdi",
                        "ORDER_REASSIGNED",
                        order.getId()
                );
            } catch (Exception ignored) {}
        }
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
    private record CandidateDistance(User courier, double distanceMeters) {}
}
