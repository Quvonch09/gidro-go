package uz.gidrogo.modules.order;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import uz.gidrogo.common.TashkentTime;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buyurtma sonlari bo'yicha statistika (ta'riflar — "Atamalar" bo'limi):
 * <ul>
 *   <li>Faol — yakuniy holatda bo'lmagan: {@link #ACTIVE_STATUSES}.</li>
 *   <li>Yakunlangan — COMPLETED; Bekor qilingan — CANCELLED; RETURNED alohida.</li>
 * </ul>
 * Sanalar {@code created_at} bo'yicha (Asia/Tashkent kuni). Tushum bu yerda emas — {@code RevenueService}.
 * Diqqat: zaxira band qilish hisobidagi "faol" ro'yxati boshqa (ASSIGNED, ON_THE_WAY, NEARBY, PROBLEM) — u o'zgartirilmagan.
 */
@Service
@RequiredArgsConstructor
public class OrderStatsService {

    /** NEW va CONFIRMED ham yakuniy emas, shuning uchun faol hisoblanadi (active+completed+cancelled+returned = total). */
    public static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.NEW, OrderStatus.CONFIRMED, OrderStatus.SEARCHING, OrderStatus.PREPARING,
            OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY, OrderStatus.DELIVERED,
            OrderStatus.PROBLEM);

    private static final String TASHKENT_DAY = "CAST((o.created_at AT TIME ZONE 'Asia/Tashkent') AS date)";

    private final NamedParameterJdbcTemplate jdbc;

    public record DayCounts(long total, long cancelled, long returned, BigDecimal orderedAmount) {
        public static final DayCounts ZERO = new DayCounts(0, 0, 0, BigDecimal.ZERO);
    }

    public record StatusCounts(Map<OrderStatus, Long> byStatus) {
        public long get(OrderStatus s) {
            return byStatus.getOrDefault(s, 0L);
        }

        public long total() {
            return byStatus.values().stream().mapToLong(Long::longValue).sum();
        }

        public long active() {
            return ACTIVE_STATUSES.stream().mapToLong(this::get).sum();
        }

        public long completed() {
            return get(OrderStatus.COMPLETED);
        }

        public long cancelled() {
            return get(OrderStatus.CANCELLED);
        }

        public long returned() {
            return get(OrderStatus.RETURNED);
        }
    }

    /** created_at kuni bo'yicha: jami, bekor qilingan, qaytarilgan va buyurtma qilingan summa (bekor qilinganlarsiz). */
    public Map<LocalDate, DayCounts> dailyCreated(Long farmId, LocalDate start, LocalDate end) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(TashkentTime.startOf(start)))
                .addValue("to", Timestamp.from(TashkentTime.endExclusive(end)));
        if (farmId != null) p.addValue("farmId", farmId);
        String sql = "SELECT " + TASHKENT_DAY + " AS d, COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE o.status = 'CANCELLED') AS cancelled, "
                + "COUNT(*) FILTER (WHERE o.status = 'RETURNED') AS returned, "
                + "COALESCE(SUM(o.total_sum) FILTER (WHERE o.status <> 'CANCELLED'), 0) AS ordered "
                + "FROM orders o WHERE o.created_at >= :from AND o.created_at < :to"
                + (farmId != null ? " AND o.farm_id = :farmId" : "") + " GROUP BY d";
        Map<LocalDate, DayCounts> result = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            result.put(rs.getDate("d").toLocalDate(), new DayCounts(
                    rs.getLong("total"), rs.getLong("cancelled"), rs.getLong("returned"), rs.getBigDecimal("ordered")));
        });
        return result;
    }

    /** Holatlar bo'yicha son. from/to — created_at bo'yicha (null bo'lsa chegarasiz). farmId null — barcha fermalar. */
    public StatusCounts countByStatus(Long farmId, Instant from, Instant to) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT o.status, COUNT(*) AS cnt FROM orders o WHERE 1=1");
        if (farmId != null) {
            sql.append(" AND o.farm_id = :farmId");
            p.addValue("farmId", farmId);
        }
        if (from != null) {
            sql.append(" AND o.created_at >= :from");
            p.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND o.created_at < :to");
            p.addValue("to", Timestamp.from(to));
        }
        sql.append(" GROUP BY o.status");
        Map<OrderStatus, Long> map = new EnumMap<>(OrderStatus.class);
        jdbc.query(sql.toString(), p, rs -> {
            try {
                map.put(OrderStatus.valueOf(rs.getString("status")), rs.getLong("cnt"));
            } catch (IllegalArgumentException ignored) {
                // Enum'da yo'q eski holat — sanalmaydi
            }
        });
        return new StatusCounts(map);
    }
}
