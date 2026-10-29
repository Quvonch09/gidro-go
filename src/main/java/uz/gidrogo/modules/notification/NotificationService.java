package uz.gidrogo.modules.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ResourceNotFoundException;
import uz.gidrogo.common.SecurityUtils;
import uz.gidrogo.modules.client.Client;
import uz.gidrogo.modules.client.ClientRepository;
import uz.gidrogo.modules.notification.dto.NotificationDtos.*;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final CourierNotificationRepository notificationRepository;
    private final ClientNotificationRepository clientNotificationRepository;
    private final ClientRepository clientRepository;
    private final uz.gidrogo.websocket.WebSocketEventPublisher eventPublisher;

    public NotificationPageResponse getCourierNotifications(int page, int size, boolean unreadOnly) {
        Long courierId = SecurityUtils.getCurrentUserId();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, size));

        Page<CourierNotification> paged = unreadOnly
                ? notificationRepository.findAllByCourierIdAndIsReadFalseOrderByCreatedAtDesc(courierId, pageRequest)
                : notificationRepository.findAllByCourierIdOrderByCreatedAtDesc(courierId, pageRequest);

        long unreadCount = notificationRepository.countByCourierIdAndIsReadFalse(courierId);

        List<NotificationItemResponse> items = paged.getContent().stream()
                .map(this::mapToItem)
                .toList();

        return NotificationPageResponse.builder()
                .content(items)
                .totalElements(paged.getTotalElements())
                .totalPages(paged.getTotalPages())
                .currentPage(paged.getNumber())
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long notificationId) {
        Long courierId = SecurityUtils.getCurrentUserId();
        CourierNotification notification = notificationRepository.findByIdAndCourierId(notificationId, courierId)
                .orElseThrow(() -> new ResourceNotFoundException("Bildirishnoma topilmadi"));

        notification.setIsRead(true);
        notificationRepository.save(notification);

        return NotificationReadResponse.builder()
                .id(notification.getId())
                .isRead(true)
                .build();
    }

    @Transactional
    public boolean markAllAsRead() {
        Long courierId = SecurityUtils.getCurrentUserId();
        notificationRepository.markAllAsReadByCourierId(courierId);
        return true;
    }

    public UnreadCountResponse getUnreadCount() {
        Long courierId = SecurityUtils.getCurrentUserId();
        long unreadCount = notificationRepository.countByCourierIdAndIsReadFalse(courierId);
        return UnreadCountResponse.builder()
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional
    public void createNotification(Long courierId, String title, String message, String type, Long referenceId) {
        try {
            CourierNotification notification = CourierNotification.builder()
                    .courierId(courierId)
                    .title(title)
                    .message(message)
                    .type(type != null ? type : "SYSTEM")
                    .referenceId(referenceId)
                    .isRead(false)
                    .build();
            notification = notificationRepository.save(notification);
            eventPublisher.publishNotification(courierId, mapToItem(notification));
        } catch (Exception e) {
            log.warn("Bildirishnoma saqlashda xatolik courier {}: {}", courierId, e.getMessage());
        }
    }

    private NotificationItemResponse mapToItem(CourierNotification n) {
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

    public NotificationPageResponse getClientNotifications(int page, int size, boolean unreadOnly) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mijoz profili topilmadi"));
        Long clientId = client.getId();

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<ClientNotification> paged = unreadOnly
                ? clientNotificationRepository.findAllByClientIdAndIsReadFalseOrderByCreatedAtDesc(clientId, pageRequest)
                : clientNotificationRepository.findAllByClientIdOrderByCreatedAtDesc(clientId, pageRequest);

        long unreadCount = clientNotificationRepository.countByClientIdAndIsReadFalse(clientId);

        List<NotificationItemResponse> items = paged.getContent().stream()
                .map(this::mapClientNotificationToItem)
                .toList();

        return NotificationPageResponse.builder()
                .content(items)
                .totalElements(paged.getTotalElements())
                .totalPages(paged.getTotalPages())
                .currentPage(paged.getNumber())
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional
    public NotificationReadResponse markClientNotificationAsRead(Long notificationId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mijoz profili topilmadi"));

        ClientNotification notification = clientNotificationRepository.findByIdAndClientId(notificationId, client.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bildirishnoma topilmadi"));

        notification.setIsRead(true);
        clientNotificationRepository.save(notification);

        return NotificationReadResponse.builder()
                .id(notification.getId())
                .isRead(true)
                .build();
    }

    @Transactional
    public boolean markAllClientNotificationsAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mijoz profili topilmadi"));
        clientNotificationRepository.markAllAsReadByClientId(client.getId());
        return true;
    }

    public UnreadCountResponse getClientUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        Client client = clientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Mijoz profili topilmadi"));
        long unreadCount = clientNotificationRepository.countByClientIdAndIsReadFalse(client.getId());
        return UnreadCountResponse.builder()
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional
    public void createClientNotification(Long clientId, String title, String message, String type, Long referenceId) {
        try {
            ClientNotification notification = ClientNotification.builder()
                    .clientId(clientId)
                    .title(title)
                    .message(message)
                    .type(type != null ? type : "SYSTEM")
                    .referenceId(referenceId)
                    .isRead(false)
                    .build();
            clientNotificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Mijoz bildirishnomasini saqlashda xatolik client {}: {}", clientId, e.getMessage());
        }
    }

    private NotificationItemResponse mapClientNotificationToItem(ClientNotification n) {
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
