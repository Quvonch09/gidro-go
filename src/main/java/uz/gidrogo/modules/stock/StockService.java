package uz.gidrogo.modules.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.product.Product;
import uz.gidrogo.modules.product.ProductRepository;
import uz.gidrogo.modules.stock.dto.StockDtos.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final WarehouseStockRepository warehouseStockRepository;
    private final VehicleStockRepository vehicleStockRepository;
    private final RestockLogRepository restockLogRepository;
    private final ProductRepository productRepository;

    public List<StockItemResponse> getWarehouseStock(Long farmId) {
        return warehouseStockRepository.findAllByFarmId(farmId).stream()
                .map(ws -> {
                    Product product = productRepository.findById(ws.getProductId()).orElse(null);
                    return StockItemResponse.builder()
                            .id(ws.getId())
                            .productId(ws.getProductId())
                            .productName(product != null ? product.getName() : null)
                            .quantity(ws.getQuantity())
                            .updatedAt(ws.getUpdatedAt())
                            .build();
                }).toList();
    }

    public BigDecimal getTotalWarehouseBottles(Long farmId) {
        return warehouseStockRepository.findAllByFarmId(farmId).stream()
                .map(WarehouseStock::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public StockItemResponse updateWarehouseStock(Long farmId, Long productId, BigDecimal quantity) {
        Product product = productRepository.findByIdAndFarmId(productId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Mahsulot topilmadi"));

        WarehouseStock stock = warehouseStockRepository.findByFarmIdAndProductId(farmId, productId)
                .orElse(WarehouseStock.builder()
                        .farmId(farmId)
                        .productId(productId)
                        .quantity(BigDecimal.ZERO)
                        .build());

        stock.setQuantity(quantity);
        stock.setUpdatedAt(Instant.now());
        stock = warehouseStockRepository.save(stock);

        return StockItemResponse.builder()
                .id(stock.getId())
                .productId(productId)
                .productName(product.getName())
                .quantity(stock.getQuantity())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    public List<StockItemResponse> getVehicleStock(Long courierId) {
        return vehicleStockRepository.findAllByCourierId(courierId).stream()
                .map(vs -> {
                    Product product = productRepository.findById(vs.getProductId()).orElse(null);
                    return StockItemResponse.builder()
                            .id(vs.getId())
                            .productId(vs.getProductId())
                            .productName(product != null ? product.getName() : null)
                            .quantity(vs.getQuantity())
                            .updatedAt(vs.getUpdatedAt())
                            .build();
                }).toList();
    }

    @Transactional
    public StockItemResponse restockVehicle(Long courierId, Long productId, BigDecimal quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Mahsulot topilmadi"));

        VehicleStock stock = vehicleStockRepository.findByCourierIdAndProductId(courierId, productId)
                .orElse(VehicleStock.builder()
                        .courierId(courierId)
                        .productId(productId)
                        .quantity(BigDecimal.ZERO)
                        .build());

        stock.setQuantity(stock.getQuantity().add(quantity));
        stock.setUpdatedAt(Instant.now());
        stock = vehicleStockRepository.save(stock);

        // Audit log
        restockLogRepository.save(RestockLog.builder()
                .courierId(courierId)
                .productId(productId)
                .quantity(quantity)
                .build());

        return StockItemResponse.builder()
                .id(stock.getId())
                .productId(productId)
                .productName(product.getName())
                .quantity(stock.getQuantity())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    @Transactional
    public void deductVehicleStock(Long courierId, Long productId, BigDecimal quantity) {
        VehicleStock stock = vehicleStockRepository.findByCourierIdAndProductId(courierId, productId)
                .orElseThrow(() -> new BadRequestException("Mashinada ushbu mahsulot zaxirasi topilmadi"));

        if (stock.getQuantity().compareTo(quantity) < 0) {
            throw new BadRequestException("Mashinada yetarli mahsulot yo'q");
        }

        stock.setQuantity(stock.getQuantity().subtract(quantity));
        stock.setUpdatedAt(Instant.now());
        vehicleStockRepository.save(stock);
    }

    public boolean hasSufficientStock(Long courierId, Long productId, BigDecimal requiredQty) {
        return vehicleStockRepository.findByCourierIdAndProductId(courierId, productId)
                .map(vs -> vs.getQuantity().compareTo(requiredQty) >= 0)
                .orElse(false);
    }
}
