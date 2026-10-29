package uz.gidrogo.modules.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseMessagingService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.firebase.credentials-path:${GOOGLE_APPLICATION_CREDENTIALS:/etc/gidrogo/firebase-sa.json}}")
    private String credentialsPath;

    @Value("${app.firebase.project-id:gidrogo-fc6b6}")
    private String projectId;

    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                initialized = true;
                log.info("[FCM] FirebaseApp already initialized.");
                return;
            }

            File credFile = new File(credentialsPath);
            if (!credFile.exists()) {
                log.warn("[FCM] Firebase service account credentials file not found at: {}. FCM push disabled.", credentialsPath);
                return;
            }

            try (InputStream serviceAccount = new FileInputStream(credFile)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                initialized = true;
                log.info("[FCM] Firebase initialized successfully with project: {} from {}", projectId, credentialsPath);
            }
        } catch (Exception e) {
            log.error("[FCM] Failed to initialize Firebase: {}", e.getMessage(), e);
        }
    }

    /**
     * Kuryer ilovasi uchun Data-Push yuborish (Android Priority: HIGH, no notification block, collapseKey)
     */
    public boolean sendCourierDataPush(Long courierId, String type, Map<String, String> dataPayload, String collapseKey) {
        if (!initialized) {
            log.debug("[FCM] Cannot send push: Firebase not initialized.");
            return false;
        }

        if (courierId == null) {
            return false;
        }

        String fcmToken = getCourierFcmToken(courierId);
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("[FCM] Courier {} has no registered FCM token in Redis.", courierId);
            return false;
        }

        try {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("type", type);
            if (dataPayload != null) {
                data.putAll(dataPayload);
            }

            AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH);

            if (collapseKey != null && !collapseKey.isBlank()) {
                androidConfigBuilder.setCollapseKey(collapseKey);
            }

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .setAndroidConfig(androidConfigBuilder.build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] Successfully sent {} push to courier {} (token: {}...): messageId={}",
                    type, courierId, fcmToken.substring(0, Math.min(10, fcmToken.length())), response);
            return true;
        } catch (Exception e) {
            log.error("[FCM] Error sending {} push to courier {}: {}", type, courierId, e.getMessage());
            return false;
        }
    }

    /**
     * NEW_OFFER data push
     */
    public boolean sendNewOfferPush(Long courierId, Long orderId, Long offerId, String expiresAt, long ttlSeconds, Integer distanceMeters) {
        Map<String, String> data = new LinkedHashMap<>();
        if (orderId != null) data.put("orderId", String.valueOf(orderId));
        if (offerId != null) data.put("offerId", String.valueOf(offerId));
        if (expiresAt != null) data.put("expiresAt", expiresAt);
        data.put("ttlSeconds", String.valueOf(ttlSeconds));
        if (distanceMeters != null) data.put("distanceMeters", String.valueOf(distanceMeters));

        String collapseKey = offerId != null ? "offer_" + offerId : null;
        return sendCourierDataPush(courierId, "NEW_OFFER", data, collapseKey);
    }

    /**
     * OFFER_CANCELLED data push
     */
    public boolean sendOfferCancelledPush(Long courierId, Long orderId, Long offerId) {
        Map<String, String> data = new LinkedHashMap<>();
        if (orderId != null) data.put("orderId", String.valueOf(orderId));
        if (offerId != null) data.put("offerId", String.valueOf(offerId));

        String collapseKey = offerId != null ? "offer_" + offerId : null;
        return sendCourierDataPush(courierId, "OFFER_CANCELLED", data, collapseKey);
    }

    /**
     * ORDER_CANCELLED data push
     */
    public boolean sendOrderCancelledPush(Long courierId, Long orderId) {
        Map<String, String> data = new LinkedHashMap<>();
        if (orderId != null) data.put("orderId", String.valueOf(orderId));
        return sendCourierDataPush(courierId, "ORDER_CANCELLED", data, "order_" + orderId);
    }

    /**
     * ORDER_REASSIGNED data push
     */
    public boolean sendOrderReassignedPush(Long courierId, Long orderId) {
        Map<String, String> data = new LinkedHashMap<>();
        if (orderId != null) data.put("orderId", String.valueOf(orderId));
        return sendCourierDataPush(courierId, "ORDER_REASSIGNED", data, "order_" + orderId);
    }

    public String getCourierFcmToken(Long courierId) {
        try {
            return redisTemplate.opsForValue().get("courier:fcm:" + courierId);
        } catch (Exception e) {
            log.warn("[FCM] Failed to read courier FCM token from Redis: {}", e.getMessage());
            return null;
        }
    }
}

