package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import uz.gidrogo.common.TashkentTime;
import uz.gidrogo.modules.finance.RevenueService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-01 (tushum faqat COMPLETED), BE-02 (soxta statistika yo'q), BE-10 (global tushum yo'q), BE-11. */
class RevenueAndFarmDetailIT extends ItBase {

    @Autowired RevenueService revenueService;

    private JsonNode dynamics(long farmId) {
        ResponseEntity<JsonNode> r = get("/api/superadmin/orders/daily-dynamics?days=7&farmId=" + farmId, superAdminToken());
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(200);
        return r.getBody().path("data");
    }

    @Test
    @DisplayName("BE-01: ASSIGNED tushumga qo'shilmaydi; COMPLETED bo'lgach +50 000 completed_at kunida; CANCELLED hech qachon")
    void revenueOnlyFromCompleted() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        long clientId = (Long) createClient(farmId).get("clientId");
        Instant now = Instant.now();

        long orderId = createOrder(farmId, clientId, "ASSIGNED", new BigDecimal("50000.00"), null, now, null);
        createOrder(farmId, clientId, "CANCELLED", new BigDecimal("70000.00"), null, now, null);
        createOrder(farmId, clientId, "DELIVERED", new BigDecimal("30000.00"), null, now, null);

        JsonNode before = dynamics(farmId);
        assertThat(before.path("totalRevenue").decimalValue()).isEqualByComparingTo("0");
        assertThat(before.path("completedOrders").asLong()).isZero();
        assertThat(before.path("totalOrders").asLong()).isEqualTo(3);
        assertThat(before.path("cancelledOrders").asLong()).isEqualTo(1);

        jdbc.update("UPDATE orders SET status = 'COMPLETED', completed_at = ? WHERE id = ?", Timestamp.from(now), orderId);

