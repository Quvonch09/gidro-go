package uz.gidrogo.modules.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.order.Order;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.dto.OrderTrackingDtos.*;
import uz.gidrogo.modules.rating.RatingRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientOrderTrackingService {

    private final OrderRepository orderRepository;
    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final RatingRepository ratingRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(2500))
            .setReadTimeout(Duration.ofMillis(2500))
            .build();

    @Transactional(readOnly = true)
    public ClientOrderTrackingResponse getTracking(Long orderId, Long currentUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        validateOwnership(order, currentUserId);

        Farm farm = farmRepository.findById(order.getFarmId()).orElse(null);
        FarmBriefDto farmDto = null;
        if (farm != null) {
            farmDto = FarmBriefDto.builder()
                    .id(farm.getId())
                    .name(farm.getName())
                    .latitude(farm.getLatitude() != null ? farm.getLatitude().doubleValue() : null)
                    .longitude(farm.getLongitude() != null ? farm.getLongitude().doubleValue() : null)
                    .phone(farm.getPhone())
                    .build();
        }

        CourierLiveDto courierDto = null;
        if (order.getCourierId() != null) {
            User courier = userRepository.findById(order.getCourierId()).orElse(null);
            if (courier != null) {
                courierDto = buildCourierLiveDto(courier, farm, order);
            }
        }

        // Metrics calculation (Distance & ETA)
        Double originLat = courierDto != null && courierDto.getCurrentLatitude() != null
                ? courierDto.getCurrentLatitude()
                : (farmDto != null ? farmDto.getLatitude() : null);
        Double originLon = courierDto != null && courierDto.getCurrentLongitude() != null
                ? courierDto.getCurrentLongitude()
                : (farmDto != null ? farmDto.getLongitude() : null);

        Double destLat = order.getLatitude() != null ? order.getLatitude().doubleValue() : null;
        Double destLon = order.getLongitude() != null ? order.getLongitude().doubleValue() : null;

        TrackingMetricsDto metrics = null;
        if (originLat != null && originLon != null && destLat != null && destLon != null) {
            double distMeters = GeoUtils.calculateDistanceMeters(originLat, originLon, destLat, destLon);
            double distKm = Math.round((distMeters / 1000.0) * 100.0) / 100.0;
            float speedKmh = courierDto != null && courierDto.getSpeedKmh() != null && courierDto.getSpeedKmh() > 5.0f
                    ? courierDto.getSpeedKmh()
                    : 25.0f; // shahar o'rtacha tezligi 25 km/soat
            int etaMinutes = (int) Math.max(1, Math.round((distKm / speedKmh) * 60.0));
            boolean isNearby = distMeters <= 500.0 || order.getStatus() == OrderStatus.NEARBY;

            metrics = TrackingMetricsDto.builder()
                    .distanceMeters(Math.round(distMeters * 10.0) / 10.0)
                    .distanceKm(distKm)
                    .etaMinutes(etaMinutes)
                    .isNearby(isNearby)
                    .build();
        }

        return ClientOrderTrackingResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(destLat)
                .deliveryLongitude(destLon)
                .farm(farmDto)
                .courier(courierDto)
                .metrics(metrics)
                .build();
    }

    @Transactional(readOnly = true)
    public OrderRouteResponse getRoute(Long orderId, Long currentUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi: " + orderId));

        validateOwnership(order, currentUserId);

        Farm farm = farmRepository.findById(order.getFarmId()).orElse(null);

        // Origin koordinatasini aniqlash: Agar kuryer bo'lsa kuryer koordinatasi, aks holda ferma koordinatasi
        Double originLat = null;
        Double originLon = null;

        if (order.getCourierId() != null) {
            try {
                String locStr = redisTemplate.opsForValue().get("courier:loc:" + order.getCourierId());
                if (locStr != null && !locStr.isBlank()) {
                    String[] parts = locStr.split(",");
                    if (parts.length >= 2) {
                        originLat = Double.parseDouble(parts[0]);
                        originLon = Double.parseDouble(parts[1]);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (originLat == null || originLon == null) {
            if (farm != null && farm.getLatitude() != null && farm.getLongitude() != null) {
                originLat = farm.getLatitude().doubleValue();
                originLon = farm.getLongitude().doubleValue();
            }
        }

        Double destLat = order.getLatitude() != null ? order.getLatitude().doubleValue() : null;
        Double destLon = order.getLongitude() != null ? order.getLongitude().doubleValue() : null;

        if (originLat == null || originLon == null || destLat == null || destLon == null) {
            return OrderRouteResponse.builder()
                    .orderId(order.getId())
                    .totalDistanceMeters(0.0)
                    .totalDurationSeconds(0)
                    .summary("Koordinatalar yetarli emas")
                    .encodedPolyline("")
                    .waypoints(List.of())
                    .build();
        }

        // Kesh tekshirish (30 soniya)
        String cacheKey = String.format(Locale.US, "route:%d:%.3f:%.3f", orderId, originLat, originLon);
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                return objectMapper.readValue(cachedJson, OrderRouteResponse.class);
            }
        } catch (Exception ignored) {}

        // OSRM orqali haqiqiy ko'chalar bo'yicha marshrut olish
        OrderRouteResponse routeResponse = fetchOsrmRoute(orderId, originLat, originLon, destLat, destLon);

        // Agar OSRM javob bermasa yoki xato bo'lsa, zaxira sifatida to'g'ri chiziqli interpolatsiya marshruti tuziladi
        if (routeResponse == null || routeResponse.getWaypoints() == null || routeResponse.getWaypoints().isEmpty()) {
            routeResponse = buildFallbackRoute(orderId, originLat, originLon, destLat, destLon);
        }

        // Keshga saqlash
        try {
            String jsonToCache = objectMapper.writeValueAsString(routeResponse);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 30, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        return routeResponse;
    }

    private OrderRouteResponse fetchOsrmRoute(Long orderId, Double originLat, Double originLon, Double destLat, Double destLon) {
        try {
            String osrmUrl = String.format(Locale.US,
                    "http://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                    originLon, originLat, destLon, destLat);

            String responseBody = restTemplate.getForObject(osrmUrl, String.class);
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String code = root.path("code").asText("");
            if (!"Ok".equalsIgnoreCase(code)) {
                return null;
            }

            JsonNode firstRoute = root.path("routes").get(0);
            if (firstRoute == null) {
                return null;
            }

            double distance = firstRoute.path("distance").asDouble(0.0);
            int duration = (int) Math.round(firstRoute.path("duration").asDouble(0.0));

            String summary = "Yetkazib berish marshruti";
            JsonNode legs = firstRoute.path("legs");
            if (legs != null && legs.isArray() && legs.size() > 0) {
                String legSummary = legs.get(0).path("summary").asText("");
                if (!legSummary.isBlank()) {
                    summary = legSummary + " orqali";
                }
            }

            List<RoutePointDto> waypoints = new ArrayList<>();
            JsonNode coordsNode = firstRoute.path("geometry").path("coordinates");
            if (coordsNode.isArray()) {
                for (JsonNode coord : coordsNode) {
                    if (coord.isArray() && coord.size() >= 2) {
                        double lon = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        waypoints.add(new RoutePointDto(lat, lon));
                    }
                }
            }

            String encodedPolyline = GeoUtils.encodePolyline(waypoints);

            return OrderRouteResponse.builder()
                    .orderId(orderId)
                    .totalDistanceMeters(Math.round(distance * 10.0) / 10.0)
                    .totalDurationSeconds(duration)
                    .summary(summary)
                    .encodedPolyline(encodedPolyline)
                    .waypoints(waypoints)
                    .build();
        } catch (Exception e) {
            log.warn("OSRM marshrutini olishda xatolik yuz berdi: {}", e.getMessage());
            return null;
        }
    }

    private OrderRouteResponse buildFallbackRoute(Long orderId, Double originLat, Double originLon, Double destLat, Double destLon) {
        double distMeters = GeoUtils.calculateDistanceMeters(originLat, originLon, destLat, destLon);
        int durationSeconds = (int) Math.round(distMeters / 8.33); // 30 km/soat = 8.33 m/s

        List<RoutePointDto> waypoints = new ArrayList<>();
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double lat = originLat + (destLat - originLat) * fraction;
            double lon = originLon + (destLon - originLon) * fraction;
            waypoints.add(new RoutePointDto(Math.round(lat * 1e6) / 1e6, Math.round(lon * 1e6) / 1e6));
        }

        String encodedPolyline = GeoUtils.encodePolyline(waypoints);

        return OrderRouteResponse.builder()
                .orderId(orderId)
                .totalDistanceMeters(Math.round(distMeters * 10.0) / 10.0)
                .totalDurationSeconds(durationSeconds)
                .summary("Yetkazib berish marshruti")
                .encodedPolyline(encodedPolyline)
                .waypoints(waypoints)
                .build();
    }

    private CourierLiveDto buildCourierLiveDto(User courier, Farm farm, Order order) {
        Double currentLat = null;
        Double currentLon = null;
        Float bearing = null;
        Float speedKmh = null;
        Instant lastUpdated = null;

        try {
            String locStr = redisTemplate.opsForValue().get("courier:loc:" + courier.getId());
            if (locStr != null && !locStr.isBlank()) {
                String[] parts = locStr.split(",");
                if (parts.length >= 2) {
                    currentLat = Double.parseDouble(parts[0]);
                    currentLon = Double.parseDouble(parts[1]);
                }
                if (parts.length >= 5) {
                    bearing = Float.parseFloat(parts[2]);
                    speedKmh = Float.parseFloat(parts[3]);
                    lastUpdated = Instant.ofEpochMilli(Long.parseLong(parts[4]));
                } else if (parts.length >= 3) {
                    lastUpdated = Instant.ofEpochMilli(Long.parseLong(parts[2]));
                }
            }
        } catch (Exception ignored) {}

        if (currentLat == null || currentLon == null) {
            // Section 6.8: GPS hali yo'q bo'lsa null qaytishi shart (0.0 yoki ferma koordinatasi emas)
            currentLat = null;
            currentLon = null;
            if (lastUpdated == null) {
                lastUpdated = Instant.now();
            }
        }

        if (bearing == null) bearing = 0.0f;
        if (speedKmh == null) speedKmh = 30.0f;

        Double avgRating = null;
        try {
            avgRating = ratingRepository.getAverageStars("COURIER", courier.getId());
        } catch (Exception ignored) {}
        Double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 4.9;

        return CourierLiveDto.builder()
                .id(courier.getId())
                .fullName(courier.getFullName())
                .phone(courier.getPhone())
                .avatarUrl(courier.getAvatarUrl())
                .vehicleModel(courier.getVehicleModel() != null ? courier.getVehicleModel() : "Chevrolet Damas")
                .vehiclePlateNumber(courier.getVehiclePlateNumber() != null ? courier.getVehiclePlateNumber() : "")
                .rating(rating)
                .currentLatitude(currentLat)
                .currentLongitude(currentLon)
                .bearing(bearing)
                .speedKmh(speedKmh)
                .lastUpdatedAt(lastUpdated)
                .build();
    }

    private void validateOwnership(Order order, Long currentUserId) {
        if (currentUserId == null) {
            return;
        }
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null) {
            return;
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (user.getRole() == Role.COURIER && currentUserId.equals(order.getCourierId())) {
            return;
        }
        if ((user.getRole() == Role.MANAGER || user.getRole() == Role.BOSS) && user.getFarmId() != null && user.getFarmId().equals(order.getFarmId())) {
            return;
        }
        if (user.getRole() == Role.CLIENT) {
            clientRepository.findByUserId(currentUserId).ifPresent(c -> {
                if (!order.getClientId().equals(c.getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ushbu buyurtma sizga tegishli emas");
                }
            });
        }
    }
}
