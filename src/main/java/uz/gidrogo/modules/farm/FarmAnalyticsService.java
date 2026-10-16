package uz.gidrogo.modules.farm;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import uz.gidrogo.common.Granularity;
import uz.gidrogo.common.TashkentTime;
import uz.gidrogo.modules.farm.dto.FarmDtos.*;
import uz.gidrogo.modules.finance.RevenueService;
import uz.gidrogo.modules.order.OrderStatsService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ferma analitikasi (BE-11, TZ §6.4, §12) va ferma tafsilotidagi grafiklar (BE-02).
 * Tushum/xarajat/foyda {@link RevenueService} dan — Boss paneli bilan bir xil formula.
 */
@Service
@RequiredArgsConstructor
public class FarmAnalyticsService {

    private static final String[] SHORT_DAYS = {"Dush", "Sesh", "Chor", "Pay", "Jum", "Shan", "Yak"};
    private static final String[] FULL_DAYS = {"Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba"};

    private final RevenueService revenueService;
    private final OrderStatsService orderStatsService;
    private final NamedParameterJdbcTemplate jdbc;

    /** Bugun bilan tugaydigan oxirgi 7 kun (Asia/Tashkent). Kelajak sanasi yo'q. */
    public List<ChartDataPoint> weeklyChart(Long farmId) {
        LocalDate today = TashkentTime.today();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, RevenueService.Totals> revenue = revenueService.dailyTotals(farmId, start, today);
        Map<LocalDate, OrderStatsService.DayCounts> created = orderStatsService.dailyCreated(farmId, start, today);

        List<ChartDataPoint> chart = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            RevenueService.Totals t = revenue.getOrDefault(d, RevenueService.Totals.ZERO);
            int dow = d.getDayOfWeek().getValue() - 1;
            chart.add(ChartDataPoint.builder()
                    .day(SHORT_DAYS[dow])
                    .dayName(FULL_DAYS[dow])
                    .date(d.toString())
                    .endDate(d.toString())
                    .ordersCount(created.getOrDefault(d, OrderStatsService.DayCounts.ZERO).total())
                    .completedOrders(t.completedOrders())
                    .revenue(t.revenue())
                    .taraRevenue(t.taraRevenue())
                    .build());
        }
        return chart;
    }

    /** Oxirgi 4 hafta: har biri 7 kunlik oraliq, oxirgisi bugun bilan tugaydi. */
    public List<ChartDataPoint> monthlyChart(Long farmId) {
        LocalDate today = TashkentTime.today();
        LocalDate start = today.minusDays(27);
        Map<LocalDate, RevenueService.Totals> revenue = revenueService.dailyTotals(farmId, start, today);
        Map<LocalDate, OrderStatsService.DayCounts> created = orderStatsService.dailyCreated(farmId, start, today);

        List<ChartDataPoint> chart = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            LocalDate wStart = start.plusDays(w * 7L);
            LocalDate wEnd = wStart.plusDays(6);
            long orders = 0;
            long completed = 0;
            BigDecimal rev = BigDecimal.ZERO;
            BigDecimal tara = BigDecimal.ZERO;
            for (LocalDate d = wStart; !d.isAfter(wEnd); d = d.plusDays(1)) {
                orders += created.getOrDefault(d, OrderStatsService.DayCounts.ZERO).total();
                RevenueService.Totals t = revenue.getOrDefault(d, RevenueService.Totals.ZERO);
                completed += t.completedOrders();
                rev = rev.add(t.revenue());
                tara = tara.add(t.taraRevenue());
            }
            chart.add(ChartDataPoint.builder()
                    .day((w + 1) + "-hafta")
                    .dayName((w + 1) + "-hafta")
                    .date(wStart.toString())
                    .endDate(wEnd.toString())
                    .ordersCount(orders)
                    .completedOrders(completed)
                    .revenue(rev)
                    .taraRevenue(tara)
                    .build());
        }
        return chart;
    }

    /** GET /api/superadmin/farms/{id}/analytics */
    public FarmAnalyticsResponse analytics(Long farmId, LocalDate from, LocalDate to, Granularity granularity) {
        Map<LocalDate, RevenueService.Totals> revenue = revenueService.dailyTotals(farmId, from, to);
        Map<LocalDate, BigDecimal> expenses = revenueService.dailyExpenses(farmId, from, to);
        Map<LocalDate, OrderStatsService.DayCounts> created = orderStatsService.dailyCreated(farmId, from, to);
        Map<LocalDate, Long> newClients = dailyNewClients(farmId, from, to);

        List<AnalyticsPoint> series = new ArrayList<>();
        AnalyticsPoint totals = emptyPoint(from, to);

        LocalDate bucket = granularity.bucketStart(from);
        while (!bucket.isAfter(to)) {
            LocalDate next = granularity.next(bucket);
            LocalDate pStart = bucket.isBefore(from) ? from : bucket;
            LocalDate pEnd = next.minusDays(1).isAfter(to) ? to : next.minusDays(1);
            AnalyticsPoint p = emptyPoint(pStart, pEnd);
            for (LocalDate d = pStart; !d.isAfter(pEnd); d = d.plusDays(1)) {
                RevenueService.Totals t = revenue.getOrDefault(d, RevenueService.Totals.ZERO);
                OrderStatsService.DayCounts c = created.getOrDefault(d, OrderStatsService.DayCounts.ZERO);
                BigDecimal e = expenses.getOrDefault(d, BigDecimal.ZERO);
                long nc = newClients.getOrDefault(d, 0L);
                accumulate(p, t, c, e, nc);
                accumulate(totals, t, c, e, nc);
            }
            p.setProfit(p.getRevenue().subtract(p.getExpenses()));
            series.add(p);
            bucket = next;
        }
        totals.setProfit(totals.getRevenue().subtract(totals.getExpenses()));

        return FarmAnalyticsResponse.builder()
                .farmId(farmId)
                .from(from.toString())
                .to(to.toString())
                .granularity(granularity)
                .totals(totals)
                .series(series)
                .products(productPerformance(farmId, from, to))
                .build();
    }

    /** Mahsulotlar samaradorligi: faqat COMPLETED buyurtmalar, completed_at bo'yicha. */
    public List<ProductPerformance> productPerformance(Long farmId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("farmId", farmId)
                .addValue("from", Timestamp.from(TashkentTime.startOf(from)))
                .addValue("to", Timestamp.from(TashkentTime.endExclusive(to)));
        String sql = "SELECT pr.id AS product_id, pr.name AS name, "
                + "COALESCE(SUM(oi.quantity), 0) AS sold_qty, COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue "
                + "FROM products pr "
                + "LEFT JOIN order_items oi ON oi.product_id = pr.id AND oi.order_id IN ("
                + "  SELECT o.id FROM orders o WHERE o.farm_id = :farmId AND o.status = 'COMPLETED' "
                + "  AND o.completed_at >= :from AND o.completed_at < :to) "
                + "WHERE pr.farm_id = :farmId "
                + "GROUP BY pr.id, pr.name ORDER BY revenue DESC, pr.id";
        return jdbc.query(sql, p, (rs, i) -> ProductPerformance.builder()
                .productId(rs.getLong("product_id"))
                .name(rs.getString("name"))
                .soldQty(rs.getBigDecimal("sold_qty"))
                .revenue(rs.getBigDecimal("revenue"))
                .build());
    }

    private Map<LocalDate, Long> dailyNewClients(Long farmId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("farmId", farmId)
                .addValue("from", Timestamp.from(TashkentTime.startOf(from)))
                .addValue("to", Timestamp.from(TashkentTime.endExclusive(to)));
        String sql = "SELECT CAST((c.created_at AT TIME ZONE 'Asia/Tashkent') AS date) AS d, COUNT(*) AS cnt "
                + "FROM clients c WHERE c.farm_id = :farmId AND c.created_at >= :from AND c.created_at < :to GROUP BY d";
        Map<LocalDate, Long> map = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            map.put(rs.getDate("d").toLocalDate(), rs.getLong("cnt"));
        });
        return map;
    }

    private static AnalyticsPoint emptyPoint(LocalDate start, LocalDate end) {
        return AnalyticsPoint.builder()
                .periodStart(start.toString())
                .periodEnd(end.toString())
                .revenue(BigDecimal.ZERO)
                .taraRevenue(BigDecimal.ZERO)
                .expenses(BigDecimal.ZERO)
                .profit(BigDecimal.ZERO)
                .build();
    }

    private static void accumulate(AnalyticsPoint p, RevenueService.Totals t, OrderStatsService.DayCounts c,
                                   BigDecimal expenses, long newClients) {
        p.setRevenue(p.getRevenue().add(t.revenue()));
        p.setTaraRevenue(p.getTaraRevenue().add(t.taraRevenue()));
        p.setExpenses(p.getExpenses().add(expenses));
        p.setCompletedOrders(p.getCompletedOrders() + t.completedOrders());
        p.setOrders(p.getOrders() + c.total());
        p.setNewClients(p.getNewClients() + newClients);
    }
}
