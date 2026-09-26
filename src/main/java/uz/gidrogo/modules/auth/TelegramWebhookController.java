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

    @PostMapping("/webhook")
    @Operation(summary = "Telegram Bot Webhook qabul qiluvchi endpoint")
    public ResponseEntity<Void> handleWebhook(@RequestBody JsonNode update) {
        try {
            telegramBotService.processUpdate(update);
        } catch (Exception e) {
            log.error("[TELEGRAM WEBHOOK] Error processing webhook update: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
