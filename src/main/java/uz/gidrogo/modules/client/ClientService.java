package uz.gidrogo.modules.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.ClientDtos.*;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.farm.FarmStatus;
import uz.gidrogo.modules.farm.dto.FarmDtos.FarmResponse;
import uz.gidrogo.modules.order.Order;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.rating.RatingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientAddressRepository addressRepository;
    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RatingService ratingService;

    public List<NearbyFarmResponse> getNearbyFarms(double clientLat, double clientLon) {
        List<Farm> activeFarms = farmRepository.findAllByStatus(FarmStatus.ACTIVE);
        List<NearbyFarmResponse> nearbyList = new ArrayList<>();

        for (Farm farm : activeFarms) {
            double distance = 0.0;
            if (farm.getLatitude() != null && farm.getLongitude() != null) {
                distance = GeoUtils.calculateDistanceMeters(
                        clientLat, clientLon,
                        farm.getLatitude().doubleValue(), farm.getLongitude().doubleValue()
                );
            }

            User boss = farm.getBossUserId() != null ? userRepository.findById(farm.getBossUserId()).orElse(null) : null;
            FarmResponse farmResp = FarmResponse.builder()
                    .id(farm.getId())
                    .name(farm.getName())
                    .phone(farm.getPhone())
                    .address(farm.getAddress())
                    .latitude(farm.getLatitude())
                    .longitude(farm.getLongitude())
                    .logoUrl(farm.getLogoUrl())
                    .status(farm.getStatus())
                    .bossUserId(farm.getBossUserId())
                    .bossFullName(boss != null ? boss.getFullName() : null)
                    .bossPhone(boss != null ? boss.getPhone() : null)
                    .createdAt(farm.getCreatedAt())
                    .build();

            double rating = ratingService.getAverageRating("FARM", farm.getId());

            nearbyList.add(NearbyFarmResponse.builder()
                    .farm(farmResp)
                    .distanceMeters(distance)
                    .rating(rating)
                    .build());
        }

        nearbyList.sort(Comparator.comparingDouble(NearbyFarmResponse::getDistanceMeters));
        return nearbyList;
    }

    @Transactional
    public AddressResponse addAddress(AddressCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz profili topilmadi"));

        if (request.isDefault()) {
            addressRepository.findAllByClientId(client.getId()).forEach(a -> {
                a.setDefault(false);
                addressRepository.save(a);
            });
        }

        ClientAddress address = ClientAddress.builder()
                .clientId(client.getId())
                .label(request.getLabel())
                .address(request.getAddress())
                .latitude(BigDecimal.valueOf(request.getLatitude()))
                .longitude(BigDecimal.valueOf(request.getLongitude()))
                .isDefault(request.isDefault())
                .build();

        address = addressRepository.save(address);
        return mapToAddressResponse(address);
    }

    public List<AddressResponse> getClientAddresses() {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz profili topilmadi"));

        return addressRepository.findAllByClientId(client.getId()).stream()
                .map(this::mapToAddressResponse)
                .toList();
    }

    public List<AvailableFarmResponse> getAvailableFarms() {
        return getAvailableFarms(null, null, null, null);
    }

    public List<AvailableFarmResponse> getAvailableFarms(Double lat, Double lon, String city, String district) {
        List<Farm> activeFarms = farmRepository.findAllByStatus(FarmStatus.ACTIVE);
        List<AvailableFarmResponse> list = new ArrayList<>();

        for (Farm farm : activeFarms) {
            String fullFarmText = (farm.getName() != null ? farm.getName() : "") + " " + (farm.getAddress() != null ? farm.getAddress() : "");

            // 1. Qat'iy filtrlash: agar city berilgan bo'lsa, mos kelmasa tashlab o'tish
            if (city != null && !city.isBlank()) {
                if (!matchesLocation(fullFarmText, city)) {
                    continue;
                }
            }

            // 2. Qat'iy filtrlash: agar district berilgan bo'lsa, mos kelmasa tashlab o'tish
            if (district != null && !district.isBlank()) {
                if (!matchesLocation(fullFarmText, district)) {
                    continue;
                }
            }

            Double distanceKm = null;
            Integer deliveryTimeMinutes = 35;

            if (lat != null && lon != null && farm.getLatitude() != null && farm.getLongitude() != null) {
                double distanceMeters = GeoUtils.calculateDistanceMeters(
                        lat, lon,
                        farm.getLatitude().doubleValue(), farm.getLongitude().doubleValue()
                );
                distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;
                deliveryTimeMinutes = (int) Math.min(90, Math.max(25, 20 + Math.round(distanceKm * 3.5)));
            }

            double rating = ratingService.getAverageRating("FARM", farm.getId());
            if (rating <= 0.0) {
                rating = 4.9;
            }

            list.add(AvailableFarmResponse.builder()
                    .id(farm.getId())
                    .name(farm.getName())
                    .phone(farm.getPhone())
                    .address(farm.getAddress())
                    .logoUrl(farm.getLogoUrl())
                    .rating(rating)
                    .reviewCount(420)
                    .distanceKm(distanceKm)
                    .deliveryTimeMinutes(deliveryTimeMinutes)
                    .latitude(farm.getLatitude() != null ? farm.getLatitude().doubleValue() : null)
                    .longitude(farm.getLongitude() != null ? farm.getLongitude().doubleValue() : null)
                    .isOpen(farm.getStatus() == FarmStatus.ACTIVE)
                    .build());
        }

        if (lat != null && lon != null) {
            list.sort(Comparator.comparing(f -> f.getDistanceKm() != null ? f.getDistanceKm() : Double.MAX_VALUE));
        }

        return list;
    }

    private boolean matchesLocation(String farmText, String searchLocation) {
        if (searchLocation == null || searchLocation.isBlank()) {
            return true;
        }
        if (farmText == null || farmText.isBlank()) {
            return false;
        }

        String cleanSearch = cleanLocationString(searchLocation);
        String cleanFarm = farmText.toLowerCase();

        if (cleanSearch.isEmpty()) {
            return true;
        }

        // To'g'ridan-to'g'ri o'z ichiga olgan bo'lsa
        if (cleanFarm.contains(cleanSearch)) {
            return true;
        }

        // Toshkent / Tashkent sinonimi
        if (cleanSearch.equals("toshkent") || cleanSearch.equals("tashkent")) {
            return cleanFarm.contains("toshkent") || cleanFarm.contains("tashkent");
        }

        // Bir necha so'z kiritilgan bo'lsa (masalan: "Chilonzor tumani", "Registon ko'chasi")
        String[] tokens = cleanSearch.split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 3) {
                if (cleanFarm.contains(token)) {
                    return true;
                }
                // Token uchun ham toshkent / tashkent tekshiruvi
                if ((token.equals("toshkent") || token.equals("tashkent")) &&
                    (cleanFarm.contains("toshkent") || cleanFarm.contains("tashkent"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private String cleanLocationString(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("\\b(sh|sh\\.|shahri|shahar|tumani|tuman|viloyati|viloyat|region|oblast|mfy|ko'chasi|ko‘chasi|massivi)\\b", "")
                .replaceAll("[^a-z0-9а-яёўқғҳ\\s]", " ")
                .trim();
    }

    public FarmResponse getMyFarm() {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz profili topilmadi"));

        if (client.getFarmId() == null) {
            throw new BadRequestException("Siz hali birorta suv firmasiga biriktirilmagansiz");
        }

        Farm farm = farmRepository.findById(client.getFarmId())
                .orElseThrow(() -> new ResourceNotFoundException("Biriktirilgan ferma topilmadi"));

        User boss = farm.getBossUserId() != null ? userRepository.findById(farm.getBossUserId()).orElse(null) : null;
        return FarmResponse.builder()
                .id(farm.getId())
                .name(farm.getName())
                .phone(farm.getPhone())
                .address(farm.getAddress())
                .latitude(farm.getLatitude())
                .longitude(farm.getLongitude())
                .logoUrl(farm.getLogoUrl())
                .status(farm.getStatus())
                .bossUserId(farm.getBossUserId())
                .bossFullName(boss != null ? boss.getFullName() : null)
                .bossPhone(boss != null ? boss.getPhone() : null)
                .createdAt(farm.getCreatedAt())
                .build();
    }

    public List<CrmClientResponse> getCrmClients(Long farmId) {
        List<Client> clients = clientRepository.findAllByFarmId(farmId);
        List<CrmClientResponse> response = new ArrayList<>();

        for (Client client : clients) {
            User user = userRepository.findById(client.getUserId()).orElse(null);
            long totalOrders = orderRepository.countByClientIdAndFarmId(client.getId(), farmId);
            BigDecimal totalSpent = orderRepository.sumSpentByClientAndFarm(client.getId(), farmId);
            Instant lastOrderAt = orderRepository.findLastOrderDateByClientAndFarm(client.getId(), farmId);

            List<ClientAddress> addresses = addressRepository.findAllByClientId(client.getId());
            String primaryAddress = addresses.stream()
                    .filter(ClientAddress::isDefault)
                    .map(ClientAddress::getAddress)
                    .findFirst()
                    .orElse(addresses.isEmpty() ? null : addresses.get(0).getAddress());

            response.add(CrmClientResponse.builder()
                    .clientId(client.getId())
                    .userId(client.getUserId())
                    .fullName(user != null ? user.getFullName() : "Noma'lum")
                    .phone(user != null ? user.getPhone() : "-")
                    .registeredAt(client.getCreatedAt())
                    .totalOrders(totalOrders)
                    .totalSpent(totalSpent)
                    .lastOrderAt(lastOrderAt)
                    .primaryAddress(primaryAddress)
                    .build());
        }

        return response;
    }

    public CrmClientDetailResponse getCrmClientDetail(Long farmId, Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Mijoz topilmadi: ID " + clientId));

        if (!farmId.equals(client.getFarmId())) {
            throw new BadRequestException("Ushbu mijoz sizning fermangizga biriktirilmagan");
        }

        User user = userRepository.findById(client.getUserId()).orElse(null);
        long totalOrders = orderRepository.countByClientIdAndFarmId(client.getId(), farmId);
        BigDecimal totalSpent = orderRepository.sumSpentByClientAndFarm(client.getId(), farmId);

        List<ClientAddress> addresses = addressRepository.findAllByClientId(client.getId());
        List<AddressResponse> addressResponses = addresses.stream()
                .map(this::mapToAddressResponse)
                .toList();

        List<Order> orders = orderRepository.findAllByClientIdAndFarmIdOrderByCreatedAtDesc(client.getId(), farmId);
        int totalReturned = 0;
        for (Order o : orders) {
            if (o.getEmptyBottlesReturned() != null) {
                totalReturned += o.getEmptyBottlesReturned();
            }
        }

        List<CrmClientOrderSummary> recentOrderSummaries = orders.stream()
                .limit(10)
                .map(o -> CrmClientOrderSummary.builder()
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .status(o.getStatus().name())
                        .totalSum(o.getTotalSum())
                        .deliveryAddress(o.getDeliveryAddress())
                        .deliverySlot(o.getDeliverySlot())
                        .emptyBottlesReturned(o.getEmptyBottlesReturned())
                        .createdAt(o.getCreatedAt())
                        .build())
                .toList();

        return CrmClientDetailResponse.builder()
                .clientId(client.getId())
                .userId(client.getUserId())
                .fullName(user != null ? user.getFullName() : "Noma'lum")
                .phone(user != null ? user.getPhone() : "-")
                .registeredAt(client.getCreatedAt())
                .totalOrders(totalOrders)
                .totalSpent(totalSpent)
                .bottleBalance(totalReturned)
                .addresses(addressResponses)
                .recentOrders(recentOrderSummaries)
                .build();
    }

    private AddressResponse mapToAddressResponse(ClientAddress a) {
        return AddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .address(a.getAddress())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .isDefault(a.isDefault())
                .build();
    }
}
