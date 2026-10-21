package uz.gidrogo.modules.superadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import uz.gidrogo.modules.auth.RefreshTokenService;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.farm.BossActivationStatus;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.farm.FarmStatus;
import uz.gidrogo.modules.finance.RevenueService;
import uz.gidrogo.modules.order.OrderStatsService;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.superadmin.dto.SuperAdminDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final Set<String> BOSS_SORT_FIELDS = Set.of("createdAt", "fullName", "id");
    private static final Set<String> AUDIT_SORT_FIELDS = Set.of("createdAt", "action", "id");
    private static final String UNKNOWN_REGION = "Belgilanmagan";

    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final SuperAdminAuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final OrderStatsService orderStatsService;
    private final RevenueService revenueService;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    // ==========================================
    // 0. DASHBOARD (BE-10, TZ §5)
    // ==========================================

    /**
     * Umumiy KPI (butun davr) + tanlangan davr (standart: oxirgi 30 kun) va oldingi teng davrga nisbatan o'sish.
     * Global tushum yo'q (TZ §5.5).
     */
    public DashboardResponse getDashboard(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : TashkentTime.today();
        LocalDate start = from != null ? from : end.minusDays(29);
        Granularity.validateRange(start, end, 3 * 366);
        long length = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(length - 1);

        Instant curFrom = TashkentTime.startOf(start);
        Instant curTo = TashkentTime.endExclusive(end);
        Instant prevFrom = TashkentTime.startOf(prevStart);

        OrderStatsService.StatusCounts orders = orderStatsService.countByStatus(null, null, null);

        long newClients = countUsersCreated(Role.CLIENT, curFrom, curTo);
        long newClientsPrev = countUsersCreated(Role.CLIENT, prevFrom, curFrom);
        long periodOrders = orderStatsService.countByStatus(null, curFrom, curTo).total();
        long periodOrdersPrev = orderStatsService.countByStatus(null, prevFrom, curFrom).total();
        long newFarms = countFarmsCreated(curFrom, curTo);
        long newFarmsPrev = countFarmsCreated(prevFrom, curFrom);

        Double ordersPercent = TashkentTime.growthPercent(periodOrders, periodOrdersPrev);
        Double clientsPercent = TashkentTime.growthPercent(newClients, newClientsPrev);
        Double farmsPercent = TashkentTime.growthPercent(newFarms, newFarmsPrev);
        Double growthRate = ordersPercent != null ? ordersPercent : (clientsPercent != null ? clientsPercent : 0.0);

        return DashboardResponse.builder()
                .totalFarms(farmRepository.count())
                .activeFarms(farmRepository.countByStatus(FarmStatus.ACTIVE))
                .blockedFarms(farmRepository.countByStatus(FarmStatus.BLOCKED))
                .inactiveFarms(farmRepository.countByStatus(FarmStatus.INACTIVE))
                .totalCouriers(countUsers(Role.COURIER))
                .totalClients(countUsers(Role.CLIENT))
                .totalOrders(orders.total())
                .activeOrders(orders.active())
                .completedOrders(orders.completed())
                .cancelledOrders(orders.cancelled())
                .returnedOrders(orders.returned())
                .period(PeriodStats.builder()
                        .from(start.toString())
                        .to(end.toString())
                        .previousFrom(prevStart.toString())
                        .previousTo(prevEnd.toString())
                        .newClients(newClients)
                        .activeClients(countActiveClients(curFrom, curTo))
                        .orders(periodOrders)
                        .newFarms(newFarms)
                        .build())
                .growth(Growth.builder()
                        .clientsPercent(clientsPercent)
                        .ordersPercent(ordersPercent)
                        .farmsPercent(farmsPercent)
                        .build())
                .growthRate(growthRate)
                .build();
    }

    /** GET /statistics/growth — TZ §5.3 grafiklari va §11 oylik dinamika. */
    public List<GrowthPoint> getGrowthSeries(LocalDate from, LocalDate to, Granularity granularity) {
        LocalDate end = to != null ? to : TashkentTime.today();
        Granularity g = granularity != null ? granularity : Granularity.MONTH;
        LocalDate start = from != null ? from : switch (g) {
            case DAY -> end.minusDays(29);
            case WEEK -> end.minusWeeks(11);
            case MONTH -> end.minusMonths(11).withDayOfMonth(1);
        };
        Granularity.validateRange(start, end, g == Granularity.DAY ? 366 : 3 * 366);

        Instant fromI = TashkentTime.startOf(start);
        Instant toI = TashkentTime.endExclusive(end);
        String unit = g.name().toLowerCase(Locale.ROOT);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(fromI))
                .addValue("to", Timestamp.from(toI));

        Map<LocalDate, Long> clients = bucketCounts("SELECT " + bucket(unit, "created_at") + " AS b, COUNT(*) AS c FROM users "
                + "WHERE role = 'CLIENT' AND created_at >= :from AND created_at < :to GROUP BY b", p);
        Map<LocalDate, Long> active = bucketCounts("SELECT " + bucket(unit, "created_at") + " AS b, COUNT(DISTINCT client_id) AS c FROM orders "
                + "WHERE created_at >= :from AND created_at < :to GROUP BY b", p);
        Map<LocalDate, Long> orders = bucketCounts("SELECT " + bucket(unit, "created_at") + " AS b, COUNT(*) AS c FROM orders "
                + "WHERE created_at >= :from AND created_at < :to GROUP BY b", p);
        Map<LocalDate, Long> farms = bucketCounts("SELECT " + bucket(unit, "created_at") + " AS b, COUNT(*) AS c FROM farms "
                + "WHERE created_at >= :from AND created_at < :to GROUP BY b", p);

        List<GrowthPoint> series = new ArrayList<>();
        for (LocalDate b = g.bucketStart(start); !b.isAfter(end); b = g.next(b)) {
            LocalDate pStart = b.isBefore(start) ? start : b;
            LocalDate pEnd = g.next(b).minusDays(1).isAfter(end) ? end : g.next(b).minusDays(1);
            series.add(GrowthPoint.builder()
                    .periodStart(pStart.toString())
                    .periodEnd(pEnd.toString())
                    .newClients(clients.getOrDefault(b, 0L))
                    .activeClients(active.getOrDefault(b, 0L))
                    .orders(orders.getOrDefault(b, 0L))
                    .newFarms(farms.getOrDefault(b, 0L))
                    .build());
        }
        return series;
    }

    // ==========================================
    // 1. STATISTIKA (BE-15, TZ §11)
    // ==========================================

    /** Hudud (farms.city) bo'yicha fermalar holati va davrdagi buyurtmalar. O'ylab topilgan qiymat yo'q. */
    public RegionalDistributionResponse getRegionalDistribution(LocalDate from, LocalDate to, String region) {
        LocalDate end = to != null ? to : TashkentTime.today();
        LocalDate start = from != null ? from : end.minusDays(29);
        Granularity.validateRange(start, end, 3 * 366);
        long length = ChronoUnit.DAYS.between(start, end) + 1;
        Instant curFrom = TashkentTime.startOf(start);
        Instant curTo = TashkentTime.endExclusive(end);
        Instant prevFrom = TashkentTime.startOf(start.minusDays(length));

        String regionExpr = "COALESCE(NULLIF(TRIM(f.city), ''), '" + UNKNOWN_REGION + "')";
        Map<String, long[]> farmCounts = new LinkedHashMap<>(); // [total, active, blocked, inactive]
        jdbc.query("SELECT " + regionExpr + " AS region, f.status AS status, COUNT(*) AS c FROM farms f GROUP BY region, f.status",
                new MapSqlParameterSource(), rs -> {
                    long[] arr = farmCounts.computeIfAbsent(rs.getString("region"), k -> new long[4]);
                    long c = rs.getLong("c");
                    arr[0] += c;
                    switch (rs.getString("status")) {
                        case "ACTIVE" -> arr[1] += c;
                        case "BLOCKED" -> arr[2] += c;
                        case "INACTIVE" -> arr[3] += c;
                        default -> { }
                    }
                });

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("curFrom", Timestamp.from(curFrom))
                .addValue("curTo", Timestamp.from(curTo))
                .addValue("prevFrom", Timestamp.from(prevFrom));
        Map<String, long[]> orderCounts = new HashMap<>(); // [current, previous]
        jdbc.query("SELECT " + regionExpr + " AS region, "
                        + "COUNT(*) FILTER (WHERE o.created_at >= :curFrom AND o.created_at < :curTo) AS cur, "
                        + "COUNT(*) FILTER (WHERE o.created_at >= :prevFrom AND o.created_at < :curFrom) AS prev "
                        + "FROM orders o JOIN farms f ON f.id = o.farm_id "
                        + "WHERE o.created_at >= :prevFrom AND o.created_at < :curTo GROUP BY region",
                p, rs -> {
                    orderCounts.put(rs.getString("region"), new long[]{rs.getLong("cur"), rs.getLong("prev")});
                });

        long allFarms = farmCounts.values().stream().mapToLong(a -> a[0]).sum();
        List<RegionItem> regions = farmCounts.entrySet().stream()
                .filter(e -> region == null || region.isBlank() || e.getKey().equalsIgnoreCase(region.trim()))
                .map(e -> {
                    long[] fc = e.getValue();
                    long[] oc = orderCounts.getOrDefault(e.getKey(), new long[2]);
                    double pct = allFarms == 0 ? 0.0
                            : BigDecimal.valueOf(fc[0] * 100.0 / allFarms).setScale(1, RoundingMode.HALF_UP).doubleValue();
                    return RegionItem.builder()
                            .regionName(e.getKey())
                            .totalFarms(fc[0])
                            .activeFarms(fc[1])
                            .blockedFarms(fc[2])
                            .inactiveFarms(fc[3])
                            .percentage(pct)
                            .ordersInPeriod(oc[0])
                            .ordersPreviousPeriod(oc[1])
                            .growthPercent(TashkentTime.growthPercent(oc[0], oc[1]))
                            .build();
                })
                .sorted(Comparator.comparingLong(RegionItem::getTotalFarms).reversed().thenComparing(RegionItem::getRegionName))
                .toList();

        return RegionalDistributionResponse.builder()
                .from(start.toString())
                .to(end.toString())
                .totalFarms(allFarms)
                .regions(regions)
                .build();
    }

    /** GET /statistics/orders-by-status (TZ §11.2). Har bir holat (0 bo'lsa ham) ro'yxatda. */
    public OrdersByStatusResponse getOrdersByStatus(LocalDate from, LocalDate to, Long farmId) {
        LocalDate end = to != null ? to : TashkentTime.today();
        LocalDate start = from != null ? from : end.minusDays(29);
        Granularity.validateRange(start, end, 3 * 366);
        OrderStatsService.StatusCounts counts = orderStatsService.countByStatus(
                farmId, TashkentTime.startOf(start), TashkentTime.endExclusive(end));
        List<StatusCount> list = Arrays.stream(OrderStatus.values())
                .map(s -> StatusCount.builder().status(s.name()).count(counts.get(s)).build())
                .toList();
        return OrdersByStatusResponse.builder()
                .from(start.toString())
                .to(end.toString())
                .farmId(farmId)
                .total(counts.total())
                .statuses(list)
                .build();
    }

    // ==========================================
    // 2. FERMA EGALARI (BE-03, BE-16, BE-22)
    // ==========================================

    /**
     * Bosslar ro'yxati. Boss–ferma bog'lanishi faqat farms.boss_user_id dan olinadi.
     * summary — ro'yxat bilan bir xil manba va filtrdan (sahifalashdan oldin): summary.total = totalElements.
     */
    public BossListResponse getBosses(String search, String status, Long farmId, BossActivationStatus activationStatus,
                                      int page, int size, String sort) {
        Pageable pageable = PageResponse.pageable(page, size, sort, BOSS_SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));

        Map<Long, Farm> farmByBoss = farmRepository.findAll().stream()
                .filter(f -> f.getBossUserId() != null)
                .collect(Collectors.toMap(Farm::getBossUserId, Function.identity()));

        String s = search != null && !search.isBlank() ? search.trim().toLowerCase(Locale.ROOT) : null;
        String sDigits = search != null ? search.replaceAll("[^0-9]", "") : "";
        String statusFilter = normalizeStatusFilter(status);

        List<User> filtered = userRepository.findAllByRoleOrderByCreatedAtDesc(Role.BOSS).stream()
                .filter(u -> !"DELETED".equalsIgnoreCase(u.getStatus()))
                .filter(u -> {
                    Farm f = farmByBoss.get(u.getId());
                    if (s != null) {
                        boolean match = (u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(s))
                                || (sDigits.length() >= 3 && u.getPhone() != null && u.getPhone().replaceAll("[^0-9]", "").contains(sDigits))
                                || (f != null && f.getName() != null && f.getName().toLowerCase(Locale.ROOT).contains(s))
                                || (f != null && farmCode(f).contains(s));
                        if (!match) return false;
                    }
                    if (statusFilter != null && !statusFilter.equalsIgnoreCase(u.getStatus())) return false;
                    if (farmId != null && (f == null || !farmId.equals(f.getId()))) return false;
                    if (activationStatus != null && activationStatus != effectiveActivation(u)) return false;
                    return true;
                })
                .sorted(bossComparator(pageable.getSort()))
                .toList();

        Instant recentSince = Instant.now().minus(30, ChronoUnit.DAYS);
        BossSummaryCards summary = BossSummaryCards.builder()
                .total(filtered.size())
                .active(filtered.stream().filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus())).count())
                .blocked(filtered.stream().filter(u -> "BLOCKED".equalsIgnoreCase(u.getStatus())).count())
                .recentlyAdded(filtered.stream().filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(recentSince)).count())
                .build();

        PageResponse<BossItemResponse> p = PageResponse.ofList(
                filtered.stream().map(u -> mapToBossItem(u, farmByBoss.get(u.getId()))).toList(),
                pageable.getPageNumber(), pageable.getPageSize());

        return BossListResponse.builder()
                .summary(summary)
                .content(p.getContent())
                .page(p.getPage())
                .size(p.getSize())
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .build();
    }

    /** Hisob holatini almashtirish (ACTIVE ↔ BLOCKED). Boss bloklansa fermasi ham BLOCKED; ochilsa — faqat BLOCKED bo'lgan ferma ACTIVE. */
    @Transactional
    public BossItemResponse toggleBossStatus(Long bossId) {
        User boss = findBoss(bossId);
        Farm farm = farmRepository.findByBossUserId(boss.getId())
                .flatMap(f -> farmRepository.findByIdForUpdate(f.getId()))
                .orElse(null);

        String oldStatus = boss.getStatus();
        boolean currentlyActive = "ACTIVE".equalsIgnoreCase(oldStatus);
        boss.setStatus(currentlyActive ? "BLOCKED" : "ACTIVE");
        userRepository.save(boss);

        FarmStatus oldFarmStatus = farm != null ? farm.getStatus() : null;
        if (farm != null) {
            if (currentlyActive) {
                farm.setStatus(FarmStatus.BLOCKED);
            } else if (farm.getStatus() == FarmStatus.BLOCKED) {
                farm.setStatus(FarmStatus.ACTIVE);
            }
            farmRepository.save(farm);
        }
        if (currentlyActive) {
            refreshTokenService.revokeAll(boss.getId());
        }

        Map<String, Object> oldV = new LinkedHashMap<>();
        oldV.put("status", oldStatus);
        oldV.put("farmStatus", oldFarmStatus);
        Map<String, Object> newV = new LinkedHashMap<>();
        newV.put("status", boss.getStatus());
        newV.put("farmStatus", farm != null ? farm.getStatus() : null);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("farmId", farm != null ? farm.getId() : null);
        auditService.record(AuditAction.BOSS_STATUS_CHANGED, "BOSS", boss.getId(), oldV, newV, extra);

        return mapToBossItem(boss, farm);
    }

    /**
     * Boss ma'lumotlarini tahrirlash. farmId o'zgarsa: ferma qatori SELECT … FOR UPDATE bilan qulflanadi;
     * ferma yo'q → 404 FARM_NOT_FOUND; fermada boshqa boss bor → 409 FARM_ALREADY_HAS_BOSS.
     * Audit BOSS_UPDATED (old/new, parolsiz).
     */
    @Transactional
    public BossItemResponse updateBoss(Long bossId, BossUpdateRequest request) {
        User boss = findBoss(bossId);
        Farm currentFarm = farmRepository.findByBossUserId(boss.getId()).orElse(null);

        Map<String, Object> oldV = bossSnapshot(boss, currentFarm);
        boolean passwordChanged = false;

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            boss.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = normalizePhoneOrFail(request.getPhone(), "phone");
            if (!phone.equals(boss.getPhone())) {
                userRepository.findByPhone(phone).filter(other -> !other.getId().equals(boss.getId())).ifPresent(other -> {
                    throw ApiException.conflict("PHONE_ALREADY_EXISTS", "Bu telefon raqamli foydalanuvchi allaqachon mavjud");
                });
                boss.setPhone(phone);
            }
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            PasswordPolicy.validate(request.getPassword(), "password");
            boss.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            passwordChanged = true;
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            boss.setStatus(request.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getAvatarUrl() != null) {
            boss.setAvatarUrl(FileUrls.normalize(request.getAvatarUrl()));
        }

        Farm targetFarm = currentFarm;
        if (request.getFarmId() != null && (currentFarm == null || !request.getFarmId().equals(currentFarm.getId()))) {
            targetFarm = farmRepository.findByIdForUpdate(request.getFarmId())
                    .orElseThrow(() -> ApiException.notFound("FARM_NOT_FOUND", "Ferma topilmadi: ID=" + request.getFarmId()));
            if (targetFarm.getBossUserId() != null && !targetFarm.getBossUserId().equals(boss.getId())) {
                throw ApiException.conflict("FARM_ALREADY_HAS_BOSS", "Bu fermada boshqa boss bor");
            }
            if (currentFarm != null) {
                Farm locked = farmRepository.findByIdForUpdate(currentFarm.getId()).orElse(currentFarm);
                locked.setBossUserId(null);
                farmRepository.saveAndFlush(locked);
            }
            targetFarm.setBossUserId(boss.getId());
            farmRepository.saveAndFlush(targetFarm);
            boss.setFarmId(targetFarm.getId()); // hosila nusxa
        }

        userRepository.save(boss);
        if (passwordChanged || "BLOCKED".equalsIgnoreCase(boss.getStatus())) {
            refreshTokenService.revokeAll(boss.getId());
        }

        Map<String, Object> newV = bossSnapshot(boss, targetFarm);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("passwordChanged", passwordChanged);
        auditService.record(AuditAction.BOSS_UPDATED, "BOSS", boss.getId(), oldV, newV, extra);

        return mapToBossItem(boss, targetFarm);
    }

    /** Soft delete: hisob DELETED, ferma bog'lanishi uziladi, tokenlar bekor qilinadi. Audit BOSS_DELETED. */
    @Transactional
    public void deleteBoss(Long bossId) {
        User boss = findBoss(bossId);
        Farm farm = farmRepository.findByBossUserId(boss.getId())
                .flatMap(f -> farmRepository.findByIdForUpdate(f.getId()))
                .orElse(null);
        Map<String, Object> oldV = bossSnapshot(boss, farm);

        if (farm != null) {
            farm.setBossUserId(null);
            farmRepository.save(farm);
        }
        boss.setStatus("DELETED");
        userRepository.save(boss);
        refreshTokenService.revokeAll(boss.getId());

        Map<String, Object> newV = new LinkedHashMap<>();
        newV.put("status", "DELETED");
        newV.put("farmId", null);
        auditService.record(AuditAction.BOSS_DELETED, "BOSS", boss.getId(), oldV, newV);
    }

    // ==========================================
    // 3. AUDIT LOG (BE-12)
    // ==========================================

    private Specification<SuperAdminAuditLog> buildAuditLogSpec(Collection<String> actions, String entityType, Long actorId,
                                                                 LocalDate from, LocalDate to, String search) {
        List<String> rawActions = actions == null ? List.of() : actions.stream()
                .filter(Objects::nonNull)
                .flatMap(a -> Arrays.stream(a.split(",")))
                .map(String::trim)
                .filter(a -> !a.isEmpty())
                .map(a -> a.toUpperCase(Locale.ROOT))
                .toList();

        List<String> actionList = new ArrayList<>();
        for (String act : rawActions) {
            switch (act) {
                case "LOGIN" -> {
                    actionList.add("AUTH_LOGIN");
                    actionList.add("AUTH_LOGOUT");
                    actionList.add("AUTH_REFRESH");
                    actionList.add("LOGIN");
                }
                case "FERMA_TIZIMI" -> {
                    actionList.add("FARM_CREATED");
                    actionList.add("FARM_UPDATED");
                    actionList.add("FARM_STATUS_CHANGED");
                    actionList.add("FARM_BLOCKED");
                }
                case "STATUS_OZGARTIRISH" -> {
                    actionList.add("FARM_STATUS_CHANGED");
                    actionList.add("BOSS_STATUS_CHANGED");
                    actionList.add("CLIENT_BLOCKED");
                    actionList.add("CLIENT_ACTIVATED");
                }
                case "XAVFSIZLIK" -> {
                    actionList.add("PASSWORD_CHANGED");
                    actionList.add("BOSS_UPDATED");
                    actionList.add("SETTINGS_UPDATED");
                }
                default -> {
                    if (!act.equalsIgnoreCase("ALL") && !act.equalsIgnoreCase("BARCHASI")) {
                        actionList.add(act);
                    }
                }
            }
        }
        actionList.removeIf(a -> a.equalsIgnoreCase("ALL") || a.equalsIgnoreCase("BARCHASI"));

        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!actionList.isEmpty()) ps.add(root.get("action").in(actionList));
            if (entityType != null && !entityType.isBlank() 
                    && !entityType.equalsIgnoreCase("ALL") 
                    && !entityType.equalsIgnoreCase("BARCHASI")) {
                ps.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (actorId != null && actorId > 0) {
                ps.add(cb.equal(root.get("actorId"), actorId));
            }
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TashkentTime.startOf(from)));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), TashkentTime.endExclusive(to)));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Subquery<Long> actors = q.subquery(Long.class);
                var u = actors.from(User.class);
                actors.select(u.get("id")).where(cb.like(cb.lower(u.get("fullName")), like));
                List<Predicate> or = new ArrayList<>(List.of(
                        cb.like(cb.lower(cb.coalesce(root.get("metadata"), "")), like),
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), like),
                        root.get("actorId").in(actors)));
                if (search.trim().matches("\\d{1,18}")) {
                    or.add(cb.equal(root.get("entityId"), Long.parseLong(search.trim())));
                }
                ps.add(cb.or(or.toArray(new Predicate[0])));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public PageResponse<AuditLogItem> getAuditLogs(Collection<String> actions, String entityType, Long actorId, String status,
                                                   LocalDate from, LocalDate to,
                                                   String search, int page, int size, String sort) {
        Pageable pageable = PageResponse.pageable(page, size, sort, AUDIT_SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<SuperAdminAuditLog> spec = buildAuditLogSpec(actions, entityType, actorId, from, to, search);

        Page<SuperAdminAuditLog> result = auditLogRepository.findAll(spec, pageable);
        Set<Long> actorIds = result.getContent().stream().map(SuperAdminAuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(result, log -> AuditLogItem.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorName(names.get(log.getActorId()))
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .metadata(parseMetadata(log.getMetadata()))
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .status("SUCCESS")
                .createdAt(log.getCreatedAt())
                .build());
    }

    public PageResponse<AuditLogItem> getAuditLogs(Collection<String> actions, String entityType, String status,
                                                   LocalDate from, LocalDate to,
                                                   String search, int page, int size, String sort) {
        return getAuditLogs(actions, entityType, null, status, from, to, search, page, size, sort);
    }

    public PageResponse<AuditLogItem> getAuditLogs(Collection<String> actions, String entityType, LocalDate from, LocalDate to,
                                                   String search, int page, int size, String sort) {
        return getAuditLogs(actions, entityType, null, null, from, to, search, page, size, sort);
    }

    public AuditLogStatsResponse getAuditLogStats(String period, LocalDate date, LocalDate startDate, LocalDate endDate,
                                                  LocalDate from, LocalDate to) {
        LocalDate[] range = TashkentTime.resolveRange(period, date, startDate, endDate, from, to);
        Instant start = range[0] != null ? TashkentTime.startOf(range[0]) : null;
        Instant end = range[1] != null ? TashkentTime.endExclusive(range[1]) : null;

        Specification<SuperAdminAuditLog> baseSpec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (start != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            if (end != null) ps.add(cb.lessThan(root.get("createdAt"), end));
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };

        Specification<SuperAdminAuditLog> farmSpec = baseSpec.and((root, q, cb) ->
                cb.or(
                        cb.equal(cb.upper(root.get("entityType")), "FARM"),
                        cb.like(cb.upper(root.get("action")), "FARM_%")
                )
        );

        Specification<SuperAdminAuditLog> userSpec = baseSpec.and((root, q, cb) ->
                cb.or(
                        cb.upper(root.get("entityType")).in("BOSS", "USER", "CLIENT"),
                        cb.like(cb.upper(root.get("action")), "BOSS_%"),
                        cb.like(cb.upper(root.get("action")), "CLIENT_%")
                )
        );

        long total = auditLogRepository.count(baseSpec);
        long farm = auditLogRepository.count(farmSpec);
        long user = auditLogRepository.count(userSpec);
        long system = Math.max(0, total - (farm + user));

        return AuditLogStatsResponse.builder()
                .totalLogs(total)
                .farmActions(farm)
                .userActions(user)
                .systemActions(system)
                .build();
    }

    public AuditLogFiltersResponse getAuditLogFilters() {
        List<FilterOption> actionTypes = new ArrayList<>();
        actionTypes.add(new FilterOption("ALL", "Barchasi"));
        actionTypes.add(new FilterOption("FERMA_TIZIMI", "Ferma amallari"));
        actionTypes.add(new FilterOption("STATUS_OZGARTIRISH", "Status o'zgartirish"));
        actionTypes.add(new FilterOption("XAVFSIZLIK", "Xavfsizlik amallari"));
        actionTypes.add(new FilterOption("LOGIN", "Tizimga kirish"));

        Map<String, String> defaultLabels = new LinkedHashMap<>();
        defaultLabels.put("FARM_CREATED", "Ferma yaratildi");
        defaultLabels.put("FARM_UPDATED", "Ferma yangilandi");
        defaultLabels.put("FARM_BLOCKED", "Ferma bloklandi");
        defaultLabels.put("FARM_STATUS_CHANGED", "Ferma statusi o'zgardi");
        defaultLabels.put("BOSS_CREATED", "Rahbar yaratildi");
        defaultLabels.put("BOSS_UPDATED", "Rahbar yangilandi");
        defaultLabels.put("BOSS_STATUS_CHANGED", "Rahbar statusi o'zgardi");
        defaultLabels.put("BOSS_DELETED", "Rahbar o'chirildi");
        defaultLabels.put("BOSS_REQUEST_APPROVED", "Rahbar so'rovi tasdiqlandi");
        defaultLabels.put("BOSS_REQUEST_REJECTED", "Rahbar so'rovi rad etildi");
        defaultLabels.put("CLIENT_BLOCKED", "Mijoz bloklandi");
        defaultLabels.put("CLIENT_ACTIVATED", "Mijoz faollashtirildi");
        defaultLabels.put("CLIENT_DELETED", "Mijoz o'chirildi");
        defaultLabels.put("PROFILE_UPDATE", "Profil yangilandi");
        defaultLabels.put("PASSWORD_CHANGED", "Parol o'zgartirildi");
        defaultLabels.put("SETTINGS_UPDATED", "Sozlamalar yangilandi");

        Set<String> addedCodes = actionTypes.stream().map(FilterOption::getCode).collect(Collectors.toSet());
        defaultLabels.forEach((code, label) -> {
            if (addedCodes.add(code)) {
                actionTypes.add(new FilterOption(code, label));
            }
        });

        // Add any additional distinct actions from DB
        List<String> dbActions = auditLogRepository.findDistinctActions();
        for (String dbAct : dbActions) {
            if (dbAct != null && !dbAct.isBlank() && addedCodes.add(dbAct)) {
                actionTypes.add(new FilterOption(dbAct, dbAct));
            }
        }

        List<FilterOption> objectTypes = new ArrayList<>();
        objectTypes.add(new FilterOption("ALL", "Barchasi"));
        objectTypes.add(new FilterOption("FARM", "Ferma"));
        objectTypes.add(new FilterOption("BOSS", "Rahbar (Boss)"));
        objectTypes.add(new FilterOption("CLIENT", "Mijoz"));
        objectTypes.add(new FilterOption("SUPERADMIN", "SuperAdmin"));
        objectTypes.add(new FilterOption("SETTINGS", "Sozlamalar"));

        Set<String> addedObjects = objectTypes.stream().map(FilterOption::getCode).collect(Collectors.toSet());
        List<String> dbEntityTypes = auditLogRepository.findDistinctEntityTypes();
        for (String dbEnt : dbEntityTypes) {
            if (dbEnt != null && !dbEnt.isBlank() && addedObjects.add(dbEnt.toUpperCase(Locale.ROOT))) {
                objectTypes.add(new FilterOption(dbEnt.toUpperCase(Locale.ROOT), dbEnt));
            }
        }

        List<SuperAdminOption> superAdmins = userRepository.findAllByRole(Role.SUPER_ADMIN).stream()
                .map(u -> new SuperAdminOption(u.getId(), u.getFullName(), u.getPhone(), u.getRole().name()))
                .toList();

        List<FilterOption> periods = List.of(
                new FilterOption("last_30_days", "So'nggi 30 kun"),
                new FilterOption("last_7_days", "So'nggi 7 kun"),
                new FilterOption("today", "Bugun"),
                new FilterOption("yesterday", "Kecha"),
                new FilterOption("this_week", "Shu hafta"),
                new FilterOption("this_month", "Shu oy"),
                new FilterOption("this_year", "Shu yil"),
                new FilterOption("all", "Barchasi")
        );

        return AuditLogFiltersResponse.builder()
                .actionTypes(actionTypes)
                .objectTypes(objectTypes)
                .superAdmins(superAdmins)
                .periods(periods)
                .build();
    }

    public byte[] exportAuditLogsToCsv(Collection<String> actions, String entityType, Long actorId, String status,
                                       LocalDate from, LocalDate to, String search) {
        Specification<SuperAdminAuditLog> spec = buildAuditLogSpec(actions, entityType, actorId, from, to, search);
        List<SuperAdminAuditLog> list = auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        Set<Long> actorIds = list.stream().map(SuperAdminAuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM so MS Excel opens CSV with proper character encoding
        sb.append('\uFEFF');
        sb.append("ID,Sana va vaqt,SuperAdmin,Amal turi,Obyekt turi,Obyekt ID,Tavsif va sabab,IP manzil,Status\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(TashkentTime.ZONE);

        for (SuperAdminAuditLog item : list) {
            String timeStr = item.getCreatedAt() != null ? dtf.format(item.getCreatedAt()) : "";
            String actor = names.getOrDefault(item.getActorId(), item.getActorId() != null ? ("#" + item.getActorId()) : "");
            String desc = item.getMetadata() != null ? item.getMetadata() : "";

            sb.append(escapeCsv(String.valueOf(item.getId()))).append(",");
            sb.append(escapeCsv(timeStr)).append(",");
            sb.append(escapeCsv(actor)).append(",");
            sb.append(escapeCsv(item.getAction())).append(",");
            sb.append(escapeCsv(item.getEntityType())).append(",");
            sb.append(escapeCsv(item.getEntityId() != null ? String.valueOf(item.getEntityId()) : "")).append(",");
            sb.append(escapeCsv(desc)).append(",");
            sb.append(escapeCsv(item.getIpAddress() != null ? item.getIpAddress() : "")).append(",");
            sb.append(escapeCsv("SUCCESS")).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    // ==========================================
    // 4. SOZLAMALAR VA PROFIL (BE-21)
    // ==========================================

    public SuperAdminProfileResponse getSuperAdminProfile() {
        return mapToProfileResponse(getCurrentSuperAdminUser());
    }

    @Transactional
    public SuperAdminProfileResponse updateSuperAdminProfile(SuperAdminProfileUpdateRequest request) {
        User user = getCurrentSuperAdminUser();
        Map<String, Object> oldV = profileSnapshot(user);

        if (request.getFirstName() != null || request.getLastName() != null) {
            String first = request.getFirstName() != null ? request.getFirstName().trim() : "";
            String last = request.getLastName() != null ? request.getLastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                user.setFullName(full);
            }
        } else if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = normalizePhoneOrFail(request.getPhone(), "phone");
            userRepository.findByPhone(phone).filter(o -> !o.getId().equals(user.getId())).ifPresent(o -> {
                throw ApiException.conflict("PHONE_ALREADY_EXISTS", "Bu telefon raqamli foydalanuvchi allaqachon mavjud");
            });
            user.setPhone(phone);
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim();
            userRepository.findByEmail(email).filter(o -> !o.getId().equals(user.getId())).ifPresent(o -> {
                throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "Bu email bilan foydalanuvchi allaqachon mavjud");
            });
            user.setEmail(email);
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(FileUrls.normalize(request.getAvatarUrl()));
        }

        User saved = userRepository.save(user);
        auditService.record(AuditAction.PROFILE_UPDATE, "SUPERADMIN", saved.getId(), oldV, profileSnapshot(saved));
        return mapToProfileResponse(saved);
    }

    /**
     * Joriy parol tekshiriladi, newPassword = confirmPassword, yagona siyosat (422), muvaffaqiyatdan keyin
     * barcha refresh tokenlar bekor qilinadi, audit PASSWORD_CHANGED.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentSuperAdminUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw ApiException.unprocessable("CURRENT_PASSWORD_INVALID", "currentPassword", "Joriy parol noto'g'ri kiritildi");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw ApiException.unprocessable("PASSWORD_CONFIRMATION_MISMATCH", "confirmPassword",
                    "Yangi parol va uni tasdiqlash mos kelmadi");
        }
        PasswordPolicy.validate(request.getNewPassword(), "newPassword");
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw ApiException.unprocessable("PASSWORD_UNCHANGED", "newPassword", "Yangi parol joriy paroldan farq qilishi kerak");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        int revoked = refreshTokenService.revokeAll(user.getId());

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("revokedSessions", revoked);
        auditService.record(AuditAction.PASSWORD_CHANGED, "SUPERADMIN", user.getId(), null, null, extra);
    }

    // ==========================================
    // 5. KUNMA-KUN DINAMIKA (BE-01, BE-10)
    // ==========================================

    /**
     * Kunlar bo'yicha: yaratilgan buyurtmalar (created_at), yakunlanganlar va tushum (completed_at, faqat COMPLETED).
     * farmId berilmasa revenue/totalRevenue/orderedAmount qaytarilmaydi (TZ §5.5).
     */
    public DailyDynamicsResponse getDailyDynamics(Integer days, LocalDate startDate, LocalDate endDate, Long farmId) {
        LocalDate end = endDate != null ? endDate : TashkentTime.today();
        int dayCount = days != null ? days : 7;
        LocalDate start = startDate != null ? startDate : end.minusDays(dayCount - 1L);
        Granularity.validateRange(start, end, 366);

        boolean withMoney = farmId != null;
        Map<LocalDate, OrderStatsService.DayCounts> created = orderStatsService.dailyCreated(farmId, start, end);
        Map<LocalDate, RevenueService.Totals> revenue = revenueService.dailyTotals(farmId, start, end);

        List<DailyDynamicsItem> items = new ArrayList<>();
        long totalOrders = 0;
        long totalCompleted = 0;
        long totalCancelled = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalTara = BigDecimal.ZERO;

        for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1)) {
            OrderStatsService.DayCounts c = created.getOrDefault(cur, OrderStatsService.DayCounts.ZERO);
            RevenueService.Totals r = revenue.getOrDefault(cur, RevenueService.Totals.ZERO);
            totalOrders += c.total();
            totalCompleted += r.completedOrders();
            totalCancelled += c.cancelled();
            totalRevenue = totalRevenue.add(r.revenue());
            totalTara = totalTara.add(r.taraRevenue());

            items.add(DailyDynamicsItem.builder()
                    .date(cur.toString())
                    .dayOfWeek(uzbekDayOfWeek(cur.getDayOfWeek()))
                    .totalOrders(c.total())
                    .completedOrders(r.completedOrders())
                    .cancelledOrders(c.cancelled())
                    .orderedAmount(withMoney ? c.orderedAmount() : null)
                    .revenue(withMoney ? r.revenue() : null)
                    .taraRevenue(withMoney ? r.taraRevenue() : null)
                    .build());
        }

        return DailyDynamicsResponse.builder()
                .farmId(farmId)
                .startDate(start.toString())
                .endDate(end.toString())
                .totalOrders(totalOrders)
                .completedOrders(totalCompleted)
                .cancelledOrders(totalCancelled)
                .totalRevenue(withMoney ? totalRevenue : null)
                .totalTaraRevenue(withMoney ? totalTara : null)
                .days(items)
                .build();
    }

    // ==========================================
    // Yordamchilar
    // ==========================================

    private User findBoss(Long bossId) {
        User boss = userRepository.findById(bossId)
                .orElseThrow(() -> ApiException.notFound("BOSS_NOT_FOUND", "Rahbar topilmadi: ID=" + bossId));
        if (boss.getRole() != Role.BOSS || "DELETED".equalsIgnoreCase(boss.getStatus())) {
            throw ApiException.notFound("BOSS_NOT_FOUND", "Rahbar topilmadi: ID=" + bossId);
        }
        return boss;
    }

    private BossItemResponse mapToBossItem(User boss, Farm farm) {
        boolean isActive = "ACTIVE".equalsIgnoreCase(boss.getStatus());
        return BossItemResponse.builder()
                .id(boss.getId())
                .fullName(boss.getFullName())
                .phone(boss.getPhone())
                .avatarUrl(FileUrls.normalize(boss.getAvatarUrl()))
                .farmId(farm != null ? farm.getId() : null)
                .farmCode(farm != null ? farmCode(farm) : null)
                .farmName(farm != null ? farm.getName() : null)
                .status(boss.getStatus())
                .active(isActive)
                .statusLabel(isActive ? "Faol" : "Bloklangan")
                .activationStatus(effectiveActivation(boss))
                .createdAt(boss.getCreatedAt())
                .build();
    }

    private static BossActivationStatus effectiveActivation(User u) {
        return u.getActivationStatus() != null ? u.getActivationStatus() : BossActivationStatus.NOT_REQUESTED;
    }

    private static String farmCode(Farm f) {
        return String.format("farm-%03d", f.getId());
    }

    private static Map<String, Object> bossSnapshot(User boss, Farm farm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", boss.getFullName());
        m.put("phone", boss.getPhone());
        m.put("status", boss.getStatus());
        m.put("avatarUrl", boss.getAvatarUrl());
        m.put("farmId", farm != null ? farm.getId() : null);
        return m;
    }

    private static Map<String, Object> profileSnapshot(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", u.getFullName());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        m.put("avatarUrl", u.getAvatarUrl());
        return m;
    }

    private static String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "ALL", "BARCHA" -> null;
            case "ACTIVE", "FAOL" -> "ACTIVE";
            case "BLOCKED", "BLOKLANGAN" -> "BLOCKED";
            default -> throw ApiException.badRequest("INVALID_PARAMETER", "'status' faqat ACTIVE yoki BLOCKED bo'lishi mumkin");
        };
    }

    private static Comparator<User> bossComparator(Sort sort) {
        Sort.Order order = sort.iterator().hasNext() ? sort.iterator().next() : Sort.Order.desc("createdAt");
        Comparator<User> c = switch (order.getProperty()) {
            case "fullName" -> Comparator.comparing(u -> u.getFullName() == null ? "" : u.getFullName().toLowerCase(Locale.ROOT));
            case "id" -> Comparator.comparing(User::getId);
            default -> Comparator.comparing(User::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        return order.isAscending() ? c : c.reversed();
    }

    private JsonNode parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            return objectMapper.readTree(metadata);
        } catch (Exception e) {
            // Eski yozuvlar oddiy matn bo'lishi mumkin
            return objectMapper.getNodeFactory().textNode(metadata);
        }
    }

    private User getCurrentSuperAdminUser() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Avtorizatsiyadan o'tilmagan");
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "SuperAdmin foydalanuvchisi topilmadi"));
        if (user.getRole() != Role.SUPER_ADMIN) {
            throw ApiException.forbidden("FORBIDDEN", "Bu amal faqat SuperAdmin uchun");
        }
        return user;
    }

    private SuperAdminProfileResponse mapToProfileResponse(User user) {
        String fullName = user.getFullName() != null ? user.getFullName() : "";
        String firstName = fullName;
        String lastName = "";
        int spaceIdx = fullName.indexOf(' ');
        if (spaceIdx > 0) {
            firstName = fullName.substring(0, spaceIdx);
            lastName = fullName.substring(spaceIdx + 1);
        }
        return SuperAdminProfileResponse.builder()
                .id(user.getId())
                .firstName(firstName)
                .lastName(lastName)
                .fullName(fullName)
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatarUrl(FileUrls.normalize(user.getAvatarUrl()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static String normalizePhoneOrFail(String raw, String field) {
        String p = OtpService.normalizePhone(raw);
        if (!p.matches("^\\+998\\d{9}$")) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PHONE",
                    "Telefon formati noto'g'ri. Masalan: +998901234567",
                    List.of(new uz.gidrogo.common.ApiResponse.FieldErrorItem(field, "Telefon formati noto'g'ri")), null);
        }
        return p;
    }

    private long countUsers(Role role) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = :role AND status <> 'DELETED'",
                new MapSqlParameterSource("role", role.name()), Long.class);
    }

    private long countUsersCreated(Role role, Instant from, Instant to) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = :role AND created_at >= :from AND created_at < :to",
                new MapSqlParameterSource("role", role.name())
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)), Long.class);
    }

    private long countFarmsCreated(Instant from, Instant to) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM farms WHERE created_at >= :from AND created_at < :to",
                new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)), Long.class);
    }

    private long countActiveClients(Instant from, Instant to) {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT client_id) FROM orders WHERE created_at >= :from AND created_at < :to",
                new MapSqlParameterSource("from", Timestamp.from(from)).addValue("to", Timestamp.from(to)), Long.class);
    }

    private static String bucket(String unit, String column) {
        return "CAST(date_trunc('" + unit + "', " + column + " AT TIME ZONE 'Asia/Tashkent') AS date)";
    }

    private Map<LocalDate, Long> bucketCounts(String sql, MapSqlParameterSource p) {
        Map<LocalDate, Long> map = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            map.put(rs.getDate("b").toLocalDate(), rs.getLong("c"));
        });
        return map;
    }

    private static String uzbekDayOfWeek(java.time.DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Dushanba";
            case TUESDAY -> "Seshanba";
            case WEDNESDAY -> "Chorshanba";
            case THURSDAY -> "Payshanba";
            case FRIDAY -> "Juma";
            case SATURDAY -> "Shanba";
            case SUNDAY -> "Yakshanba";
        };
    }
}
