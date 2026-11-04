package uz.gidrogo.modules.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.auth.User;
import uz.gidrogo.modules.auth.UserRepository;
import uz.gidrogo.modules.farm.FarmRepository;
import uz.gidrogo.modules.notification.dto.NotificationDtos.*;
import uz.gidrogo.websocket.WebSocketEventPublisher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Band 24: Manager va Boss bildirishnomalari — kuryer to'plamining nusxasi, joriy foydalanuvchi bo'yicha chegaralangan */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffNotificationService {

    private final StaffNotificationRepository repository;
    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final WebSocketEventPublisher eventPublisher;

    public NotificationPageResponse getMine(int page, int size, boolean unreadOnly) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<StaffNotification> paged = unreadOnly
                ? repository.findAllByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageRequest)
                : repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        return NotificationPageResponse.builder()
                .content(paged.getContent().stream().map(this::toItem).toList())
                .totalElements(paged.getTotalElements())
                .totalPages(paged.getTotalPages())
                .currentPage(paged.getNumber())
                .unreadCount(repository.countByUserIdAndIsReadFalse(userId))
                .build();
    }

    public UnreadCountResponse getUnreadCount() {
        return UnreadCountResponse.builder()
                .unreadCount(repository.countByUserIdAndIsReadFalse(SecurityUtils.getCurrentUserId()))
                .build();
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long id) {
        StaffNotification n = repository.findByIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Bildirishnoma topilmadi"));
        n.setIsRead(true);
        repository.save(n);
        return NotificationReadResponse.builder().id(n.getId()).isRead(true).build();
    }

    @Transactional
    public boolean markAllAsRead() {
        repository.markAllAsReadByUserId(SecurityUtils.getCurrentUserId());
        return true;
    }

    /** Fermaning barcha faol Manager'lari va Boss'iga */
    @Transactional
    public void notifyFarmStaff(Long farmId, String title, String message, String type, Long referenceId) {
        notify(farmStaff(farmId, true), farmId, title, message, type, referenceId);
    }

    /** Faqat ferma Boss'iga */
    @Transactional
    public void notifyFarmBoss(Long farmId, String title, String message, String type, Long referenceId) {
        notify(farmStaff(farmId, false), farmId, title, message, type, referenceId);
    }

    private void notify(Set<Long> userIds, Long farmId, String title, String message, String type, Long referenceId) {
        for (Long userId : userIds) {
            StaffNotification n = repository.save(StaffNotification.builder()
                    .userId(userId)
                    .farmId(farmId)
                    .title(title)
                    .message(message)
                    .type(type)
                    .referenceId(referenceId)
                    .build());
            // Panel ochiq bo'lsa darhol: /user/queue/notifications
            eventPublisher.publishUserNotification(userId, toItem(n));
        }
        if (userIds.isEmpty()) {
            log.warn("Ferma {} da faol Manager/Boss yo'q — {} bildirishnomasi hech kimga yuborilmadi", farmId, type);
        }
    }

    private Set<Long> farmStaff(Long farmId, boolean withManagers) {
        Set<Long> ids = new LinkedHashSet<>();
        if (farmId == null) return ids;
        List<User> users = userRepository.findAllByFarmId(farmId);
        for (User u : users) {
            boolean target = u.getRole() == Role.BOSS || (withManagers && u.getRole() == Role.MANAGER);
            if (target && "ACTIVE".equalsIgnoreCase(u.getStatus())) ids.add(u.getId());
        }
        farmRepository.findById(farmId).map(f -> f.getBossUserId()).ifPresent(bossId ->
                userRepository.findById(bossId)
                        .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                        .ifPresent(b -> ids.add(b.getId())));
        return ids;
    }

    private NotificationItemResponse toItem(StaffNotification n) {
        return NotificationItemResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
