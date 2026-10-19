package uz.gidrogo.modules.cash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ConflictException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.cash.CashDtos.*;
import uz.gidrogo.modules.order.Order;
import uz.gidrogo.websocket.CourierWebSocketHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashService {

    private final CashLedgerRepository ledgerRepository;
    private final CashHandoverRepository handoverRepository;
    private final UserRepository userRepository;
    private final CourierWebSocketHandler courierWebSocketHandler;

    @Transactional
    public void recordCashCollected(Order order, Long courierId) {
        if (order == null || courierId == null) return;

        boolean exists = ledgerRepository.existsByOrderIdAndType(order.getId(), "COLLECTED");
        if (exists) {
            log.info("Order {} uchun naqd pul allaqachon kassa ledgeriga yozilgan", order.getId());
            return;
        }

        BigDecimal amount = order.getTotalSum();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        CashLedger entry = CashLedger.builder()
                .courierId(courierId)
                .farmId(order.getFarmId())
                .type("COLLECTED")
                .amount(amount)
                .orderId(order.getId())
                .build();
        ledgerRepository.save(entry);
        log.info("Kuryer {} uchun order {} dan {} so'm naqd pul kassa ledgeriga yozildi", courierId, order.getId(), amount);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCourierBalance(Long courierId) {
        BigDecimal collected = ledgerRepository.sumAmountByCourierIdAndType(courierId, "COLLECTED");
        BigDecimal handedOver = ledgerRepository.sumAmountByCourierIdAndType(courierId, "HANDOVER");
        return collected.subtract(handedOver);
    }

    @Transactional(readOnly = true)
    public CourierCashSummaryResponse getCourierCash(Long courierId) {
        BigDecimal balance = calculateCourierBalance(courierId);

        Instant startOfToday = LocalDate.now(ZoneId.of("Asia/Tashkent")).atStartOfDay(ZoneId.of("Asia/Tashkent")).toInstant();
        BigDecimal collectedToday = ledgerRepository.sumAmountByCourierIdAndTypeSince(courierId, "COLLECTED", startOfToday);

        PendingHandoverDto pendingDto = handoverRepository.findFirstByCourierIdAndStatus(courierId, "PENDING")
                .map(h -> PendingHandoverDto.builder()
                        .id(h.getId())
                        .declaredAmount(h.getDeclaredAmount())
                        .createdAt(h.getCreatedAt())
                        .build())
                .orElse(null);

        List<CashLedgerEntryDto> entries = ledgerRepository
                .findAllByCourierIdOrderByCreatedAtDesc(courierId, PageRequest.of(0, 20))
                .stream()
                .map(e -> CashLedgerEntryDto.builder()
                        .id(e.getId())
                        .type(e.getType())
                        .amount(e.getAmount())
                        .orderId(e.getOrderId())
                        .handoverId(e.getHandoverId())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();

        return CourierCashSummaryResponse.builder()
                .balance(balance)
                .collectedToday(collectedToday)
                .pendingHandover(pendingDto)
                .entries(entries)
                .build();
    }

    @Transactional
    public CashHandoverResponse requestHandover(Long courierId, Long farmId, CourierHandoverRequest request) {
        if (request.getDeclaredAmount() == null || request.getDeclaredAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Summa 0 dan katta bo'lishi kerak");
        }

        BigDecimal balance = calculateCourierBalance(courierId);
        if (request.getDeclaredAmount().compareTo(balance) > 0) {
            throw new BadRequestException("Kassa balansidan (" + balance + " so'm) ortiq summa topshirib bo'lmaydi");
        }

        if (handoverRepository.existsByCourierIdAndStatus(courierId, "PENDING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HANDOVER_ALREADY_PENDING: Kutilayotgan topshirish so'rovi allaqachon mavjud");
        }

        CashHandover handover = CashHandover.builder()
                .courierId(courierId)
                .farmId(farmId)
                .declaredAmount(request.getDeclaredAmount())
                .status("PENDING")
                .initiatedBy("COURIER")
                .note(request.getNote())
                .build();
        handover = handoverRepository.save(handover);

        User courier = userRepository.findById(courierId).orElse(null);

        // WS xabari menejerlarga
        Map<String, Object> event = Map.of(
                "type", "CASH_HANDOVER_REQUESTED",
                "data", Map.of(
                        "handoverId", handover.getId(),
                        "courierId", courierId,
                        "courierName", courier != null ? courier.getFullName() : "",
                        "declaredAmount", handover.getDeclaredAmount(),
                        "createdAt", handover.getCreatedAt().toString()
                )
        );
        // Farm managerlariga xabar yuborish
        log.info("Kassa topshirish so'rovi yaratildi: id={}, courierId={}", handover.getId(), courierId);

        return mapToResponse(handover, courier != null ? courier.getFullName() : null, null);
    }

    @Transactional
    public CashHandoverResponse confirmHandover(Long managerId, Long farmId, Long handoverId, ManagerHandoverConfirmRequest request) {
        CashHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Topshirish so'rovi topilmadi"));

        if (!"PENDING".equalsIgnoreCase(handover.getStatus())) {
            throw new BadRequestException("Topshirish so'rovi faol emas: " + handover.getStatus());
        }

        BigDecimal currentBalance = calculateCourierBalance(handover.getCourierId());
        if (request.getConfirmedAmount().compareTo(currentBalance) > 0) {
            throw new BadRequestException("Tasdiqlanayotgan summa kuryerning joriy balansidan (" + currentBalance + ") oshmasligi kerak");
        }

        if (request.getConfirmedAmount().compareTo(handover.getDeclaredAmount()) != 0) {
            if (request.getNote() == null || request.getNote().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Summa farq qilganda izoh (note) majburiy");
            }
        }

        Instant now = Instant.now();
        handover.setStatus("CONFIRMED");
        handover.setConfirmedAmount(request.getConfirmedAmount());
        handover.setManagerId(managerId);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            handover.setNote(request.getNote());
        }
        handover.setResolvedAt(now);
        handover = handoverRepository.save(handover);

        // Ledgerga HANDOVER yozish
        CashLedger entry = CashLedger.builder()
                .courierId(handover.getCourierId())
                .farmId(handover.getFarmId())
                .type("HANDOVER")
                .amount(request.getConfirmedAmount())
                .handoverId(handover.getId())
                .build();
        ledgerRepository.save(entry);

        User courier = userRepository.findById(handover.getCourierId()).orElse(null);
        User manager = userRepository.findById(managerId).orElse(null);

        // Kuryerga WS xabari
        Map<String, Object> wsEvent = Map.of(
                "type", "CASH_HANDOVER_CONFIRMED",
                "data", Map.of(
                        "handoverId", handover.getId(),
                        "confirmedAmount", handover.getConfirmedAmount(),
                        "newBalance", calculateCourierBalance(handover.getCourierId())
                ),
                "timestamp", now.toString()
        );
        courierWebSocketHandler.sendToCourier(handover.getCourierId(), wsEvent);

        return mapToResponse(handover, courier != null ? courier.getFullName() : null, manager != null ? manager.getFullName() : null);
    }

    @Transactional
    public CashHandoverResponse rejectHandover(Long managerId, Long farmId, Long handoverId, ManagerHandoverRejectRequest request) {
        CashHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Topshirish so'rovi topilmadi"));

        if (!"PENDING".equalsIgnoreCase(handover.getStatus())) {
            throw new BadRequestException("Topshirish so'rovi faol emas: " + handover.getStatus());
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Rad etish sababi majburiy");
        }

        Instant now = Instant.now();
        handover.setStatus("REJECTED");
        handover.setRejectReason(request.getReason());
        handover.setManagerId(managerId);
        handover.setResolvedAt(now);
        handover = handoverRepository.save(handover);

        User courier = userRepository.findById(handover.getCourierId()).orElse(null);
        User manager = userRepository.findById(managerId).orElse(null);

        // Kuryerga WS xabari
        Map<String, Object> wsEvent = Map.of(
                "type", "CASH_HANDOVER_REJECTED",
                "data", Map.of(
                        "handoverId", handover.getId(),
                        "reason", request.getReason()
                ),
                "timestamp", now.toString()
        );
        courierWebSocketHandler.sendToCourier(handover.getCourierId(), wsEvent);

        return mapToResponse(handover, courier != null ? courier.getFullName() : null, manager != null ? manager.getFullName() : null);
    }

    @Transactional
    public CashHandoverResponse managerDirectHandover(Long managerId, Long farmId, Long courierId, ManagerDirectHandoverRequest request) {
        BigDecimal balance = calculateCourierBalance(courierId);
        if (request.getAmount().compareTo(balance) > 0) {
            throw new BadRequestException("Kassa balansidan (" + balance + " so'm) ortiq summa kiritib bo'lmaydi");
        }

        Instant now = Instant.now();
        CashHandover handover = CashHandover.builder()
                .courierId(courierId)
                .farmId(farmId)
                .declaredAmount(request.getAmount())
                .confirmedAmount(request.getAmount())
                .status("CONFIRMED")
                .initiatedBy("MANAGER")
                .managerId(managerId)
                .note(request.getNote())
                .resolvedAt(now)
                .build();
        handover = handoverRepository.save(handover);

        CashLedger entry = CashLedger.builder()
                .courierId(courierId)
                .farmId(farmId)
                .type("HANDOVER")
                .amount(request.getAmount())
                .handoverId(handover.getId())
                .build();
        ledgerRepository.save(entry);

        User courier = userRepository.findById(courierId).orElse(null);
        User manager = userRepository.findById(managerId).orElse(null);

        return mapToResponse(handover, courier != null ? courier.getFullName() : null, manager != null ? manager.getFullName() : null);
    }

    @Transactional(readOnly = true)
    public List<CourierCashBalanceItem> getCouriersCashBalances(Long farmId) {
        List<User> couriers = userRepository.findAllByFarmIdAndRole(farmId, Role.COURIER);
        List<CourierCashBalanceItem> list = new ArrayList<>();

        for (User c : couriers) {
            BigDecimal bal = calculateCourierBalance(c.getId());
            Instant oldest = ledgerRepository.findOldestCollectedAt(c.getId());
            Instant latestHandover = ledgerRepository.findLatestHandoverAt(c.getId());

            list.add(CourierCashBalanceItem.builder()
                    .courierId(c.getId())
                    .fullName(c.getFullName())
                    .phone(c.getPhone())
                    .balance(bal)
                    .oldestUnsubmittedAt(oldest)
                    .lastHandoverAt(latestHandover)
                    .build());
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<CashHandoverResponse> getFarmHandovers(Long farmId, String status) {
        return getFarmHandovers(farmId, status, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<CashHandoverResponse> getFarmHandovers(Long farmId, String status, Long courierId,
                                                      LocalDate date, LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = (date != null || startDate != null || endDate != null)
                ? uz.gidrogo.common.TashkentTime.resolveRange(null, date, startDate, endDate, null, null)
                : new LocalDate[]{null, null};
        Instant from = range[0] != null ? uz.gidrogo.common.TashkentTime.startOf(range[0]) : null;
        Instant to = range[1] != null ? uz.gidrogo.common.TashkentTime.endExclusive(range[1]) : null;

        List<CashHandover> list = handoverRepository.findAllByFarmIdOrderByCreatedAtDesc(farmId);
        List<CashHandoverResponse> resp = new ArrayList<>();
        for (CashHandover h : list) {
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                if (!status.equalsIgnoreCase(h.getStatus())) continue;
            }
            if (courierId != null && !courierId.equals(h.getCourierId())) continue;
            if (from != null && h.getCreatedAt().isBefore(from)) continue;
            if (to != null && h.getCreatedAt().isAfter(to)) continue;

            User c = userRepository.findById(h.getCourierId()).orElse(null);
            User m = h.getManagerId() != null ? userRepository.findById(h.getManagerId()).orElse(null) : null;
            resp.add(mapToResponse(h, c != null ? c.getFullName() : null, m != null ? m.getFullName() : null));
        }
        return resp;
    }

    @Transactional(readOnly = true)
    public BossCashSummaryResponse getBossCashSummary(Long farmId, Instant from, Instant to) {
        return getBossCashSummary(farmId, null, null, null, null, from, to);
    }

    @Transactional(readOnly = true)
    public BossCashSummaryResponse getBossCashSummary(Long farmId, String period, LocalDate date, LocalDate startDate, LocalDate endDate, Instant from, Instant to) {
        Instant startTime = from;
        Instant endTime = to;
        if (startTime == null || endTime == null) {
            if (period != null || date != null || startDate != null || endDate != null) {
                LocalDate[] range = uz.gidrogo.common.TashkentTime.resolveRange(period, date, startDate, endDate, null, null);
                startTime = uz.gidrogo.common.TashkentTime.startOf(range[0]);
                endTime = uz.gidrogo.common.TashkentTime.endExclusive(range[1]);
            }
        }

        BigDecimal collected;
        BigDecimal handedOver;

        if (startTime != null && endTime != null) {
            collected = ledgerRepository.sumAmountByFarmIdAndTypeBetween(farmId, "COLLECTED", startTime, endTime);
            handedOver = ledgerRepository.sumAmountByFarmIdAndTypeBetween(farmId, "HANDOVER", startTime, endTime);
        } else {
            collected = ledgerRepository.sumAmountByFarmIdAndType(farmId, "COLLECTED");
            handedOver = ledgerRepository.sumAmountByFarmIdAndType(farmId, "HANDOVER");
        }

        if (collected == null) collected = BigDecimal.ZERO;
        if (handedOver == null) handedOver = BigDecimal.ZERO;

        BigDecimal remaining = collected.subtract(handedOver);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        List<User> couriers = userRepository.findAllByFarmIdAndRole(farmId, Role.COURIER);
        int activeCouriersWithCash = 0;
        for (User c : couriers) {
            BigDecimal bal = calculateCourierBalance(c.getId());
            if (bal != null && bal.compareTo(BigDecimal.ZERO) > 0) {
                activeCouriersWithCash++;
            }
        }

        return BossCashSummaryResponse.builder()
                .totalCollected(collected)
                .totalHandedOver(handedOver)
                .remainingWithCouriers(remaining)
                .activeCouriersWithCash(activeCouriersWithCash)
                .build();
    }

    private CashHandoverResponse mapToResponse(CashHandover h, String courierName, String managerName) {
        return CashHandoverResponse.builder()
                .id(h.getId())
                .courierId(h.getCourierId())
                .courierName(courierName)
                .farmId(h.getFarmId())
                .declaredAmount(h.getDeclaredAmount())
                .confirmedAmount(h.getConfirmedAmount())
                .status(h.getStatus())
                .initiatedBy(h.getInitiatedBy())
                .managerId(h.getManagerId())
                .managerName(managerName)
                .note(h.getNote())
                .rejectReason(h.getRejectReason())
                .createdAt(h.getCreatedAt())
                .resolvedAt(h.getResolvedAt())
                .build();
    }
}
