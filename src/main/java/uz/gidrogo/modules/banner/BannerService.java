package uz.gidrogo.modules.banner;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.modules.banner.BannerDtos.*;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;

    @PostConstruct
    public void init() {
        try {
            if (bannerRepository.count() == 0) {
                Banner defaultBanner = Banner.builder()
                        .badgeText("BEPUL YETKAZIB BERISH")
                        .title("Toza va mineralli suv bir zumda eshigingizda!")
                        .subtitle("Eng yaqin fermalardan 20 daqiqada")
                        .imageUrl(null)
                        .gradientStart("#0284C7")
                        .gradientEnd("#1D61F2")
                        .iconName("water_drop")
                        .actionType("NONE")
                        .actionValue(null)
                        .sortOrder(1)
                        .isActive(true)
                        .type("HOME_SLIDER")
                        .build();
                bannerRepository.save(defaultBanner);
                log.info("Default promo banner created");
            }
        } catch (Exception e) {
            log.warn("Banner init check warning: {}", e.getMessage());
        }
    }

    public List<BannerResponse> getActiveBanners(Long farmId, String type) {
        String targetType = (type != null && !type.isBlank()) ? type.trim() : "HOME_SLIDER";
        List<Banner> banners = bannerRepository.findActiveBanners(targetType, farmId);

        if (banners.isEmpty() && "HOME_SLIDER".equalsIgnoreCase(targetType)) {
            // Agar bazada faol banner topilmasa, default banner qaytaramiz
            return List.of(BannerResponse.builder()
                    .id(1L)
                    .badgeText("BEPUL YETKAZIB BERISH")
                    .title("Toza va mineralli suv bir zumda eshigingizda!")
                    .subtitle("Eng yaqin fermalardan 20 daqiqada")
                    .imageUrl(null)
                    .gradientStart("#0284C7")
                    .gradientEnd("#1D61F2")
                    .iconName("water_drop")
                    .actionType("NONE")
                    .actionValue(null)
                    .sortOrder(1)
                    .isActive(true)
                    .build());
        }

        return banners.stream().map(this::mapToResponse).toList();
    }

    public List<Banner> getAllBanners() {
        return bannerRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional
    public Banner createBanner(BannerCreateRequest req) {
        Banner banner = Banner.builder()
                .badgeText(req.getBadgeText())
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .imageUrl(req.getImageUrl())
                .gradientStart(req.getGradientStart() != null ? req.getGradientStart() : "#0284C7")
                .gradientEnd(req.getGradientEnd() != null ? req.getGradientEnd() : "#1D61F2")
                .iconName(req.getIconName() != null ? req.getIconName() : "water_drop")
                .actionType(req.getActionType() != null ? req.getActionType() : "NONE")
                .actionValue(req.getActionValue())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 1)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .farmId(req.getFarmId())
                .type(req.getType() != null ? req.getType() : "HOME_SLIDER")
                .build();
        return bannerRepository.save(banner);
    }

    @Transactional
    public Banner updateBanner(Long id, BannerUpdateRequest req) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner topilmadi: " + id));

        if (req.getBadgeText() != null) banner.setBadgeText(req.getBadgeText());
        if (req.getTitle() != null) banner.setTitle(req.getTitle());
        if (req.getSubtitle() != null) banner.setSubtitle(req.getSubtitle());
        if (req.getImageUrl() != null) banner.setImageUrl(req.getImageUrl());
        if (req.getGradientStart() != null) banner.setGradientStart(req.getGradientStart());
        if (req.getGradientEnd() != null) banner.setGradientEnd(req.getGradientEnd());
        if (req.getIconName() != null) banner.setIconName(req.getIconName());
        if (req.getActionType() != null) banner.setActionType(req.getActionType());
        if (req.getActionValue() != null) banner.setActionValue(req.getActionValue());
        if (req.getSortOrder() != null) banner.setSortOrder(req.getSortOrder());
        if (req.getIsActive() != null) banner.setIsActive(req.getIsActive());
        if (req.getFarmId() != null) banner.setFarmId(req.getFarmId());
        if (req.getType() != null) banner.setType(req.getType());

        return bannerRepository.save(banner);
    }

    @Transactional
    public void deleteBanner(Long id) {
        if (!bannerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Banner topilmadi: " + id);
        }
        bannerRepository.deleteById(id);
    }

    private BannerResponse mapToResponse(Banner b) {
        return BannerResponse.builder()
                .id(b.getId())
                .badgeText(b.getBadgeText())
                .title(b.getTitle())
                .subtitle(b.getSubtitle())
                .imageUrl(b.getImageUrl())
                .gradientStart(b.getGradientStart())
                .gradientEnd(b.getGradientEnd())
                .iconName(b.getIconName())
                .actionType(b.getActionType())
                .actionValue(b.getActionValue())
                .sortOrder(b.getSortOrder())
                .isActive(b.getIsActive())
                .build();
    }
}
