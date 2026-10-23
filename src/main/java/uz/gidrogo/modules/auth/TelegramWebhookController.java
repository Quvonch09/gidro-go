package uz.gidrogo.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@Tag(name = "Telegram Bot API", description = "Telegram bot webhook va integratsiya")
public class TelegramWebhookController {

    private final TelegramBotService telegramBotService;

    @org.springframework.beans.factory.annotation.Value("${telegram.bot.secret-token:}")
    private String configuredSecretToken;

    @PostMapping("/webhook")
    @Operation(summary = "Telegram Bot Webhook qabul qiluvchi endpoint (X-11)")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody JsonNode update) {
        if (configuredSecretToken != null && !configuredSecretToken.isBlank()) {
            if (!configuredSecretToken.equals(secretToken)) {
                log.warn("[TELEGRAM WEBHOOK] Invalid secret token received");
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }
        }
        try {
            telegramBotService.processUpdate(update);
        } catch (Exception e) {
            log.error("[TELEGRAM WEBHOOK] Error processing webhook update: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
