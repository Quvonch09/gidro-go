package uz.gidrogo.modules.product;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.security.UserPrincipal;
import uz.gidrogo.modules.product.dto.ProductDtos.*;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ServiceRegionRepository serviceRegionRepository;

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Mahsulot yaratish faqat Manager vakolatida (Boss narxni o'zgartira olmaydi)");
        }

        Long farmId = currentUser.getFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma identifikatori topilmadi");
        }

        Product product = Product.builder()
                .farmId(farmId)
                .name(request.getName())
                .price(request.getPrice())
                .volumeLiters(request.getVolumeLiters() != null ? request.getVolumeLiters() : BigDecimal.valueOf(19.0))
                .depositPrice(request.getDepositPrice() != null ? request.getDepositPrice() : BigDecimal.ZERO)
                .imageUrl(request.getImageUrl())
                .description(request.getDescription())
                .active(true)
                .build();

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Mahsulot narxi va parametrlarini o'zgartirish faqat Manager vakolatida");
        }

        Long farmId = currentUser.getFarmId();
        Product product = productRepository.findByIdAndFarmId(productId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Mahsulot topilmadi yoki boshqa fermaga tegishli"));

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getVolumeLiters() != null) {
            product.setVolumeLiters(request.getVolumeLiters());
        }
        if (request.getDepositPrice() != null) {
            product.setDepositPrice(request.getDepositPrice());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    public List<ProductResponse> getProductsByFarm(Long farmId) {
        return productRepository.findAllByFarmIdAndActiveTrue(farmId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ProductResponse> getAllProductsForManager() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw new BadRequestException("Ferma topilmadi");
        }
        return productRepository.findAllByFarmId(farmId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ServiceRegionResponse createServiceRegion(ServiceRegionCreateRequest request) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Xizmat hududlarini belgilash faqat Manager vakolatida");
        }

        ServiceRegion region = ServiceRegion.builder()
                .farmId(currentUser.getFarmId())
                .regionName(request.getRegionName())
                .polygonJson(request.getPolygonJson())
                .active(true)
                .build();

        region = serviceRegionRepository.save(region);
        return mapToRegionResponse(region);
    }

    @Transactional
    public ServiceRegionResponse toggleServiceRegion(Long regionId) {
        ServiceRegion region = serviceRegionRepository.findById(regionId)
                .orElseThrow(() -> new ResourceNotFoundException("Xizmat hududi topilmadi"));

        region.setActive(!region.isActive());
        region = serviceRegionRepository.save(region);
        return mapToRegionResponse(region);
    }

    public List<ServiceRegionResponse> getFarmRegions(Long farmId) {
        return serviceRegionRepository.findAllByFarmId(farmId).stream()
                .map(this::mapToRegionResponse)
                .toList();
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .farmId(product.getFarmId())
                .name(product.getName())
                .price(product.getPrice())
                .volumeLiters(product.getVolumeLiters())
                .depositPrice(product.getDepositPrice())
                .imageUrl(product.getImageUrl())
                .description(product.getDescription())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .build();
    }

    private ServiceRegionResponse mapToRegionResponse(ServiceRegion region) {
        return ServiceRegionResponse.builder()
                .id(region.getId())
                .farmId(region.getFarmId())
                .regionName(region.getRegionName())
                .polygonJson(region.getPolygonJson())
                .active(region.isActive())
                .createdAt(region.getCreatedAt())
                .build();
    }
}
