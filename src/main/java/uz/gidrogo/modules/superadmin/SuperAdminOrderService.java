package uz.gidrogo.modules.superadmin;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.common.TashkentTime;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.PaymentMethod;
import uz.gidrogo.modules.superadmin.dto.SuperAdminCrmDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SuperAdmin Buyurtmalar API (BE-09, TZ §10) — faqat o'qish.
 * Filtrlar birgalikda (AND) ishlaydi; from/to — createdAt bo'yicha, Asia/Tashkent kunlari.
 * Mahsulot productId bo'yicha filtrlanadi (nom bo'yicha emas).
 */
@Service
@RequiredArgsConstructor
public class SuperAdminOrderService {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "totalSum", "id", "status");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "o.created_at", "totalSum", "o.total_sum", "id", "o.id", "status", "o.status");

    private final NamedParameterJdbcTemplate jdbc;

    @Builder
    public record OrderFilter(Long farmId, List<OrderStatus> statuses, PaymentMethod paymentType, Long productId,
                              LocalDate from, LocalDate to, String search, Long clientId) {}

    public PageResponse<OrderListItem> getOrders(OrderFilter filter, int page, int size, String sort) {
        Pageable pageable = PageResponse.pageable(page, size, sort, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
        Sort.Order order = pageable.getSort().iterator().next();
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "'from' sanasi 'to' sanasidan keyin bo'lishi mumkin emas");
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (filter.farmId() != null) {
            where.append(" AND o.farm_id = :farmId");
            p.addValue("farmId", filter.farmId());
        }
        if (filter.clientId() != null) {
            where.append(" AND o.client_id = :clientId");
            p.addValue("clientId", filter.clientId());
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            where.append(" AND o.status IN (:statuses)");
            p.addValue("statuses", filter.statuses().stream().map(Enum::name).toList());
        }
        if (filter.paymentType() != null) {
            where.append(" AND o.payment_method = :paymentType");
            p.addValue("paymentType", filter.paymentType().name());
        }
        if (filter.productId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = :productId)");
            p.addValue("productId", filter.productId());
        }
        if (filter.from() != null) {
            where.append(" AND o.created_at >= :from");
            p.addValue("from", Timestamp.from(TashkentTime.startOf(filter.from())));
        }
        if (filter.to() != null) {
            where.append(" AND o.created_at < :to");
            p.addValue("to", Timestamp.from(TashkentTime.endExclusive(filter.to())));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String s = filter.search().trim().toLowerCase(Locale.ROOT);
            String digits = filter.search().replaceAll("[^0-9]", "");
            where.append(" AND (LOWER(o.order_number) LIKE :s OR LOWER(u.full_name) LIKE :s");
            p.addValue("s", "%" + s + "%");
            if (digits.length() >= 3) {
                where.append(" OR regexp_replace(COALESCE(u.phone, ''), '[^0-9]', '', 'g') LIKE :digits");
                p.addValue("digits", "%" + digits + "%");
            }
            where.append(")");
        }

        String from = " FROM orders o LEFT JOIN clients c ON c.id = o.client_id LEFT JOIN users u ON u.id = c.user_id "
                + "LEFT JOIN farms f ON f.id = o.farm_id";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, p, Long.class);

        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        String sql = "SELECT o.id, o.order_number, o.client_id, u.full_name AS client_name, o.farm_id, f.name AS farm_name, "
                + "o.total_sum, o.payment_method, o.status, o.created_at, o.latitude, o.longitude, o.delivery_address"
                + from + where + " ORDER BY " + SORT_COLUMNS.get(order.getProperty())
                + (order.isAscending() ? " ASC" : " DESC") + ", o.id DESC LIMIT :limit OFFSET :offset";
        List<OrderListItem> content = jdbc.query(sql, p, (rs, i) -> OrderListItem.builder()
                .id(rs.getLong("id"))
                .orderNumber(rs.getString("order_number"))
                .client(PersonRef.builder().id(rs.getLong("client_id")).fullName(rs.getString("client_name")).build())
                .farm(Ref.builder().id(rs.getLong("farm_id")).name(rs.getString("farm_name")).build())
                .totalSum(rs.getBigDecimal("total_sum"))
                .paymentType(rs.getString("payment_method"))
                .status(rs.getString("status"))
                .createdAt(instant(rs, "created_at"))
                .delivery(DeliveryInfo.builder()
                        .lat(rs.getBigDecimal("latitude"))
                        .lon(rs.getBigDecimal("longitude"))
                        .address(rs.getString("delivery_address"))
                        .build())
                .build());

        Map<Long, List<OrderItemLine>> items = loadItems(content.stream().map(OrderListItem::getId).toList());
        content.forEach(o -> o.setItems(items.getOrDefault(o.getId(), List.of())));

        return PageResponse.<OrderListItem>builder()
                .content(content)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(total)
                .totalPages((int) Math.ceil(total / (double) pageable.getPageSize()))
                .build();
    }

    public OrderDetail getOrder(Long orderId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", orderId);
        List<OrderDetail> found = jdbc.query("""
                SELECT o.*, u.full_name AS client_name, u.phone AS client_phone, f.name AS farm_name,
                       cu.full_name AS courier_name
                FROM orders o
                LEFT JOIN clients c ON c.id = o.client_id
                LEFT JOIN users u ON u.id = c.user_id
                LEFT JOIN farms f ON f.id = o.farm_id
                LEFT JOIN users cu ON cu.id = o.courier_id
                WHERE o.id = :id
                """, p, (rs, i) -> {
            Long courierId = getLong(rs, "courier_id");
            Integer taraFinal = getInt(rs, "tara_qty_final");
            return OrderDetail.builder()
                    .id(rs.getLong("id"))
                    .orderNumber(rs.getString("order_number"))
                    .client(PersonRef.builder().id(rs.getLong("client_id")).fullName(rs.getString("client_name")).build())
                    .clientPhone(rs.getString("client_phone"))
                    .farm(Ref.builder().id(rs.getLong("farm_id")).name(rs.getString("farm_name")).build())
                    .productsSum(rs.getBigDecimal("products_sum"))
                    .totalSum(rs.getBigDecimal("total_sum"))
                    .paymentType(rs.getString("payment_method"))
                    .paymentStatus(rs.getString("payment_status"))
                    .status(rs.getString("status"))
                    .createdAt(instant(rs, "created_at"))
                    .delivery(DeliveryInfo.builder()
                            .lat(rs.getBigDecimal("latitude"))
                            .lon(rs.getBigDecimal("longitude"))
                            .address(rs.getString("delivery_address"))
                            .build())
                    .courier(courierId == null ? null : PersonRef.builder().id(courierId).fullName(rs.getString("courier_name")).build())
                    .assignedAt(instant(rs, "assigned_at"))
                    .deliveredAt(instant(rs, "delivered_at"))
                    .completedAt(instant(rs, "completed_at"))
                    .tara(TaraInfo.builder()
                            .fullGiven(taraFinal != null ? taraFinal : getInt(rs, "tara_qty_estimated"))
                            .emptyReturned(getInt(rs, "empty_bottles_returned"))
                            .taraSum(rs.getBigDecimal("tara_sum"))
                            .build())
                    .cancelReason(null)
                    .build();
        });
        if (found.isEmpty()) {
            throw ApiException.notFound("ORDER_NOT_FOUND", "Buyurtma topilmadi");
        }
        OrderDetail detail = found.get(0);
        detail.setItems(loadItems(List.of(orderId)).getOrDefault(orderId, List.of()));
        detail.setStatusHistory(jdbc.query("""
                SELECT h.from_status, h.to_status, h.changed_at, h.changed_by, u.role
                FROM order_status_history h LEFT JOIN users u ON u.id = h.changed_by
                WHERE h.order_id = :id ORDER BY h.changed_at, h.id
                """, p, (rs, i) -> StatusHistoryItem.builder()
                .fromStatus(rs.getString("from_status"))
                .status(rs.getString("to_status"))
                .at(instant(rs, "changed_at"))
                .actorId(getLong(rs, "changed_by"))
                .actorRole(rs.getString("role") != null ? rs.getString("role") : "SYSTEM")
                .build()));
        return detail;
    }

    private Map<Long, List<OrderItemLine>> loadItems(List<Long> orderIds) {
        Map<Long, List<OrderItemLine>> map = new HashMap<>();
        if (orderIds.isEmpty()) return map;
        jdbc.query("""
                SELECT oi.order_id, oi.product_id, pr.name, oi.quantity, oi.unit_price
                FROM order_items oi LEFT JOIN products pr ON pr.id = oi.product_id
                WHERE oi.order_id IN (:ids) ORDER BY oi.id
                """, new MapSqlParameterSource("ids", orderIds), rs -> {
            map.computeIfAbsent(rs.getLong("order_id"), k -> new ArrayList<>()).add(OrderItemLine.builder()
                    .productId(rs.getLong("product_id"))
                    .productName(rs.getString("name"))
                    .quantity(rs.getBigDecimal("quantity"))
                    .unitPrice(rs.getBigDecimal("unit_price"))
                    .build());
        });
        return map;
    }

    private static Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }
}
