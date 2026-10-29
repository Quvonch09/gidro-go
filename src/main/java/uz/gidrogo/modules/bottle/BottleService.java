package uz.gidrogo.modules.bottle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.bottle.BottleDtos.*;
import uz.gidrogo.modules.farm.Farm;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.order.dto.OrderDtos.CartCheckoutRequest;
import uz.gidrogo.modules.order.dto.OrderDtos.CartItemDto;
import uz.gidrogo.modules.order.dto.OrderDtos.CartQuoteResponse;
import uz.gidrogo.modules.order.dto.OrderDtos.TaraLineDto;
import uz.gidrogo.modules.product.Product;
import uz.gidrogo.modules.product.ProductRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BottleService {

    private final ClientBottleBalanceRepository balanceRepository;
    private final BottleLedgerRepository ledgerRepository;
    private final ProductRepository productRepository;
    private final FarmRepository farmRepository;
    private final uz.gidrogo.modules.client.ClientRepository clientRepository;

    private Long resolveClientId(Long userIdOrClientId) {
        if (userIdOrClientId == null) return null;
        return clientRepository.findByUserId(userIdOrClientId)
                .map(uz.gidrogo.modules.client.Client::getId)
                .orElse(userIdOrClientId);
    }

    @Transactional(readOnly = true)
    public CartQuoteResponse calculateQuote(Long clientUserId, CartCheckoutRequest request) {
        final Long clientId = resolveClientId(clientUserId);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Savat bo'sh bo'lishi mumkin emas");
        }

        BigDecimal productsSum = BigDecimal.ZERO;
        BigDecimal taraSum = BigDecimal.ZERO;
        List<TaraLineDto> taraLines = new ArrayList<>();

        for (CartItemDto item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Mahsulot topilmadi: " + item.getProductId()));

            BigDecimal itemSum = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            productsSum = productsSum.add(itemSum);

            if (product.isReturnable()) {
                int clientBalance = 0;
                if (clientId != null) {
                    clientBalance = balanceRepository
                            .findByClientIdAndFarmIdAndProductId(clientId, product.getFarmId(), product.getId())
                            .map(ClientBottleBalance::getBalance)
                            .orElse(0);
                }

                int orderedQty = item.getQuantity();
                int taraQty = Math.max(0, orderedQty - clientBalance);
                BigDecimal unitTaraPrice = product.getTaraPrice() != null ? product.getTaraPrice() :
                        (product.getDepositPrice() != null ? product.getDepositPrice() : BigDecimal.ZERO);

                BigDecimal lineSum = unitTaraPrice.multiply(BigDecimal.valueOf(taraQty));
                taraSum = taraSum.add(lineSum);

                taraLines.add(TaraLineDto.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .qty(taraQty)
                        .unitPrice(unitTaraPrice)
                        .sum(lineSum)
                        .build());
            }
        }

        BigDecimal totalSum = productsSum.add(taraSum);

        return CartQuoteResponse.builder()
                .productsSum(productsSum)
                .taraLines(taraLines)
                .totalSum(totalSum)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ClientBottleBalanceResponse> getClientBottleBalances(Long clientUserId) {
        Long clientId = resolveClientId(clientUserId);
        List<ClientBottleBalance> list = balanceRepository.findAllByClientId(clientId);
        List<ClientBottleBalanceResponse> result = new ArrayList<>();

        for (ClientBottleBalance b : list) {
            Farm farm = farmRepository.findById(b.getFarmId()).orElse(null);
            Product product = productRepository.findById(b.getProductId()).orElse(null);

            result.add(ClientBottleBalanceResponse.builder()
                    .clientId(b.getClientId())
                    .farmId(b.getFarmId())
                    .farmName(farm != null ? farm.getName() : "Ferma #" + b.getFarmId())
                    .productId(b.getProductId())
                    .productName(product != null ? product.getName() : "Mahsulot #" + b.getProductId())
                    .balance(b.getBalance())
                    .updatedAt(b.getUpdatedAt())
                    .build());
        }
        return result;
    }

    @Transactional
    public ClientBottleBalanceResponse adjustBottleBalance(Long managerId, Long farmId, Long rawClientId, BottleAdjustRequest request) {
        final Long clientId = resolveClientId(rawClientId);
        if (request.getNote() == null || request.getNote().isBlank()) {
            throw new BadRequestException("Izoh (note) majburiy");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Mahsulot topilmadi"));

        ClientBottleBalance balance = balanceRepository
                .findByClientIdAndFarmIdAndProductId(clientId, farmId, product.getId())
                .orElseGet(() -> ClientBottleBalance.builder()
                        .clientId(clientId)
                        .farmId(farmId)
                        .productId(product.getId())
                        .balance(0)
                        .updatedAt(Instant.now())
                        .build());

        int newBalance = balance.getBalance() + request.getDelta();
        if (newBalance < 0) {
            throw new BadRequestException("Idish balansi 0 dan pastga tushishi mumkin emas");
        }

        balance.setBalance(newBalance);
        balance.setUpdatedAt(Instant.now());
        balance = balanceRepository.save(balance);

        ledgerRepository.save(BottleLedger.builder()
                .clientId(clientId)
                .farmId(farmId)
                .productId(product.getId())
                .delta(request.getDelta())
                .reason("MANUAL_ADJUST")
                .actorId(managerId)
                .note(request.getNote())
                .build());

        Farm farm = farmRepository.findById(farmId).orElse(null);

        return ClientBottleBalanceResponse.builder()
                .clientId(balance.getClientId())
                .farmId(farmId)
                .farmName(farm != null ? farm.getName() : "")
                .productId(product.getId())
                .productName(product.getName())
                .balance(balance.getBalance())
                .updatedAt(balance.getUpdatedAt())
                .build();
    }

    @Transactional
    public void recordDeliveryBottleBalance(Long orderId, Long clientId, Long farmId, Long productId, int fullQty, int emptyReturned) {
        ClientBottleBalance balance = balanceRepository
                .findByClientIdAndFarmIdAndProductId(clientId, farmId, productId)
                .orElseGet(() -> ClientBottleBalance.builder()
                        .clientId(clientId)
                        .farmId(farmId)
                        .productId(productId)
                        .balance(0)
                        .updatedAt(Instant.now())
                        .build());

        int newBalance = balance.getBalance() + fullQty - emptyReturned;
        balance.setBalance(Math.max(0, newBalance));
        balance.setUpdatedAt(Instant.now());
        balanceRepository.save(balance);

        ledgerRepository.save(BottleLedger.builder()
                .clientId(clientId)
                .farmId(farmId)
                .productId(productId)
                .delta(fullQty)
                .reason("DELIVERED_FULL")
                .orderId(orderId)
                .build());

        if (emptyReturned > 0) {
            ledgerRepository.save(BottleLedger.builder()
                    .clientId(clientId)
                    .farmId(farmId)
                    .productId(productId)
                    .delta(-emptyReturned)
                    .reason("EMPTY_RETURNED")
                    .orderId(orderId)
                    .build());
        }
    }
}
