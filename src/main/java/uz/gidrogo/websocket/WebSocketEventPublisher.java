package uz.gidrogo.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import uz.gidrogo.modules.courier.CourierDtos.CourierTrackingResponse;
import uz.gidrogo.modules.notification.dto.NotificationDtos.NotificationItemResponse;
import uz.gidrogo.modules.order.dto.OrderDtos.OrderResponse;
import uz.gidrogo.websocket.WebSocketDtos.WsEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Yangi buyurtma yaratilganda ferma menejeri va bossiga xabar yuborish
     */
    public void publishOrderCreated(Long farmId, OrderResponse order) {
        if (farmId == null || order == null) return;
        String topic = "/topic/farm/" + farmId + "/orders";
        WsEvent<OrderResponse> event = WsEvent.<OrderResponse>builder()
                .eventType("ORDER_CREATED")
                .topic(topic)
                .farmId(farmId)
                .data(order)
                .build();
        send(topic, event);

        // Shuningdek, fermaning dashboard ko'rsatkichlarini yangilash uchun xabar
        publishDashboardChanged(farmId, "ORDER_CREATED");
    }

    /**
     * Buyurtma dastavkachiga biriktirilganda (Kuryer ilovasida 30 soniyalik taklif oynasi chiqishi uchun)
     */
    public void publishOrderAssigned(Long farmId, Long courierId, OrderResponse order) {
        if (order == null) return;
        // 1. Fermaga xabar
        if (farmId != null) {
            String farmTopic = "/topic/farm/" + farmId + "/orders";
            send(farmTopic, WsEvent.<OrderResponse>builder()
                    .eventType("ORDER_ASSIGNED")
                    .topic(farmTopic)
                    .farmId(farmId)
                    .data(order)
                    .build());
        }

        // 2. Kuryerga shaxsiy kanal orqali tezkor buyurtma taklifi
        if (courierId != null) {
            String courierTopic = "/topic/courier/" + courierId + "/orders";
            send(courierTopic, WsEvent.<OrderResponse>builder()
                    .eventType("NEW_ORDER_OFFER")
                    .topic(courierTopic)
                    .farmId(farmId)
                    .data(order)
                    .build());
        }
    }

    /**
     * Buyurtma holati o'zgarganda (ON_THE_WAY, NEARBY, DELIVERED, COMPLETED, PROBLEM, CANCELLED)
     */
    public void publishOrderStatusChanged(Long farmId, Long courierId, Long clientId, OrderResponse order) {
        if (order == null) return;

        // 1. Ferma xabari (Manager va Boss paneli jonli yangilanadi)
        if (farmId != null) {
            String farmTopic = "/topic/farm/" + farmId + "/orders";
            send(farmTopic, WsEvent.<OrderResponse>builder()
                    .eventType("ORDER_STATUS_CHANGED")
                    .topic(farmTopic)
                    .farmId(farmId)
                    .data(order)
                    .build());
            publishDashboardChanged(farmId, "ORDER_STATUS_CHANGED");
        }

        // 2. Kuryer xabari
        if (courierId != null) {
            String courierTopic = "/topic/courier/" + courierId + "/orders";
            send(courierTopic, WsEvent.<OrderResponse>builder()
                    .eventType("ORDER_STATUS_CHANGED")
                    .topic(courierTopic)
                    .farmId(farmId)
                    .data(order)
                    .build());
        }

        // 3. Mijoz xabari (Mijoz ilovasiga 'Yo'lga chiqdi', 'Yaqinlashdi', 'Yetkazildi')
        if (clientId != null) {
            String clientTopic = "/topic/client/" + clientId + "/orders";
            send(clientTopic, WsEvent.<OrderResponse>builder()
                    .eventType("ORDER_STATUS_CHANGED")
                    .topic(clientTopic)
                    .farmId(farmId)
                    .data(order)
                    .build());
        }

        // 4. Bitta buyurtma sahifasidagi jonli obuna
        if (order.getId() != null) {
            String orderTopic = "/topic/order/" + order.getId();
            send(orderTopic, WsEvent.<OrderResponse>builder()
                    .eventType("ORDER_STATUS_CHANGED")
                    .topic(orderTopic)
                    .farmId(farmId)
                    .data(order)
                    .build());
        }
    }

    /**
     * Kuryer GPS lokatsiyasi yangilanganda jonli xaritaga translyatsiya qilish
     */
    public void publishCourierLocation(Long farmId, Long courierId, CourierTrackingResponse tracking) {
        if (courierId == null || tracking == null) return;

        // 1. Menejer va Boss xaritasi uchun (/topic/farm/{farmId}/couriers)
        if (farmId != null) {
            String farmTopic = "/topic/farm/" + farmId + "/couriers";
            send(farmTopic, WsEvent.<CourierTrackingResponse>builder()
                    .eventType("COURIER_LOCATION")
                    .topic(farmTopic)
                    .farmId(farmId)
                    .data(tracking)
                    .build());
        }

        // 2. Kuryerning shaxsiy lokatsiya kanali
        String courierTopic = "/topic/courier/" + courierId + "/location";
        send(courierTopic, WsEvent.<CourierTrackingResponse>builder()
                .eventType("COURIER_LOCATION")
                .topic(courierTopic)
                .farmId(farmId)
                .data(tracking)
                .build());

        // 3. Agar faol buyurtma bo'lsa, mijoz xaritada mashinaning harakatini ko'rib turadi
        if (tracking.getActiveOrderId() != null) {
            String orderTopic = "/topic/order/" + tracking.getActiveOrderId() + "/courier-location";
            send(orderTopic, WsEvent.<CourierTrackingResponse>builder()
                    .eventType("COURIER_LOCATION")
                    .topic(orderTopic)
                    .farmId(farmId)
                    .data(tracking)
                    .build());
        }
    }

    /**
     * Kuryer online/offline yoki holati o'zgarganda
     */
    public void publishCourierStatusChanged(Long farmId, Long courierId, String status) {
        if (farmId == null) return;
        String topic = "/topic/farm/" + farmId + "/couriers";
        WsEvent<Object> event = WsEvent.builder()
                .eventType("COURIER_STATUS_CHANGED")
                .topic(topic)
                .farmId(farmId)
                .data(java.util.Map.of("courierId", courierId, "status", status))
                .build();
        send(topic, event);
        publishDashboardChanged(farmId, "COURIER_STATUS_CHANGED");
    }

    /**
     * Kuryerga bildirishnoma yuborilganda
     */
    public void publishNotification(Long courierId, NotificationItemResponse notification) {
        if (courierId == null || notification == null) return;
        String topic = "/topic/courier/" + courierId + "/notifications";
        WsEvent<NotificationItemResponse> event = WsEvent.<NotificationItemResponse>builder()
                .eventType("NOTIFICATION")
                .topic(topic)
                .data(notification)
                .build();
        send(topic, event);
    }

    /**
     * Ferma dashboard ko'rsatkichlari o'zgarganda
     */
    public void publishDashboardChanged(Long farmId, String reason) {
        if (farmId == null) return;
        String topic = "/topic/farm/" + farmId + "/dashboard";
        WsEvent<Object> event = WsEvent.builder()
                .eventType("DASHBOARD_UPDATED")
                .topic(topic)
                .farmId(farmId)
                .data(java.util.Map.of("farmId", farmId, "reason", reason))
                .build();
        send(topic, event);
    }

    /**
     * Zaxira (ombordagi yoki mashinadagi) o'zgarganda
     */
    public void publishStockUpdated(Long farmId, Long courierId, Object stockData) {
        if (farmId != null) {
            String farmTopic = "/topic/farm/" + farmId + "/stock";
            send(farmTopic, WsEvent.builder()
                    .eventType("STOCK_UPDATED")
                    .topic(farmTopic)
                    .farmId(farmId)
                    .data(stockData)
                    .build());
        }
        if (courierId != null) {
            String courierTopic = "/topic/courier/" + courierId + "/stock";
            send(courierTopic, WsEvent.builder()
                    .eventType("STOCK_UPDATED")
                    .topic(courierTopic)
                    .farmId(farmId)
                    .data(stockData)
                    .build());
        }
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("WebSocket yuborishda xatolik: destination={}, error={}", destination, e.getMessage());
        }
    }
}
