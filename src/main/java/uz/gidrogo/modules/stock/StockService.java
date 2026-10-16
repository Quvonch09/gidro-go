package uz.gidrogo.modules.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.order.Order;
import uz.gidrogo.modules.order.OrderItem;
import uz.gidrogo.modules.order.OrderItemRepository;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.order.OrderStatus;
import uz.gidrogo.modules.product.Product;
import uz.gidrogo.modules.product.ProductRepository;
import uz.gidrogo.modules.stock.dto.StockDtos.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final WarehouseStockRepository warehouseStockRepository;
    private final VehicleStockRepository vehicleStockRepository;
    private final RestockLogRepository restockLogRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final org.springframework.context.ApplicationContext applicationContext;

    private uz.gidrogo.modules.courier.CourierDispatchService getCourierDispatchService() {
        return applicationContext.getBean(uz.gidrogo.modules.courier.CourierDispatchService.class);
    }

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

    public uz.gidrogo.modules.courier.CourierDtos.CourierStockResponse getCourierVehicleStock(Long courierId) {
        List<uz.gidrogo.modules.courier.CourierDtos.CourierStockItem> items = new ArrayList<>();
        List<VehicleStock> stocks = vehicleStockRepository.findAllByCourierId(courierId);

        for (VehicleStock vs : stocks) {
            Product product = productRepository.findById(vs.getProductId()).orElse(null);
            BigDecimal reserved = getReservedStock(courierId, vs.getProductId());
            BigDecimal available = vs.getQuantity().subtract(reserved).max(BigDecimal.ZERO);

            items.add(uz.gidrogo.modules.courier.CourierDtos.CourierStockItem.builder()
                    .productId(vs.getProductId())
                    .productName(product != null ? product.getName() : "")
                    .volumeLiters(product != null && product.getVolumeLiters() != null ? product.getVolumeLiters().doubleValue() : 18.9)
                    .quantity(vs.getQuantity())
                    .reserved(reserved)
                    .available(available)
                    .build());
        }

        return uz.gidrogo.modules.courier.CourierDtos.CourierStockResponse.builder()
                .items(items)
                .build();
    }

    public BigDecimal getReservedStock(Long courierId, Long productId) {
        List<Order> activeOrders = orderRepository.findAllByCourierIdAndStatusIn(
                courierId,
                List.of(OrderStatus.ASSIGNED, OrderStatus.ON_THE_WAY, OrderStatus.NEARBY, OrderStatus.PROBLEM)
        );

        BigDecimal reserved = BigDecimal.ZERO;
        for (Order o : activeOrders) {
            List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(o.getId());
            for (OrderItem oi : orderItems) {
                if (oi.getProductId().equals(productId) && oi.getQuantity() != null) {
                    reserved = reserved.add(oi.getQuantity());
                }
            }
        }
        return reserved;
    }

    public BigDecimal getAvailableStock(Long courierId, Long productId) {
        BigDecimal total = vehicleStockRepository.findByCourierIdAndProductId(courierId, productId)
                .map(VehicleStock::getQuantity)
                .orElse(BigDecimal.ZERO);
        BigDecimal reserved = getReservedStock(courierId, productId);
        return total.subtract(reserved).max(BigDecimal.ZERO);
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

        // 4-talab: restock qayta urinishni (PREPARING retry) ishga tushiradi
        try {
            getCourierDispatchService().onCourierRestocked(courierId, product.getFarmId());
        } catch (Exception e) {
            // non-blocking
        }

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

    @Transactional
    public void adjustVehicleStockManual(Long courierId, Long productId, BigDecimal newQuantity) {
        VehicleStock stock = vehicleStockRepository.findByCourierIdAndProductId(courierId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Mashina zaxirasi topilmadi"));

        BigDecimal reserved = getReservedStock(courierId, productId);
        if (newQuantity.compareTo(reserved) < 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "BELOW_RESERVED: Qoldiqni band qilingan miqdordan (" + reserved + ") pastga tushirib bo'lmaydi"
            );
        }

        stock.setQuantity(newQuantity);
        stock.setUpdatedAt(Instant.now());
        vehicleStockRepository.save(stock);
    }

    public boolean hasSufficientStock(Long courierId, Long productId, BigDecimal requiredQty) {
        return getAvailableStock(courierId, productId).compareTo(requiredQty) >= 0;
    }
}
