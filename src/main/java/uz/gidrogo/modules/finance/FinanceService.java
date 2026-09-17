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
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
import uz.gidrogo.modules.stock.StockService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    public FinanceSummaryResponse getSummary(Long farmId, String period) {
        Instant since = switch (period != null ? period.toLowerCase() : "all") {
            case "today" -> Instant.now().truncatedTo(ChronoUnit.DAYS);
            case "week" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "month" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "year" -> Instant.now().minus(365, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };

        BigDecimal income = financeRepository.sumByFarmIdAndTypeSince(farmId, "INCOME", since);
        BigDecimal expense = financeRepository.sumByFarmIdAndTypeSince(farmId, "EXPENSE", since);
        BigDecimal netProfit = income.subtract(expense);

        return FinanceSummaryResponse.builder()
                .farmId(farmId)
                .totalIncome(income)
                .totalExpense(expense)
                .netProfit(netProfit)
                .period(period != null ? period : "all")
                .build();
    }

    public DashboardResponse getDashboard(Long farmId) {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        FinanceSummaryResponse todayFinance = getSummary(farmId, "today");
        FinanceSummaryResponse monthFinance = getSummary(farmId, "month");

        long totalOrders = orderRepository.countByFarmId(farmId);
        long todayOrders = orderRepository.countByFarmIdSince(farmId, todayStart);
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.NEW, OrderStatus.SEARCHING, OrderStatus.ASSIGNED,
                OrderStatus.PREPARING, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY
        );
        long activeOrders = orderRepository.countByFarmIdAndStatusIn(farmId, activeStatuses);
        long deliveredToday = orderRepository.countCompletedByFarmSince(farmId, todayStart);
        BigDecimal bottlesSoldToday = orderRepository.sumDeliveredBottlesByFarmSince(farmId, todayStart);

        BigDecimal warehouseBottles = stockService.getTotalWarehouseBottles(farmId);
        long activeCouriers = userRepository.findAllByFarmIdAndRole(farmId, Role.COURIER).stream()
                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .count();
        long totalClients = clientRepository.countByFarmId(farmId);

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
                .recentOrders(recentOrders)
                .build();
    }

    public List<TransactionResponse> getTransactions(Long farmId) {
        return financeRepository.findAllByFarmIdOrderByCreatedAtDesc(farmId).stream()
                .map(this::mapToResponse)
                .toList();
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
