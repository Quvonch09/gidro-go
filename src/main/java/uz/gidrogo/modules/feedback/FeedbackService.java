package uz.gidrogo.modules.feedback;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.PageResponse;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.feedback.FeedbackDtos.*;
import uz.gidrogo.modules.order.Order;
import uz.gidrogo.modules.order.OrderRepository;
import uz.gidrogo.websocket.WebSocketEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Set<String> CATEGORIES = Set.of("SERVICE", "DELIVERY", "PRODUCT", "COURIER", "APP", "PAYMENT", "OTHER");
    private static final Set<String> STATUSES = Set.of("NEW", "SEEN", "RESOLVED");

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    private final WebSocketEventPublisher eventPublisher;
    private final uz.gidrogo.modules.notification.StaffNotificationService staffNotificationService;

    /** Mijoz feedback'i: mijoz biriktirilgan fermaga boradi */
    @Transactional
    public FeedbackResponse createFromClient(FeedbackCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("CLIENT_NOT_FOUND", "Mijoz profili topilmadi"));

        Long farmId = client.getFarmId();
        if (request.getOrderId() != null) {
            Order order = orderRepository.findById(request.getOrderId())
                    .filter(o -> client.getId().equals(o.getClientId()))
                    .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Buyurtma topilmadi yoki sizga tegishli emas"));
            farmId = order.getFarmId();
        }
        if (farmId == null) {
            throw ApiException.conflict("FARM_NOT_SELECTED", "Feedback yuborish uchun avval fermani tanlang");
        }
        return create(farmId, "CLIENT", userId, request);
    }

    /** Kuryer feedback'i: kuryer fermasiga boradi */
    @Transactional
    public FeedbackResponse createFromCourier(FeedbackCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (request.getOrderId() != null) {
            orderRepository.findById(request.getOrderId())
                    .filter(o -> userId.equals(o.getCourierId()))
                    .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Buyurtma topilmadi yoki sizga biriktirilmagan"));
        }
        if (farmId == null) {
            throw ApiException.conflict("FARM_NOT_FOUND", "Kuryer fermaga biriktirilmagan");
        }
        return create(farmId, "COURIER", userId, request);
    }

    public PageResponse<FeedbackResponse> getMine(int page, int size) {
        Page<Feedback> list = feedbackRepository.findAllByAuthorUserIdOrderByCreatedAtDesc(
                SecurityUtils.getCurrentUserId(), pageRequest(page, size, Sort.unsorted()));
        return PageResponse.of(list, this::toResponse);
    }

    /** Manager/Boss: fermaning feedback'lari (status va manba bo'yicha filtr) */
    public PageResponse<FeedbackResponse> getFarmFeedback(String status, String source, String category, int page, int size) {
        Long farmId = requireFarm();
        Specification<Feedback> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("farmId"), farmId));
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                p.add(cb.equal(root.get("status"), normalize(status, STATUSES, "status")));
            }
            if (source != null && !source.isBlank() && !"ALL".equalsIgnoreCase(source)) {
                p.add(cb.equal(root.get("source"), normalize(source, Set.of("CLIENT", "COURIER"), "source")));
            }
            if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
                p.add(cb.equal(root.get("category"), normalize(category, CATEGORIES, "category")));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<Feedback> list = feedbackRepository.findAll(spec, pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(list, this::toResponse);
    }

    public FeedbackResponse getFarmFeedbackById(Long id) {
        return toResponse(findInFarm(id));
    }

    public FeedbackSummaryResponse getSummary() {
        Long farmId = requireFarm();
        return FeedbackSummaryResponse.builder()
                .newCount(feedbackRepository.countByFarmIdAndStatus(farmId, "NEW"))
                .seenCount(feedbackRepository.countByFarmIdAndStatus(farmId, "SEEN"))
                .resolvedCount(feedbackRepository.countByFarmIdAndStatus(farmId, "RESOLVED"))
                .build();
    }

    @Transactional
    public FeedbackResponse update(Long id, FeedbackUpdateRequest request) {
        Feedback feedback = findInFarm(id);
        feedback.setStatus(normalize(request.getStatus(), STATUSES, "status"));
        if (request.getManagerNote() != null) {
            feedback.setManagerNote(request.getManagerNote().isBlank() ? null : request.getManagerNote().trim());
        }
        feedback.setHandledBy(SecurityUtils.getCurrentUserId());
        feedback.setHandledAt(Instant.now());
        feedback = feedbackRepository.save(feedback);

        FeedbackResponse response = toResponse(feedback);
        eventPublisher.publishFarmEvent(feedback.getFarmId(), "feedback", "FEEDBACK_UPDATED", response);
        return response;
    }

    private FeedbackResponse create(Long farmId, String source, Long userId, FeedbackCreateRequest request) {
        String category = request.getCategory() == null || request.getCategory().isBlank()
                ? "OTHER" : normalize(request.getCategory(), CATEGORIES, "category");
        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .farmId(farmId)
                .source(source)
                .authorUserId(userId)
                .orderId(request.getOrderId())
                .category(category)
                .message(request.getMessage().trim())
                .build());

        FeedbackResponse response = toResponse(feedback);
        // Manager va Boss paneli: /topic/farm/{farmId}/feedback
        eventPublisher.publishFarmEvent(farmId, "feedback", "FEEDBACK_CREATED", response);
        staffNotificationService.notifyFarmStaff(farmId, "Yangi feedback",
                ("CLIENT".equals(source) ? "Mijoz" : "Kuryer") + " (" + (response.getAuthorName() != null ? response.getAuthorName() : "—") + "): "
                        + (feedback.getMessage().length() > 120 ? feedback.getMessage().substring(0, 120) + "…" : feedback.getMessage()),
                "FEEDBACK_CREATED", feedback.getId());
        return response;
    }

    private Feedback findInFarm(Long id) {
        Long farmId = requireFarm();
        return feedbackRepository.findById(id)
                .filter(f -> farmId.equals(f.getFarmId()))
                .orElseThrow(() -> ApiException.notFound("FEEDBACK_NOT_FOUND", "Feedback topilmadi"));
    }

    private Long requireFarm() {
        Long farmId = SecurityUtils.getCurrentFarmId();
        if (farmId == null) {
            throw ApiException.forbidden("FARM_NOT_FOUND", "Ferma aniqlanmadi");
        }
        return farmId;
    }

    private String normalize(String value, Set<String> allowed, String field) {
        String v = value.trim().toUpperCase();
        if (!allowed.contains(v)) {
            throw ApiException.unprocessable("INVALID_" + field.toUpperCase(), field,
                    "Noto'g'ri qiymat: " + value + ". Ruxsat etilgan: " + String.join(", ", allowed.stream().sorted().toList()));
        }
        return v;
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("INVALID_PARAMETER", "'page' 0 dan, 'size' 1–100 oralig'ida bo'lishi kerak");
        }
        return PageRequest.of(page, size, sort);
    }

    private FeedbackResponse toResponse(Feedback f) {
        User author = userRepository.findById(f.getAuthorUserId()).orElse(null);
        User handler = f.getHandledBy() != null ? userRepository.findById(f.getHandledBy()).orElse(null) : null;
        String orderNumber = f.getOrderId() != null
                ? orderRepository.findById(f.getOrderId()).map(Order::getOrderNumber).orElse(null) : null;
        return FeedbackResponse.builder()
                .id(f.getId())
                .farmId(f.getFarmId())
                .source(f.getSource())
                .authorUserId(f.getAuthorUserId())
                .authorName(author != null ? author.getFullName() : null)
                .authorPhone(author != null ? author.getPhone() : null)
                .orderId(f.getOrderId())
                .orderNumber(orderNumber)
                .category(f.getCategory())
                .message(f.getMessage())
                .status(f.getStatus())
                .managerNote(f.getManagerNote())
                .handledBy(f.getHandledBy())
                .handledByName(handler != null ? handler.getFullName() : null)
                .handledAt(f.getHandledAt())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
