package uz.gidrogo.modules.farm;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.FileUrls;
import uz.gidrogo.common.Granularity;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.common.PasswordPolicy;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.common.TashkentTime;
import uz.gidrogo.modules.auth.OtpService;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.farm.dto.FarmDtos.*;
import uz.gidrogo.modules.finance.RevenueService;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.order.OrderService;
import uz.gidrogo.modules.order.OrderStatsService;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
import uz.gidrogo.modules.product.ProductService;
import uz.gidrogo.modules.product.dto.ProductDtos.ProductResponse;
import uz.gidrogo.modules.staff.StaffDtos.StaffResponse;
import uz.gidrogo.modules.superadmin.AuditAction;
import uz.gidrogo.modules.superadmin.AuditService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FarmService {

    private static final Set<String> FARM_SORT_FIELDS = Set.of("id", "name", "createdAt", "status", "city");
    private static final Set<String> ACTIVATION_SORT_FIELDS = Set.of("requestedAt", "decidedAt", "id");

    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final BossActivationRequestRepository activationRequestRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ProductService productService;
    private final RevenueService revenueService;
    private final OrderStatsService orderStatsService;
    private final FarmAnalyticsService farmAnalyticsService;

    // ==========================================
    // Ferma yaratish (BE-17)
    // ==========================================

    /** Ferma, boss va audit FARM_CREATED — bitta tranzaksiyada. */
    @Transactional
    public FarmResponse createFarm(FarmCreateRequest request) {
        String name = request.getName().trim();
        String farmPhone = normalizePhoneOrFail(request.getPhone(), "phone");
        String bossPhone = normalizePhoneOrFail(request.getBossPhone(), "bossPhone");
        PasswordPolicy.validate(request.getBossPassword(), "bossPassword");

        if (farmRepository.findByNameIgnoreCase(name).isPresent()) {
            throw ApiException.conflict("FARM_NAME_EXISTS", "Bu nomdagi ferma allaqachon mavjud");
        }
        // Boss telefoni barcha foydalanuvchilar orasida noyob
        if (userRepository.existsByPhone(bossPhone)) {
            throw ApiException.conflict("PHONE_ALREADY_EXISTS", "Bu telefon raqamli foydalanuvchi allaqachon mavjud");
        }

        Farm farm = Farm.builder()
                .name(name)
                .phone(farmPhone)
                .address(request.getAddress().trim())
                .latitude(BigDecimal.valueOf(request.getLatitude()))
                .longitude(BigDecimal.valueOf(request.getLongitude()))
                .logoUrl(FileUrls.normalize(request.getLogoUrl()))
                .city(blankToNull(request.getCity()))
                .district(blankToNull(request.getDistrict()))
                .status(FarmStatus.ACTIVE)
                .build();
        farm = farmRepository.save(farm);

        // Har bir fermada FAQAT 1 ta Boss. Yangi boss birinchi kirishda faollashtirish so'rovini yuboradi (TZ §7.2).
        User boss = User.builder()
                .farmId(farm.getId())
                .role(Role.BOSS)
                .fullName(request.getBossFullName().trim())
                .phone(bossPhone)
                .passwordHash(passwordEncoder.encode(request.getBossPassword()))
                .status("ACTIVE")
                .activationStatus(BossActivationStatus.NOT_REQUESTED)
                .build();
        boss = userRepository.save(boss);

        farm.setBossUserId(boss.getId());
        farm = farmRepository.save(farm);

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("farmName", farm.getName());
        created.put("farmPhone", farm.getPhone());
        created.put("address", farm.getAddress());
        created.put("latitude", farm.getLatitude());
        created.put("longitude", farm.getLongitude());
        created.put("status", farm.getStatus());
        created.put("bossUserId", boss.getId());
        created.put("bossFullName", boss.getFullName());
        created.put("bossPhone", boss.getPhone());
        auditService.record(AuditAction.FARM_CREATED, "FARM", farm.getId(), null, created);

        return mapToResponse(farm, boss);
    }

    // ==========================================
    // Ferma holati (BE-14 / CHK-03)
    // ==========================================

    /**
     * BLOCKED yoki INACTIVE fermaga yangi checkout 409, mijoz ro'yxatlarida ko'rinmaydi, dispatch yangi taklif bermaydi.
     * ASSIGNED va undan keyingi buyurtmalar davom etadi. SEARCHING/PREPARING buyurtmalar taqdiri — Q6 (loyiha egasi qarori);
     * hozircha ular o'zgartirilmaydi.
     */
    @Transactional
    public FarmResponse updateFarmStatus(Long farmId, FarmStatus status, String reason) {
        Farm farm = farmRepository.findByIdForUpdate(farmId)
                .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Ferma topilmadi"));

        FarmStatus oldStatus = farm.getStatus();
        if (oldStatus == status) {
            User boss = farm.getBossUserId() != null ? userRepository.findById(farm.getBossUserId()).orElse(null) : null;
            return mapToResponse(farm, boss);
        }
        farm.setStatus(status);
        farm = farmRepository.save(farm);

        String oldBossStatus = null;
        String newBossStatus = null;
        User boss = farm.getBossUserId() != null ? userRepository.findById(farm.getBossUserId()).orElse(null) : null;
        if (boss != null && !"DELETED".equalsIgnoreCase(boss.getStatus())) {
            oldBossStatus = boss.getStatus();
            if (status == FarmStatus.ACTIVE) {
                boss.setStatus("ACTIVE");
            } else if (status == FarmStatus.BLOCKED) {
                boss.setStatus("BLOCKED");
            }
            newBossStatus = boss.getStatus();
            userRepository.save(boss);
        }

        Map<String, Object> oldV = new LinkedHashMap<>();
        oldV.put("status", oldStatus);
        oldV.put("bossStatus", oldBossStatus);
        Map<String, Object> newV = new LinkedHashMap<>();
        newV.put("status", status);
        newV.put("bossStatus", newBossStatus);
        Map<String, Object> extra = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) extra.put("reason", reason.trim());
        auditService.record(status == FarmStatus.BLOCKED ? AuditAction.FARM_BLOCKED : AuditAction.FARM_STATUS_CHANGED,
                "FARM", farm.getId(), oldV, newV, extra);

        return mapToResponse(farm, boss);
    }

    // ==========================================
    // Fermalar ro'yxati (BE-22)
    // ==========================================

    public PageResponse<FarmResponse> getFarms(String search, FarmStatus status, String region,
                                               int page, int size, String sort) {
        Pageable pageable = PageResponse.pageable(page, size, sort, FARM_SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Farm> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (region != null && !region.isBlank()) {
                ps.add(cb.equal(cb.lower(root.get("city")), region.trim().toLowerCase()));
            }
            if (search != null && !search.isBlank()) {
                String s = "%" + search.trim().toLowerCase() + "%";
                String digits = search.replaceAll("[^0-9]", "");
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("name")), s));
                or.add(cb.like(cb.lower(cb.coalesce(root.get("address"), "")), s));
                or.add(cb.like(cb.lower(cb.coalesce(root.get("city"), "")), s));
                if (digits.length() >= 3) {
                    or.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }
                ps.add(cb.or(or.toArray(new Predicate[0])));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Farm> farms = farmRepository.findAll(spec, pageable);
        List<Long> bossIds = farms.getContent().stream().map(Farm::getBossUserId).filter(java.util.Objects::nonNull).toList();
        Map<Long, User> bosses = userRepository.findAllById(bossIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(farms, f -> mapToResponse(f, f.getBossUserId() != null ? bosses.get(f.getBossUserId()) : null));
    }

    public FarmResponse getFarmById(Long id) {
        Farm farm = farmRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Ferma topilmadi"));
        User boss = farm.getBossUserId() != null ? userRepository.findById(farm.getBossUserId()).orElse(null) : null;
        return mapToResponse(farm, boss);
    }

    // ==========================================
    // Boss tasdiqlash so'rovlari (BE-04, BE-05)
    // ==========================================

    /** Arxiv bilan: status = PENDING | APPROVED | REJECTED (bo'sh — barchasi). from/to — requestedAt, Toshkent kunlari. */
    public ActivationRequestPage getActivationRequests(ActivationStatus status, LocalDate from, LocalDate to,
                                                       int page, int size, String sort) {
        Pageable pageable = PageResponse.pageable(page, size, sort, ACTIVATION_SORT_FIELDS,
                Sort.by(Sort.Direction.DESC, "requestedAt"));
        Specification<BossActivationRequest> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), TashkentTime.startOf(from)));
            if (to != null) ps.add(cb.lessThan(root.get("requestedAt"), TashkentTime.endExclusive(to)));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<BossActivationRequest> result = activationRequestRepository.findAll(spec, pageable);
        PageResponse<ActivationRequestResponse> p = PageResponse.of(result, this::mapToActivationResponse);
        return ActivationRequestPage.builder()
                .summary(ActivationSummary.builder()
                        .pending(activationRequestRepository.countByStatus(ActivationStatus.PENDING))
                        .approved(activationRequestRepository.countByStatus(ActivationStatus.APPROVED))
                        .rejected(activationRequestRepository.countByStatus(ActivationStatus.REJECTED))
                        .build())
                .content(p.getContent())
                .page(p.getPage())
                .size(p.getSize())
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .build();
    }

    /**
     * Qaror faqat PENDING so'rovga qabul qilinadi (aks holda 409 REQUEST_ALREADY_DECIDED).
     * So'rov qatori SELECT … FOR UPDATE bilan qulflanadi. Audit qaror bilan bitta tranzaksiyada.
     */
    @Transactional
    public ActivationRequestResponse decideBossActivation(Long requestId, ActivationDecisionRequest request) {
        String reason = request.getRejectReason() != null ? request.getRejectReason().trim() : null;
        if (request.getStatus() == ActivationDecision.REJECTED && (reason == null || reason.length() < 10 || reason.length() > 500)) {
            throw ApiException.unprocessable("REJECT_REASON_REQUIRED", "rejectReason",
                    "Rad etish sababi 10–500 belgidan iborat bo'lishi shart");
        }

        BossActivationRequest bar = activationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Tasdiqlash so'rovi topilmadi"));
        if (bar.getStatus() != ActivationStatus.PENDING) {
            throw ApiException.conflict("REQUEST_ALREADY_DECIDED", "Bu so'rov bo'yicha qaror allaqachon qabul qilingan");
        }

        User boss = userRepository.findById(bar.getBossUserId())
                .orElseThrow(() -> ApiException.notFound("BOSS_NOT_FOUND", "Boss foydalanuvchi topilmadi"));

        Long superAdminId = SecurityUtils.getCurrentUserId();
        BossActivationStatus oldActivation = boss.getActivationStatus();
        boolean approved = request.getStatus() == ActivationDecision.APPROVED;

        bar.setStatus(approved ? ActivationStatus.APPROVED : ActivationStatus.REJECTED);
        bar.setRejectReason(approved ? null : reason);
        bar.setDecidedAt(Instant.now());
        bar.setDecidedBy(superAdminId);
        bar = activationRequestRepository.save(bar);

        boss.setActivationStatus(approved ? BossActivationStatus.APPROVED : BossActivationStatus.REJECTED);
        userRepository.save(boss);

        Map<String, Object> oldV = new LinkedHashMap<>();
        oldV.put("requestStatus", ActivationStatus.PENDING);
        oldV.put("activationStatus", oldActivation);
        Map<String, Object> newV = new LinkedHashMap<>();
        newV.put("requestStatus", bar.getStatus());
        newV.put("activationStatus", boss.getActivationStatus());
        if (!approved) newV.put("rejectReason", reason);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("bossUserId", boss.getId());
        extra.put("farmId", bar.getFarmId());
        auditService.record(approved ? AuditAction.BOSS_REQUEST_APPROVED : AuditAction.BOSS_REQUEST_REJECTED,
                "BOSS_ACTIVATION_REQUEST", bar.getId(), oldV, newV, extra);

        return mapToActivationResponse(bar);
    }

    // ==========================================
    // Ferma tafsiloti (BE-02, BE-11)
    // ==========================================

    public FarmDetailResponse getFarmDetail(Long farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Ferma topilmadi: ID=" + farmId));

        User boss = farm.getBossUserId() != null ? userRepository.findById(farm.getBossUserId()).orElse(null) : null;

        // Xodimlar
        List<User> farmUsers = userRepository.findAllByFarmId(farmId).stream()
                .filter(u -> !"DELETED".equalsIgnoreCase(u.getStatus()))
                .toList();
        int managersCount = (int) farmUsers.stream().filter(u -> u.getRole() == Role.MANAGER).count();
        int couriersCount = (int) farmUsers.stream().filter(u -> u.getRole() == Role.COURIER).count();
        int activeManagers = (int) farmUsers.stream().filter(u -> u.getRole() == Role.MANAGER && "ACTIVE".equalsIgnoreCase(u.getStatus())).count();
        int activeCouriers = (int) farmUsers.stream().filter(u -> u.getRole() == Role.COURIER && "ACTIVE".equalsIgnoreCase(u.getStatus())).count();
        List<StaffResponse> staffList = farmUsers.stream()
                .filter(u -> u.getRole() == Role.MANAGER || u.getRole() == Role.COURIER)
                .map(this::mapStaffToResponse)
                .toList();

        List<ProductResponse> products = productService.getProductsByFarm(farmId);
        long totalClients = clientRepository.countByFarmId(farmId);

        // Buyurtma sonlari — butun davr
        OrderStatsService.StatusCounts counts = orderStatsService.countByStatus(farmId, null, null);
        long totalOrders = counts.total();
        Double successRate = totalOrders > 0
                ? BigDecimal.valueOf(counts.completed() * 100.0 / totalOrders).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : null;

        // O'sish: oxirgi 30 kun vs oldingi 30 kun (Toshkent kunlari)
        LocalDate today = TashkentTime.today();
        Instant curStart = TashkentTime.startOf(today.minusDays(29));
        Instant curEnd = TashkentTime.endExclusive(today);
        Instant prevStart = TashkentTime.startOf(today.minusDays(59));
        long ordersCur = orderStatsService.countByStatus(farmId, curStart, curEnd).total();
        long ordersPrev = orderStatsService.countByStatus(farmId, prevStart, curStart).total();

        RevenueService.Totals allTime = revenueService.totals(farmId, Instant.EPOCH, curEnd);
        BigDecimal expensesAllTime = revenueService.expenses(farmId, Instant.EPOCH, curEnd);
        RevenueService.Totals revCur = revenueService.totals(farmId, curStart, curEnd);
        RevenueService.Totals revPrev = revenueService.totals(farmId, prevStart, curStart);
        Double revenueGrowth = TashkentTime.growthPercent(revCur.revenue(), revPrev.revenue());

        StatisticsCards cards = StatisticsCards.builder()
                .totalOrders(totalOrders)
                .activeOrders(counts.active())
                .completedOrders(counts.completed())
                .cancelledOrders(counts.cancelled())
                .returnedOrders(counts.returned())
                .successRatePercentage(successRate)
                .ordersGrowthPercentage(TashkentTime.growthPercent(ordersCur, ordersPrev))
                .totalRevenue(allTime.revenue())
                .taraRevenue(allTime.taraRevenue())
                .expenses(expensesAllTime)
                .profit(revenueService.profit(allTime, expensesAllTime))
                .revenueGrowthPercentage(revenueGrowth)
                .monthlyGrowthPercent(revenueGrowth)
                .activeManagers(activeManagers)
                .activeCouriers(activeCouriers)
                .clients(totalClients)
                .build();

        List<OrderResponse> recentOrders = orderRepository.findTop10ByFarmIdOrderByCreatedAtDesc(farmId).stream()
                .map(o -> orderService.mapToResponse(o, false))
                .toList();

        return FarmDetailResponse.builder()
                .id(farm.getId())
                .name(farm.getName())
                .phone(farm.getPhone())
                .address(farm.getAddress())
                .city(farm.getCity())
                .district(farm.getDistrict())
                .latitude(farm.getLatitude())
                .longitude(farm.getLongitude())
                .logoUrl(FileUrls.normalize(farm.getLogoUrl()))
                .status(farm.getStatus())
                .createdAt(farm.getCreatedAt())
                .bossUserId(boss != null ? boss.getId() : null)
                .bossFullName(boss != null ? boss.getFullName() : null)
                .bossPhone(boss != null ? boss.getPhone() : null)
                .bossStatus(boss != null ? boss.getStatus() : null)
                .statistics(cards)
                .weeklyChart(farmAnalyticsService.weeklyChart(farmId))
                .monthlyChart(farmAnalyticsService.monthlyChart(farmId))
                .totalStaff(managersCount + couriersCount)
                .totalManagers(managersCount)
                .totalCouriers(couriersCount)
                .activeCouriers(activeCouriers)
                .totalClients(totalClients)
                .products(products)
                .staff(staffList)
                .recentOrders(recentOrders)
                .build();
    }

    public FarmAnalyticsResponse getFarmAnalytics(Long farmId, LocalDate from, LocalDate to, Granularity granularity) {
        if (!farmRepository.existsById(farmId)) {
            throw ApiException.notFound("FARM_NOT_FOUND", "Ferma topilmadi: ID=" + farmId);
        }
        LocalDate end = to != null ? to : TashkentTime.today();
        LocalDate start = from != null ? from : end.minusDays(29);
        Granularity g = granularity != null ? granularity : Granularity.DAY;
        Granularity.validateRange(start, end, g == Granularity.DAY ? 366 : 3 * 366);
        return farmAnalyticsService.analytics(farmId, start, end, g);
    }

    // ==========================================
    // Mapperlar
    // ==========================================

    private FarmResponse mapToResponse(Farm farm, User boss) {
        return FarmResponse.builder()
                .id(farm.getId())
                .name(farm.getName())
                .phone(farm.getPhone())
                .address(farm.getAddress())
                .city(farm.getCity())
                .district(farm.getDistrict())
                .latitude(farm.getLatitude())
                .longitude(farm.getLongitude())
                .logoUrl(FileUrls.normalize(farm.getLogoUrl()))
                .status(farm.getStatus())
                .bossUserId(farm.getBossUserId())
                .bossFullName(boss != null ? boss.getFullName() : null)
                .bossPhone(boss != null ? boss.getPhone() : null)
                .createdAt(farm.getCreatedAt())
                .build();
    }

    private ActivationRequestResponse mapToActivationResponse(BossActivationRequest bar) {
        Farm farm = farmRepository.findById(bar.getFarmId()).orElse(null);
        User boss = userRepository.findById(bar.getBossUserId()).orElse(null);

        return ActivationRequestResponse.builder()
                .id(bar.getId())
                .farmId(bar.getFarmId())
                .farmName(farm != null ? farm.getName() : null)
                .bossUserId(bar.getBossUserId())
                .bossFullName(boss != null ? boss.getFullName() : null)
                .bossPhone(boss != null ? boss.getPhone() : null)
                .status(bar.getStatus())
                .rejectReason(bar.getRejectReason())
                .requestedAt(bar.getRequestedAt())
                .decidedAt(bar.getDecidedAt())
                .decidedBy(bar.getDecidedBy())
                .build();
    }

    private StaffResponse mapStaffToResponse(User user) {
        return StaffResponse.builder()
                .id(user.getId())
                .farmId(user.getFarmId())
                .role(user.getRole())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /** +998XXXXXXXXX ga keltiradi; bo'lmasa → 400 INVALID_PHONE. */
    static String normalizePhoneOrFail(String raw, String field) {
        String p = OtpService.normalizePhone(raw);
        if (!p.matches("^\\+998\\d{9}$")) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PHONE",
                    "Telefon formati noto'g'ri. Masalan: +998901234567",
                    List.of(new uz.gidrogo.common.ApiResponse.FieldErrorItem(field, "Telefon formati noto'g'ri")), null);
        }
        return p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
