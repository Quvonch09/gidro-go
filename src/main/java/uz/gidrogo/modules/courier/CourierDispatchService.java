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
    private final OrderService orderService;
    private final CourierWebSocketHandler courierWebSocketHandler;
    private final WebSocketEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

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

        // Taklif muddati tekshiruvi (40 s TTL + 5 s grace)
        Optional<CourierOffer> courierOfferOpt = courierOfferRepository.findByOrderIdAndCourierId(orderId, courierId);
        if (courierOfferOpt.isPresent()) {
            CourierOffer offer = courierOfferOpt.get();
            if (offer.getExpiresAt().plusSeconds(5).isBefore(Instant.now())) {
                offer.setStatus("EXPIRED");
                courierOfferRepository.save(offer);
                throw new ResponseStatusException(HttpStatus.GONE, "Taklif muddati tugadi");
            }
        }

        // DB darajasidagi atomik yangilash (Race guard)
        int updatedRows = orderRepository.atomicAssignCourier(orderId, courierId, Instant.now());

        if (updatedRows == 0) {
            // Yangilanmadi => buyurtma holatini tekshirib 409 qaytaramiz
            Order refreshed = orderRepository.findById(orderId).orElse(order);
            if (refreshed.getCourierId() != null && !refreshed.getCourierId().equals(courierId)) {
                log.warn("Buyurtma {} kuryer {} tomonidan olingan, kuryer {} ga 409 berildi",
                        orderId, refreshed.getCourierId(), courierId);
                throw new ConflictException("Buyurtma boshqa kuryer tomonidan qabul qilingan");
            }
            if (refreshed.getStatus() != OrderStatus.NEW && refreshed.getStatus() != OrderStatus.SEARCHING && refreshed.getStatus() != OrderStatus.ASSIGNED) {
                throw new ConflictException("Buyurtma allaqachon boshqa holatda: " + refreshed.getStatus());
            }
            throw new ConflictException("Buyurtma allaqachon biriktirilgan");
        }

        // Muvaffaqiyatli qabul qilindi (200 OK)
        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();

        // 1. Shu kuryer taklifini ACCEPTED ga o'tkazish
        courierOfferOpt.ifPresent(offer -> {
            offer.setStatus("ACCEPTED");
            offer.setRespondedAt(Instant.now());
            courierOfferRepository.save(offer);
        });

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

        OrderResponse resp = orderService.mapToResponse(updatedOrder, true);
        eventPublisher.publishOrderStatusChanged(updatedOrder.getFarmId(), courierId, updatedOrder.getClientId(), resp);
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

        OrderResponse resp = orderService.mapToResponse(order, true);
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

        // Ushbu buyurtmani allaqachon rad etgan yoki taklifi bor kuryerlar ID si
        Set<Long> excludedCouriers = new HashSet<>();
        List<CourierOffer> existingOffers = courierOfferRepository.findAllByOrderIdAndStatus(order.getId(), "PENDING");
        if (!existingOffers.isEmpty()) {
            return; // Allaqachon faol taklif bor
        }

        courierOfferRepository.findAll().stream()
                .filter(o -> o.getOrderId().equals(order.getId()) && ("REJECTED".equals(o.getStatus()) || "EXPIRED".equals(o.getStatus())))
                .forEach(o -> excludedCouriers.add(o.getCourierId()));

        // Nomzodlarni topish: COURIER, ACTIVE, ONLINE, oxirgi 120s GPS, aktiv buyurtmasi yo'q
        List<User> farmCouriers = userRepository.findAllByFarmIdAndRole(order.getFarmId(), Role.COURIER);

        List<CandidateDistance> candidates = new ArrayList<>();
        for (User courier : farmCouriers) {
            if (excludedCouriers.contains(courier.getId())) continue;
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

            candidates.add(new CandidateDistance(courier, dist));
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

            // STOMP ga ham dublyaj
            eventPublisher.publishOrderAssigned(order.getFarmId(), bestCandidate.getId(), orderService.mapToResponse(order, false));
        } else {
            log.info("Buyurtma {} uchun bo'sh kuryer topilmadi, status SEARCHING da qoladi (Manager navbati)", order.getId());
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
