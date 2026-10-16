package uz.gidrogo.modules.superadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.Granularity;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.common.TashkentTime;
import uz.gidrogo.modules.auth.RefreshTokenService;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.order.OrderStatsService;
import uz.gidrogo.modules.superadmin.dto.SuperAdminCrmDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SuperAdmin Mijozlar API (BE-08, TZ §9). Mijoz holati — users.status (ACTIVE | BLOCKED | DELETED).
 * O'chirish faqat soft delete; buyurtmalar, cash_ledger va bottle_ledger hech qachon o'chirilmaydi.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminClientService {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "fullName", "ordersCount", "lastOrderAt", "id");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "c.created_at",
            "fullName", "LOWER(u.full_name)",
            "ordersCount", "ordersCount",
            "lastOrderAt", "lastOrderAt",
            "id", "c.id");

    private final NamedParameterJdbcTemplate jdbc;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;

    public ClientSummary getSummary(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : TashkentTime.today();
        LocalDate start = from != null ? from : end.minusDays(29);
        Granularity.validateRange(start, end, 3 * 366);
        long length = ChronoUnit.DAYS.between(start, end) + 1;
        Instant curFrom = TashkentTime.startOf(start);
        Instant curTo = TashkentTime.endExclusive(end);
        Instant prevFrom = TashkentTime.startOf(start.minusDays(length));
        Instant activeFrom = TashkentTime.startOf(end.minusDays(29));

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("curFrom", Timestamp.from(curFrom))
                .addValue("curTo", Timestamp.from(curTo))
                .addValue("prevFrom", Timestamp.from(prevFrom))
                .addValue("activeFrom", Timestamp.from(activeFrom));
        return jdbc.queryForObject("""
                SELECT
                  COUNT(*) FILTER (WHERE u.status <> 'DELETED') AS total,
                  COUNT(*) FILTER (WHERE u.status = 'BLOCKED') AS blocked,
                  COUNT(*) FILTER (WHERE c.created_at >= :curFrom AND c.created_at < :curTo) AS new_cur,
                  COUNT(*) FILTER (WHERE c.created_at >= :prevFrom AND c.created_at < :curFrom) AS new_prev,
                  COUNT(*) FILTER (WHERE u.status <> 'DELETED' AND EXISTS (
                      SELECT 1 FROM orders o WHERE o.client_id = c.id
                        AND o.created_at >= :activeFrom AND o.created_at < :curTo)) AS active
                FROM clients c JOIN users u ON u.id = c.user_id
                """, p, (rs, i) -> ClientSummary.builder()
                .from(start.toString())
                .to(end.toString())
                .total(rs.getLong("total"))
                .newInPeriod(rs.getLong("new_cur"))
                .active(rs.getLong("active"))
                .blocked(rs.getLong("blocked"))
                .growthPercent(TashkentTime.growthPercent(rs.getLong("new_cur"), rs.getLong("new_prev")))
                .build());
    }

    public PageResponse<ClientListItem> getClients(String search, String status, Long farmId, int page, int size, String sort) {
        Pageable pageable = PageResponse.pageable(page, size, sort, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
        Sort.Order order = pageable.getSort().iterator().next();

        StringBuilder where = new StringBuilder(" WHERE u.status <> 'DELETED'");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (status != null && !status.isBlank()) {
            String st = status.trim().toUpperCase(Locale.ROOT);
            if (!st.equals("ACTIVE") && !st.equals("BLOCKED")) {
                throw ApiException.badRequest("INVALID_PARAMETER", "'status' faqat ACTIVE yoki BLOCKED bo'lishi mumkin");
            }
            where.append(" AND u.status = :status");
            p.addValue("status", st);
        }
        if (farmId != null) {
            where.append(" AND c.farm_id = :farmId");
            p.addValue("farmId", farmId);
        }
        if (search != null && !search.isBlank()) {
            String digits = search.replaceAll("[^0-9]", "");
            where.append(" AND (LOWER(u.full_name) LIKE :s");
            p.addValue("s", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
            if (digits.length() >= 3) {
                where.append(" OR regexp_replace(COALESCE(u.phone, ''), '[^0-9]', '', 'g') LIKE :digits");
                p.addValue("digits", "%" + digits + "%");
            }
            where.append(")");
        }

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM clients c JOIN users u ON u.id = c.user_id" + where, p, Long.class);

        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        String sql = """
                SELECT c.id, c.user_id, u.full_name, u.phone, u.status, c.farm_id, f.name AS farm_name, c.created_at,
                       COALESCE(os.cnt, 0) AS ordersCount, os.last AS lastOrderAt
                FROM clients c
                JOIN users u ON u.id = c.user_id
                LEFT JOIN farms f ON f.id = c.farm_id
                LEFT JOIN LATERAL (SELECT COUNT(*) AS cnt, MAX(o.created_at) AS last FROM orders o WHERE o.client_id = c.id) os ON true
                """ + where + " ORDER BY " + SORT_COLUMNS.get(order.getProperty()) + (order.isAscending() ? " ASC" : " DESC")
                + " NULLS LAST, c.id DESC LIMIT :limit OFFSET :offset";
        List<ClientListItem> content = jdbc.query(sql, p, (rs, i) -> ClientListItem.builder()
                .id(rs.getLong("id"))
                .userId(rs.getLong("user_id"))
                .fullName(rs.getString("full_name"))
                .phone(rs.getString("phone"))
                .status(rs.getString("status"))
                .farmId(getLong(rs, "farm_id"))
                .farmName(rs.getString("farm_name"))
                .createdAt(instant(rs, "created_at"))
                .ordersCount(rs.getLong("ordersCount"))
                .lastOrderAt(instant(rs, "lastOrderAt"))
                .build());

        return PageResponse.<ClientListItem>builder()
                .content(content)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(total)
                .totalPages((int) Math.ceil(total / (double) pageable.getPageSize()))
                .build();
    }

    public ClientDetail getClient(Long clientId) {
        Client client = findClient(clientId);
        User user = userRepository.findById(client.getUserId())
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "Mijoz topilmadi"));
        MapSqlParameterSource p = new MapSqlParameterSource("clientId", clientId)
                .addValue("active", OrderStatsService.ACTIVE_STATUSES.stream().map(Enum::name).toList());

        Map<String, Object> stats = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                       COUNT(*) FILTER (WHERE status IN (:active)) AS active,
                       MAX(created_at) AS last
                FROM orders WHERE client_id = :clientId
                """, p);
        long addresses = jdbc.queryForObject("SELECT COUNT(*) FROM client_addresses WHERE client_id = :clientId", p, Long.class);
        List<BottleBalanceItem> balances = jdbc.query("""
                SELECT b.farm_id, f.name AS farm_name, b.product_id, pr.name AS product_name, b.balance
                FROM client_bottle_balances b
                LEFT JOIN farms f ON f.id = b.farm_id
                LEFT JOIN products pr ON pr.id = b.product_id
                WHERE b.client_id = :clientId ORDER BY b.farm_id, b.product_id
                """, p, (rs, i) -> BottleBalanceItem.builder()
                .farmId(rs.getLong("farm_id"))
                .farmName(rs.getString("farm_name"))
                .productId(rs.getLong("product_id"))
                .productName(rs.getString("product_name"))
                .balance(rs.getInt("balance"))
                .build());
        String farmName = client.getFarmId() == null ? null : jdbc.query("SELECT name FROM farms WHERE id = :id",
                new MapSqlParameterSource("id", client.getFarmId()), rs -> rs.next() ? rs.getString(1) : null);

        Object last = stats.get("last");
        return ClientDetail.builder()
                .id(client.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .farmId(client.getFarmId())
                .farmName(farmName)
                .createdAt(client.getCreatedAt())
                .addressesCount(addresses)
                .ordersCount(((Number) stats.get("total")).longValue())
                .completedOrders(((Number) stats.get("completed")).longValue())
                .activeOrders(((Number) stats.get("active")).longValue())
                .lastOrderAt(last instanceof Timestamp ts ? ts.toInstant() : last instanceof java.time.OffsetDateTime odt ? odt.toInstant() : null)
                .bottleBalances(balances)
                .totalBottleBalance(balances.stream().mapToInt(BottleBalanceItem::getBalance).sum())
                .build();
    }

    /** Bloklash/faollashtirish. Bloklanganda refresh tokenlar bekor qilinadi; login va checkout → 403 CLIENT_BLOCKED. */
    @Transactional
    public ClientDetail updateStatus(Long clientId, ClientStatusUpdateRequest request) {
        Client client = findClient(clientId);
        User user = lockUser(client.getUserId());
        String oldStatus = user.getStatus();
        String newStatus = request.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!newStatus.equals(oldStatus)) {
            user.setStatus(newStatus);
            userRepository.save(user);
            if ("BLOCKED".equals(newStatus)) {
                refreshTokenService.revokeAll(user.getId());
            }
            Map<String, Object> extra = new LinkedHashMap<>();
            if (request.getReason() != null && !request.getReason().isBlank()) extra.put("reason", request.getReason().trim());
            auditService.record("BLOCKED".equals(newStatus) ? AuditAction.CLIENT_BLOCKED : AuditAction.CLIENT_ACTIVATED,
                    "CLIENT", client.getId(), Map.of("status", oldStatus), Map.of("status", newStatus), extra);
        }
        return getClient(clientId);
    }

    /**
     * Faqat soft delete. Faol buyurtma bo'lsa → 409 CLIENT_HAS_ACTIVE_ORDERS;
     * idish balansi > 0 bo'lsa → 409 CLIENT_HAS_BOTTLE_BALANCE (Q9 tavsiyasi). Audit CLIENT_DELETED.
     */
    @Transactional
    public void deleteClient(Long clientId) {
        Client client = findClient(clientId);
        User user = lockUser(client.getUserId());

        MapSqlParameterSource p = new MapSqlParameterSource("clientId", clientId)
                .addValue("active", OrderStatsService.ACTIVE_STATUSES.stream().map(Enum::name).toList());
        long activeOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE client_id = :clientId AND status IN (:active)", p, Long.class);
        if (activeOrders > 0) {
            throw ApiException.conflict("CLIENT_HAS_ACTIVE_ORDERS", "Mijozda " + activeOrders + " ta faol buyurtma bor");
        }
        long bottles = jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance), 0) FROM client_bottle_balances WHERE client_id = :clientId", p, Long.class);
        if (bottles > 0) {
            throw ApiException.conflict("CLIENT_HAS_BOTTLE_BALANCE", "Mijozda " + bottles + " ta qaytarilmagan idish bor");
        }

        String oldStatus = user.getStatus();
        user.setStatus("DELETED");
        userRepository.save(user);
        refreshTokenService.revokeAll(user.getId());
        auditService.record(AuditAction.CLIENT_DELETED, "CLIENT", client.getId(),
                Map.of("status", oldStatus), Map.of("status", "DELETED"));
    }

    public PageResponse<ClientPaymentItem> getPayments(Long clientId, int page, int size) {
        findClient(clientId);
        Pageable pageable = PageResponse.pageable(page, size, null, Set.of(), Sort.unsorted());
        MapSqlParameterSource p = new MapSqlParameterSource("clientId", clientId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        String where = " WHERE o.client_id = :clientId AND o.payment_status IN ('PAID', 'CASH_COLLECTED')";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders o" + where, p, Long.class);
        List<ClientPaymentItem> content = jdbc.query("""
                SELECT o.id, o.order_number, o.farm_id, f.name AS farm_name, o.payment_method, o.payment_status,
                       COALESCE(o.products_sum, o.total_sum - COALESCE(o.tara_sum, 0)) AS amount,
                       COALESCE(o.tara_sum, 0) AS tara_sum, o.total_sum, COALESCE(o.completed_at, o.created_at) AS paid_at
                FROM orders o LEFT JOIN farms f ON f.id = o.farm_id
                """ + where + " ORDER BY paid_at DESC, o.id DESC LIMIT :limit OFFSET :offset", p,
                (rs, i) -> ClientPaymentItem.builder()
                        .orderId(rs.getLong("id"))
                        .orderNumber(rs.getString("order_number"))
                        .farmId(rs.getLong("farm_id"))
                        .farmName(rs.getString("farm_name"))
                        .paymentType(rs.getString("payment_method"))
                        .paymentStatus(rs.getString("payment_status"))
                        .amount(rs.getBigDecimal("amount"))
                        .taraSum(rs.getBigDecimal("tara_sum"))
                        .totalSum(rs.getBigDecimal("total_sum"))
                        .paidAt(instant(rs, "paid_at"))
                        .build());
        return PageResponse.<ClientPaymentItem>builder()
                .content(content)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(total)
                .totalPages((int) Math.ceil(total / (double) pageable.getPageSize()))
                .build();
    }

    private Client findClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "Mijoz topilmadi"));
        User user = userRepository.findById(client.getUserId()).orElse(null);
        if (user == null || "DELETED".equalsIgnoreCase(user.getStatus())) {
            throw ApiException.notFound("CLIENT_NOT_FOUND", "Mijoz topilmadi");
        }
        return client;
    }

    private User lockUser(Long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = :id FOR UPDATE", new MapSqlParameterSource("id", userId), Long.class);
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "Mijoz topilmadi"));
    }

    private static Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }
}
