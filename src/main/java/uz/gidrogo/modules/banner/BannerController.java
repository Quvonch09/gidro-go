package uz.gidrogo.modules.banner;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.gidrogo.common.ApiResponse;
import uz.gidrogo.modules.banner.BannerDtos.BannerResponse;

import java.util.List;

@RestController
@RequestMapping("/api/client/banners")
@RequiredArgsConstructor
@Tag(name = "Client Banner API", description = "Mijoz bosh sahifasi promo bannerlari va aksiyalar")
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    @Operation(summary = "Faol promo-aksiyalar va slayder bannerlarini olish (Ochiq API)")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getBanners(
            @RequestParam(required = false) Long farmId,
            @RequestParam(defaultValue = "HOME_SLIDER") String type) {
        List<BannerResponse> banners = bannerService.getActiveBanners(farmId, type);
        return ResponseEntity.ok(ApiResponse.ok("Faol bannerlar muvaffaqiyatli yuklandi", banners));
    }
}
