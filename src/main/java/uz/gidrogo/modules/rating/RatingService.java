package uz.gidrogo.modules.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.BadRequestException;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.order.Order;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.modules.order.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRatingRequest {
        @NotNull(message = "Buyurtma ID kiritilishi shart")
        private Long orderId;

        @NotBlank(message = "Target turi (COURIER yoki FARM) kiritilishi shart")
        private String targetType;

        @NotNull(message = "Target ID kiritilishi shart")
        private Long targetId;

        @Min(value = 1, message = "Baholash kamida 1 yulduz bo'lishi kerak")
        @Max(value = 5, message = "Baholash ko'pi bilan 5 yulduz bo'lishi kerak")
        private int stars;

        private String comment;
    }

    @Transactional
    public void submitRating(CreateRatingRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz topilmadi"));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        if (!order.getClientId().equals(client.getId())) {
            throw new BadRequestException("Ushbu buyurtma sizga tegishli emas");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("Faqat yakunlangan buyurtmalarni baholash mumkin");
        }

        Rating rating = Rating.builder()
                .orderId(order.getId())
                .clientId(client.getId())
                .targetType(request.getTargetType().toUpperCase())
                .targetId(request.getTargetId())
                .stars(request.getStars())
                .comment(request.getComment())
                .build();

        ratingRepository.save(rating);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderReviewRequest {
        @Min(value = 1, message = "Kuryer bahosi kamida 1 yulduz bo'lishi kerak")
        @Max(value = 5, message = "Kuryer bahosi ko'pi bilan 5 yulduz bo'lishi kerak")
        private Integer courierStars;

        @Min(value = 1, message = "Suv sifati bahosi kamida 1 yulduz bo'lishi kerak")
        @Max(value = 5, message = "Suv sifati bahosi ko'pi bilan 5 yulduz bo'lishi kerak")
        private Integer waterStars;

        private String comment;
    }

    @Transactional
    public void submitOrderReview(Long orderId, OrderReviewRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Mijoz topilmadi"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyurtma topilmadi"));

        if (!order.getClientId().equals(client.getId())) {
            throw new BadRequestException("Ushbu buyurtma sizga tegishli emas");
        }

        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Faqat yetkazilgan yoki yakunlangan buyurtmalarni baholash mumkin");
        }

        if (request.getCourierStars() != null && order.getCourierId() != null) {
            ratingRepository.save(Rating.builder()
                    .orderId(order.getId())
                    .clientId(client.getId())
                    .targetType("COURIER")
                    .targetId(order.getCourierId())
                    .stars(request.getCourierStars())
                    .comment(request.getComment())
                    .build());
        }

        if (request.getWaterStars() != null && order.getFarmId() != null) {
            ratingRepository.save(Rating.builder()
                    .orderId(order.getId())
                    .clientId(client.getId())
                    .targetType("FARM")
                    .targetId(order.getFarmId())
                    .stars(request.getWaterStars())
                    .comment(request.getComment())
                    .build());
        }
    }

    public Double getAverageRating(String targetType, Long targetId) {
        Double avg = ratingRepository.getAverageStars(targetType.toUpperCase(), targetId);
        if (avg == null) {
            return 5.0;
        }
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