        JsonNode after = dynamics(farmId);
        assertThat(after.path("totalRevenue").decimalValue()).isEqualByComparingTo("50000");
        assertThat(after.path("completedOrders").asLong()).isEqualTo(1);
        JsonNode today = after.path("days").get(6);
        assertThat(today.path("date").asText()).isEqualTo(TashkentTime.today().toString());
        assertThat(today.path("revenue").decimalValue()).isEqualByComparingTo("50000");
        assertThat(today.has("totalAmount")).isFalse();
    }

    @Test
    @DisplayName("BE-01: 23:30 (Toshkent) da yakunlangan buyurtma shu kunga tushadi; tara alohida; BigDecimal aniqligi")
    void tashkentDayBoundaryAndTara() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        long clientId = (Long) createClient(farmId).get("clientId");
        LocalDate day = LocalDate.of(2026, 9, 22);
        Instant at2330 = Instant.parse("2026-09-22T18:30:00Z"); // 23:30 Asia/Tashkent
        Instant next0010 = Instant.parse("2026-09-22T19:10:00Z"); // 00:10 23-sentabr
        createOrder(farmId, clientId, "COMPLETED", new BigDecimal("0.10"), new BigDecimal("40000.00"), at2330, at2330);
        createOrder(farmId, clientId, "COMPLETED", new BigDecimal("0.20"), null, next0010, next0010);

        Map<LocalDate, RevenueService.Totals> daily = revenueService.dailyTotals(farmId, day, day.plusDays(1));
        assertThat(daily.get(day).revenue()).isEqualByComparingTo("0.10");
        assertThat(daily.get(day).taraRevenue()).isEqualByComparingTo("40000.00");
        assertThat(daily.get(day.plusDays(1)).revenue()).isEqualByComparingTo("0.20");

        RevenueService.Totals both = revenueService.totals(farmId,
                TashkentTime.startOf(day), TashkentTime.endExclusive(day.plusDays(1)));
        assertThat(both.revenue()).isEqualByComparingTo("0.30"); // 0.1 + 0.2 = 0.30 (double emas)
    }

    @Test
    @DisplayName("BE-10: farmId'siz daily-dynamics da revenue/totalRevenue yo'q")
    void noGlobalRevenue() {
        ResponseEntity<JsonNode> r = get("/api/superadmin/orders/daily-dynamics?days=30", superAdminToken());
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        JsonNode data = r.getBody().path("data");
        assertThat(data.has("totalRevenue")).isFalse();
        assertThat(data.path("days").get(0).has("revenue")).isFalse();
        assertThat(data.path("days").get(0).has("orderedAmount")).isFalse();
    }

    @Test
    @DisplayName("BE-02: yangi ferma — barcha raqamlar 0, grafiklar 0, '$' va kelajak sanasi yo'q")
    void newFarmHasZeroStatistics() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        ResponseEntity<JsonNode> r = get("/api/superadmin/farms/" + farmId, superAdminToken());
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        JsonNode d = r.getBody().path("data");
        JsonNode s = d.path("statistics");
        assertThat(s.path("totalOrders").asLong()).isZero();
        assertThat(s.path("activeOrders").asLong()).isZero();
        assertThat(s.path("completedOrders").asLong()).isZero();
        assertThat(s.path("totalRevenue").decimalValue()).isEqualByComparingTo("0");
        assertThat(s.path("successRatePercentage").isNull()).isTrue();
        assertThat(s.path("ordersGrowthPercentage").isNull()).isTrue();
        assertThat(s.path("revenueGrowthPercentage").isNull()).isTrue();
        assertThat(d.toString()).doesNotContain("$").doesNotContain("formattedRevenue");

        LocalDate today = TashkentTime.today();
        assertThat(d.path("weeklyChart")).hasSize(7);
        assertThat(d.path("weeklyChart").get(6).path("date").asText()).isEqualTo(today.toString());
        assertThat(d.path("monthlyChart")).hasSize(4);
        for (String chart : new String[]{"weeklyChart", "monthlyChart"}) {
            for (JsonNode p : d.path(chart)) {
                assertThat(p.path("ordersCount").asLong()).isZero();
                assertThat(p.path("revenue").decimalValue()).isEqualByComparingTo("0");
                assertThat(LocalDate.parse(p.path("endDate").asText())).isBeforeOrEqualTo(today);
            }
        }
    }

    @Test
    @DisplayName("BE-02: statistics.totalOrders = daily-dynamics?days=365 dagi totalOrders")
    void detailMatchesDailyDynamics() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        long clientId = (Long) createClient(farmId).get("clientId");
        Instant now = Instant.now();
        createOrder(farmId, clientId, "COMPLETED", new BigDecimal("10000"), null, now.minusSeconds(86400 * 3), now.minusSeconds(86400 * 2));
        createOrder(farmId, clientId, "CANCELLED", new BigDecimal("10000"), null, now.minusSeconds(86400 * 40), null);
        createOrder(farmId, clientId, "SEARCHING", new BigDecimal("10000"), null, now, null);

        JsonNode s = get("/api/superadmin/farms/" + farmId, superAdminToken()).getBody().path("data").path("statistics");
        JsonNode dyn = get("/api/superadmin/orders/daily-dynamics?days=365&farmId=" + farmId, superAdminToken()).getBody().path("data");
        assertThat(s.path("totalOrders").asLong()).isEqualTo(3).isEqualTo(dyn.path("totalOrders").asLong());
        assertThat(s.path("activeOrders").asLong()).isEqualTo(1);
        assertThat(s.path("completedOrders").asLong()).isEqualTo(1);
        assertThat(s.path("cancelledOrders").asLong()).isEqualTo(1);
        assertThat(s.path("totalRevenue").decimalValue()).isEqualByComparingTo("10000");
        assertThat(s.path("successRatePercentage").asDouble()).isEqualTo(33.3);
    }

    @Test
    @DisplayName("BE-11: analitika — tushum, xarajat, foyda, mahsulotlar; Boss moliya bilan bir xil raqam")
    void analyticsMatchesBossFinance() {
        Map<String, Object> farm = createFarmViaApi();
        long farmId = (Long) farm.get("farmId");
        long clientId = (Long) createClient(farmId).get("clientId");
        long productId = createProduct(farmId, new BigDecimal("15000"));
        Instant now = Instant.now();
        long orderId = createOrder(farmId, clientId, "COMPLETED", new BigDecimal("30000"), new BigDecimal("5000"), now, now);
        jdbc.update("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, 2, 15000)", orderId, productId);
        jdbc.update("INSERT INTO finance_transactions (farm_id, type, category, amount, created_at) VALUES (?, 'EXPENSE', 'FUEL', 12000, now())", farmId);

        JsonNode a = get("/api/superadmin/farms/" + farmId + "/analytics?granularity=WEEK", superAdminToken()).getBody().path("data");
        assertThat(a.path("totals").path("revenue").decimalValue()).isEqualByComparingTo("30000");
        assertThat(a.path("totals").path("taraRevenue").decimalValue()).isEqualByComparingTo("5000");
        assertThat(a.path("totals").path("expenses").decimalValue()).isEqualByComparingTo("12000");
        assertThat(a.path("totals").path("profit").decimalValue()).isEqualByComparingTo("18000");
        assertThat(a.path("products").get(0).path("soldQty").decimalValue()).isEqualByComparingTo("2");
        assertThat(a.path("products").get(0).path("revenue").decimalValue()).isEqualByComparingTo("30000");

        // Boss paneli shu fermani ko'radi (Boss tasdiqlangan bo'lishi kerak)
        jdbc.update("UPDATE users SET activation_status = 'APPROVED' WHERE id = ?", farm.get("bossId"));
        String bossToken = loginToken((String) farm.get("bossPhone"), PASSWORD);
        JsonNode summary = get("/api/boss/finance/summary?period=month", bossToken).getBody().path("data");
        assertThat(summary.path("totalIncome").decimalValue()).isEqualByComparingTo("30000");
        assertThat(summary.path("taraIncome").decimalValue()).isEqualByComparingTo("5000");
        assertThat(summary.path("netProfit").decimalValue()).isEqualByComparingTo("18000");

        JsonNode detail = get("/api/superadmin/farms/" + farmId, superAdminToken()).getBody().path("data").path("statistics");
        assertThat(detail.path("profit").decimalValue()).isEqualByComparingTo("18000");
    }
}
