package uz.gidrogo.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class WebSocketDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WsEvent<T> {
        private String eventType; // ORDER_CREATED, ORDER_ASSIGNED, ORDER_UPDATED, LOCATION_UPDATED, NOTIFICATION, DASHBOARD_UPDATED, STOCK_UPDATED
        private String topic;
        private Long farmId;
        private T data;
        @Builder.Default
        private Instant timestamp = Instant.now();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WsLocationPayload {
        private Double latitude;
        private Double longitude;
        private Float accuracy;
        private Float bearing;
        private Float speed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WsStatusPayload {
        private Boolean online;
        private String status;
    }
}
