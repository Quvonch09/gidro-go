package uz.gidrogo.modules.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.finance.dto.FinanceDtos.*;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.order.OrderService;
import uz.gidrogo.modules.order.OrderStatsService;
import uz.gidrogo.common.TashkentTime;
import java.time.LocalDate;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
import uz.gidrogo.modules.stock.StockService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final FinanceRepository financeRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final OrderService orderService;
    private final RevenueService revenueService;
    private final OrderStatsService orderStatsService;

    @Transactional
    public void recordIncome(Long farmId, BigDecimal amount, Long orderId, Long courierId, String note) {
        FinanceTransaction tx = FinanceTransaction.builder()
                .farmId(farmId)
                .type("INCOME")
                .category("ORDER_PAYMENT")
                .amount(amount)
                .orderId(orderId)
                .courierId(courierId)
                .note(note)
                .build();
        financeRepository.save(tx);
    }

    @Transactional
    public TransactionResponse recordExpense(ExpenseCreateRequest request) {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma identifikatori aniqlanmadi");
        }

        FinanceTransaction tx = FinanceTransaction.builder()
                .farmId(farmId)
                .type("EXPENSE")
                .category(request.getCategory())
                .amount(request.getAmount())
                .courierId(request.getCourierId())
                .note(request.getNote())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        tx = financeRepository.save(tx);
        return mapToResponse(tx);
    }

    /**
     * BE-01: tushum yagona {@link RevenueService} dan — faqat COMPLETED, completed_at bo'yicha (Asia/Tashkent).
     * totalIncome — suv/mahsulot tushumi, taraIncome — tara (alohida qator), netProfit = totalIncome − totalExpense.
     * Davrlar: today (bugun), week (oxirgi 7 kun), month (oxirgi 30 kun), year (oxirgi 365 kun), all.
     */
    public FinanceSummaryResponse getSummary(Long farmId, String period) {
        String p = period != null ? period.toLowerCase() : "all";
        LocalDate today = TashkentTime.today();
        Instant since = switch (p) {
            case "today" -> TashkentTime.startOf(today);
            case "week" -> TashkentTime.startOf(today.minusDays(6));
            case "month" -> TashkentTime.startOf(today.minusDays(29));
            case "year" -> TashkentTime.startOf(today.minusDays(364));
            default -> Instant.EPOCH;
        };
        Instant until = TashkentTime.endExclusive(today);

        RevenueService.Totals totals = revenueService.totals(farmId, since, until);
        BigDecimal expense = revenueService.expenses(farmId, since, until);

        return FinanceSummaryResponse.builder()
                .farmId(farmId)
                .totalIncome(totals.revenue())
                .taraIncome(totals.taraRevenue())
                .totalExpense(expense)
                .netProfit(revenueService.profit(totals, expense))
                .completedOrders(totals.completedOrders())
                .period(period != null ? period : "all")
                .build();
    }

    public DashboardResponse getDashboard(Long farmId) {
        LocalDate today = TashkentTime.today();
        Instant todayStart = TashkentTime.startOf(today);
        FinanceSummaryResponse todayFinance = getSummary(farmId, "today");
        FinanceSummaryResponse monthFinance = getSummary(farmId, "month");

        long totalOrders = orderRepository.countByFarmId(farmId);
        long todayOrders = orderRepository.countByFarmIdSince(farmId, todayStart);
        long activeOrders = orderRepository.countByFarmIdAndStatusIn(farmId, OrderStatsService.ACTIVE_STATUSES);
        long deliveredToday = orderRepository.countCompletedByFarmSince(farmId, todayStart);
        BigDecimal bottlesSoldToday = orderRepository.sumDeliveredBottlesByFarmSince(farmId, todayStart);

        BigDecimal warehouseBottles = stockService.getTotalWarehouseBottles(farmId);
        long activeCouriers = userRepository.findAllByFarmIdAndRole(farmId, Role.COURIER).stream()
                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .count();
        long totalClients = clientRepository.countByFarmId(farmId);

        // O'zgarish %: oxirgi 30 kun vs oldingi 30 kun (oldingi 0 bo'lsa null) — o'ylab topilgan raqam yo'q
        Instant curStart = TashkentTime.startOf(today.minusDays(29));
        Instant curEnd = TashkentTime.endExclusive(today);
        Instant prevStart = TashkentTime.startOf(today.minusDays(59));
        RevenueService.Totals revCur = revenueService.totals(farmId, curStart, curEnd);
        RevenueService.Totals revPrev = revenueService.totals(farmId, prevStart, curStart);
        long ordersCur = orderStatsService.countByStatus(farmId, curStart, curEnd).total();
        long ordersPrev = orderStatsService.countByStatus(farmId, prevStart, curStart).total();
        long clientsCur = clientRepository.countByFarmIdAndCreatedAtBetween(farmId, curStart, curEnd);
        long clientsPrev = clientRepository.countByFarmIdAndCreatedAtBetween(farmId, prevStart, curStart);

        List<OrderResponse> recentOrders = orderService.getManagerOrders(null).stream()
                .limit(5)
                .toList();

        return DashboardResponse.builder()
                .farmId(farmId)
                .todayIncome(todayFinance.getTotalIncome())
                .monthIncome(monthFinance.getTotalIncome())
                .todayExpense(todayFinance.getTotalExpense())
                .todayProfit(todayFinance.getNetProfit())
                .monthProfit(monthFinance.getNetProfit())
                .totalOrders(totalOrders)
                .todayOrders(todayOrders)
                .activeOrders(activeOrders)
                .deliveredToday(deliveredToday)
                .bottlesSoldToday(bottlesSoldToday != null ? bottlesSoldToday : BigDecimal.ZERO)
                .warehouseBottles(warehouseBottles != null ? warehouseBottles : BigDecimal.ZERO)
                .activeCouriers(activeCouriers)
                .totalClients(totalClients)
                .incomeChangePercent(TashkentTime.growthPercent(revCur.revenue(), revPrev.revenue()))
                .ordersChangePercent(TashkentTime.growthPercent(ordersCur, ordersPrev))
                 .clientsChangePercent(TashkentTime.growthPercent(clientsCur, clientsPrev))
                .recentOrders(recentOrders)
                .build();
    }

    public DashboardResponse getDashboard(Long farmId, LocalDate date, String period, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = TashkentTime.resolveRange(period, date, startDate, endDate, null, null);
        LocalDate start = range[0];
        LocalDate end = range[1];
        Instant curStart = TashkentTime.startOf(start);
        Instant curEnd = TashkentTime.endExclusive(end);

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(daysBetween - 1);
        Instant prevFrom = TashkentTime.startOf(prevStart);
        Instant prevTo = TashkentTime.endExclusive(prevEnd);

        RevenueService.Totals periodTotals = revenueService.totals(farmId, curStart, curEnd);
        BigDecimal periodExpense = revenueService.expenses(farmId, curStart, curEnd);
        BigDecimal periodProfit = revenueService.profit(periodTotals, periodExpense);
        long periodOrders = orderRepository.countByFarmIdSince(farmId, curStart);
        long periodDelivered = orderRepository.countCompletedByFarmSince(farmId, curStart);

        RevenueService.Totals prevTotals = revenueService.totals(farmId, prevFrom, prevTo);
        Double growthRate = TashkentTime.growthPercent(periodTotals.revenue(), prevTotals.revenue());

        DashboardResponse base = getDashboard(farmId);
        base.setPeriod(period != null ? period : (date != null ? "custom_date" : "custom_range"));
        base.setDate(date != null ? date.toString() : null);
        base.setStartDate(start.toString());
        base.setEndDate(end.toString());
        base.setPeriodIncome(periodTotals.revenue());
        base.setPeriodExpense(periodExpense);
        base.setPeriodProfit(periodProfit);
        base.setPeriodOrders(periodOrders);
        base.setPeriodDelivered(periodDelivered);
        base.setGrowthRate(growthRate != null ? growthRate : base.getIncomeChangePercent());
        return base;
    }

    public List<TransactionResponse> getTransactions(Long farmId) {
        return getTransactions(farmId, null, null, null, null);
    }

    public List<TransactionResponse> getTransactions(Long farmId, LocalDate startDate, LocalDate endDate,
                                                     String type, Long courierId) {
        Instant from = startDate != null ? TashkentTime.startOf(startDate) : null;
        Instant to = endDate != null ? TashkentTime.endExclusive(endDate) : null;

        return financeRepository.findAllByFarmIdOrderByCreatedAtDesc(farmId).stream()
                .filter(tx -> {
                    if (from != null && tx.getCreatedAt().isBefore(from)) return false;
                    if (to != null && tx.getCreatedAt().isAfter(to)) return false;
                    if (courierId != null && !courierId.equals(tx.getCourierId())) return false;
                    if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
                        String t = type.trim().toUpperCase(java.util.Locale.ROOT);
                        boolean typeMatch = tx.getType() != null && tx.getType().equalsIgnoreCase(t);
                        boolean catMatch = tx.getCategory() != null && tx.getCategory().toUpperCase().contains(t);
                        boolean noteMatch = tx.getNote() != null && tx.getNote().toUpperCase().contains(t);
                        if (!typeMatch && !catMatch && !noteMatch) return false;
                    }
                    return true;
                })
                .map(this::mapToResponse)
                .toList();
    }

    public uz.gidrogo.common.PageResponse<TransactionResponse> getTransactionsPaged(Long farmId, LocalDate startDate, LocalDate endDate,
                                                                                   String type, Long courierId, int page, int size) {
        List<TransactionResponse> filtered = getTransactions(farmId, startDate, endDate, type, courierId);
        return uz.gidrogo.common.PageResponse.ofList(filtered, page, size);
    }

    public List<TransactionResponse> getCourierTransactions(Long farmId, Long courierId) {
        return financeRepository.findAllByFarmIdAndCourierIdOrderByCreatedAtDesc(farmId, courierId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private TransactionResponse mapToResponse(FinanceTransaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .farmId(tx.getFarmId())
                .type(tx.getType())
                .category(tx.getCategory())
                .amount(tx.getAmount())
                .orderId(tx.getOrderId())
                .courierId(tx.getCourierId())
                .note(tx.getNote())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
