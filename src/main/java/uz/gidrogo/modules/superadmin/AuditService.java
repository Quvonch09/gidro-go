package uz.gidrogo.modules.superadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.ClientIp;
import uz.gidrogo.common.SecurityUtils;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SuperAdmin amallarini Audit Log'ga yozadi.
 * <p>
 * Propagation.MANDATORY: audit faqat amalning o'z tranzaksiyasi ichida yoziladi —
 * audit yozilmasa, amal ham bekor bo'ladi (TZ §13, §18). Xatolar yutib yuborilmaydi.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Set<String> SECRET_KEY_PARTS = Set.of("password", "parol", "token", "secret", "hash");

    private final SuperAdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public SuperAdminAuditLog record(AuditAction action, String entityType, Long entityId,
                                     Object oldValue, Object newValue) {
        return record(action, entityType, entityId, oldValue, newValue, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SuperAdminAuditLog record(AuditAction action, String entityType, Long entityId,
                                     Object oldValue, Object newValue, Map<String, ?> extra) {
        Long actorId = SecurityUtils.getCurrentUserId();
        if (actorId == null) {
            // Audit yozib bo'lmaydi → amal ham bajarilmaydi
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUDIT_ACTOR_UNKNOWN",
                    "Audit Log yozib bo'lmadi: amal bajaruvchi aniqlanmadi");
        }

        ObjectNode meta = objectMapper.createObjectNode();
        meta.set("old", sanitize(objectMapper.valueToTree(oldValue)));
        meta.set("new", sanitize(objectMapper.valueToTree(newValue)));
        if (extra != null) {
            extra.forEach((k, v) -> {
                if (!isSecret(k)) meta.set(k, sanitize(objectMapper.valueToTree(v)));
            });
        }

        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_WRITE_FAILED", "Audit Log yozib bo'lmadi");
        }

        return auditLogRepository.saveAndFlush(SuperAdminAuditLog.builder()
                .actorId(actorId)
                .action(action.name())
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata)
                .ipAddress(ClientIp.ip())
                .userAgent(ClientIp.userAgent())
                .build());
    }

    private JsonNode sanitize(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (isSecret(e.getKey())) {
                    it.remove();
                } else {
                    sanitize(e.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::sanitize);
        }
        return node;
    }

    private static boolean isSecret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return SECRET_KEY_PARTS.stream().anyMatch(k::contains);
    }
}
