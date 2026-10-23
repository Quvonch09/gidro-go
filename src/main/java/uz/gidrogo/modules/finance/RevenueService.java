package uz.gidrogo.modules.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import uz.gidrogo.common.TashkentTime;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Tushum, xarajat va foydani hisoblaydigan YAGONA servis (BE-01, BE-11).
 * SuperAdmin, Boss va Manager raqamlari shu yerdan olinadi, shuning uchun bir xil chiqadi.
 *
 * <ul>
 *   <li>Tushum faqat {@code status = COMPLETED} buyurtmalardan, {@code completed_at} kuni bo'yicha (Asia/Tashkent).</li>
 *   <li>{@code revenue} — suv/mahsulot sotuvi ({@code products_sum}); {@code taraRevenue} — tara ({@code tara_sum}), alohida qator.</li>
 *   <li>{@code DELIVERED} — tushum emas. {@code CANCELLED} hech qachon qo'shilmaydi.</li>
 *   <li>Xarajat — {@code finance_transactions.type = EXPENSE} (Manager xarajatlari).</li>
 *   <li>Foyda = revenue − expenses. Tara — qaytariladigan garov, foydaga kirmaydi.</li>
 * </ul>
 * Pul faqat BigDecimal / NUMERIC.
 */
@Service
@RequiredArgsConstructor
public class RevenueService {

    /** Eski yozuvlarda products_sum bo'sh bo'lsa: total_sum − tara_sum. */
    private static final String WATER_SUM = "COALESCE(o.products_sum, o.total_sum - COALESCE(o.tara_sum, 0))";
    private static final String TASHKENT_DAY = "CAST((%s AT TIME ZONE 'Asia/Tashkent') AS date)";

    private final NamedParameterJdbcTemplate jdbc;

    public record Totals(long completedOrders, BigDecimal revenue, BigDecimal taraRevenue) {
        public static final Totals ZERO = new Totals(0, BigDecimal.ZERO, BigDecimal.ZERO);

        public BigDecimal collected() {
            return revenue.add(taraRevenue);
        }
    }

    /** [from, to) oralig'ida COMPLETED bo'lgan buyurtmalar tushumi (BE-01). farmId = null → barcha fermalar. */
    public Totals totals(Long farmId, Instant from, Instant to) {
        MapSqlParameterSource p = params(farmId, from, to);
        String sql = "SELECT COUNT(*) AS cnt, COALESCE(SUM(" + WATER_SUM + "), 0) AS revenue, "
                + "COALESCE(SUM(COALESCE(o.tara_sum, 0)), 0) AS tara "
                + "FROM orders o WHERE o.status = 'COMPLETED' "
                + "AND COALESCE(o.completed_at, o.created_at) >= :from "
                + "AND COALESCE(o.completed_at, o.created_at) < :to"
                + farmFilter(farmId);
        return jdbc.queryForObject(sql, p, (rs, i) -> new Totals(
                rs.getLong("cnt"), rs.getBigDecimal("revenue"), rs.getBigDecimal("tara")));
    }

    /** Kunlik tushum (Toshkent kuni bo'yicha), [start, end] kunlari ichida (BE-01: faqat COMPLETED). */
    public Map<LocalDate, Totals> dailyTotals(Long farmId, LocalDate start, LocalDate end) {
        MapSqlParameterSource p = params(farmId, TashkentTime.startOf(start), TashkentTime.endExclusive(end));
        String day = TASHKENT_DAY.formatted("COALESCE(o.completed_at, o.created_at)");
        String sql = "SELECT " + day + " AS d, COUNT(*) AS cnt, COALESCE(SUM(" + WATER_SUM + "), 0) AS revenue, "
                + "COALESCE(SUM(COALESCE(o.tara_sum, 0)), 0) AS tara "
                + "FROM orders o WHERE o.status = 'COMPLETED' "
                + "AND COALESCE(o.completed_at, o.created_at) >= :from "
                + "AND COALESCE(o.completed_at, o.created_at) < :to"
                + farmFilter(farmId) + " GROUP BY d";
        Map<LocalDate, Totals> result = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            result.put(rs.getDate("d").toLocalDate(), new Totals(
                    rs.getLong("cnt"), rs.getBigDecimal("revenue"), rs.getBigDecimal("tara")));
        });
        return result;
    }

    /** [from, to) oralig'idagi Manager xarajatlari. */
    public BigDecimal expenses(Long farmId, Instant from, Instant to) {
        MapSqlParameterSource p = params(farmId, from, to);
        String sql = "SELECT COALESCE(SUM(f.amount), 0) FROM finance_transactions f "
                + "WHERE f.type = 'EXPENSE' AND f.created_at >= :from AND f.created_at < :to"
                + (farmId != null ? " AND f.farm_id = :farmId" : "");
        BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    public Map<LocalDate, BigDecimal> dailyExpenses(Long farmId, LocalDate start, LocalDate end) {
        MapSqlParameterSource p = params(farmId, TashkentTime.startOf(start), TashkentTime.endExclusive(end));
        String day = TASHKENT_DAY.formatted("f.created_at");
        String sql = "SELECT " + day + " AS d, COALESCE(SUM(f.amount), 0) AS amount FROM finance_transactions f "
                + "WHERE f.type = 'EXPENSE' AND f.created_at >= :from AND f.created_at < :to"
                + (farmId != null ? " AND f.farm_id = :farmId" : "") + " GROUP BY d";
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            result.put(rs.getDate("d").toLocalDate(), rs.getBigDecimal("amount"));
        });
        return result;
    }

    public BigDecimal profit(Totals totals, BigDecimal expenses) {
        return totals.revenue().subtract(expenses != null ? expenses : BigDecimal.ZERO);
    }

    private static String farmFilter(Long farmId) {
        return farmId != null ? " AND o.farm_id = :farmId" : "";
    }

    private static MapSqlParameterSource params(Long farmId, Instant from, Instant to) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
        if (farmId != null) p.addValue("farmId", farmId);
        return p;
    }
}
