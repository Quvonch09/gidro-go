package uz.gidrogo.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Band 23: kontakt tasdiqlangach klaviatura olinadi, inline va menyu tugmasi; tasdiqlangan mijozdan kontakt so'ralmaydi. */
class TelegramBotServiceTest {

    private static final String URL = "https://miniapp.example";

    private final TelegramUserRepository telegramUsers = mock(TelegramUserRepository.class);
    private final TelegramAccountRepository accounts = mock(TelegramAccountRepository.class);
    private final TelegramPhoneVerificationRepository verifications = mock(TelegramPhoneVerificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RestTemplate rest = mock(RestTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private TelegramBotService bot;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bot = new TelegramBotService(telegramUsers, accounts, verifications, users, redis);
        ReflectionTestUtils.setField(bot, "botToken", "test-token");
        ReflectionTestUtils.setField(bot, "miniAppUrl", URL);
        ReflectionTestUtils.setField(bot, "restTemplate", rest);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(verifications.findTopByTelegramIdAndPhoneAndExpiresAtAfter(anyLong(), anyString(), any())).thenReturn(Optional.empty());
        when(accounts.findByTelegramId(anyLong())).thenReturn(Optional.empty());
        when(users.findByPhone(anyString())).thenReturn(Optional.empty());
        when(rest.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok("{\"ok\":true}"));
    }

    private JsonNode update(String messageJson) throws Exception {
        return json.readTree("{\"update_id\":1,\"message\":" + messageJson + "}");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sent(String method) {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> body = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest, atLeastOnce()).postForEntity(url.capture(), body.capture(), eq(String.class));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = 0; i < url.getAllValues().size(); i++) {
            if (url.getAllValues().get(i).endsWith("/" + method)) {
                out.add((Map<String, Object>) body.getAllValues().get(i).getBody());
            }
        }
        return out;
    }

    @Test
    void contactRemovesKeyboardThenOffersMiniApp() throws Exception {
        when(telegramUsers.findByTelegramId(77L)).thenReturn(Optional.empty());

        bot.processUpdate(update("{\"chat\":{\"id\":500},\"from\":{\"id\":77,\"first_name\":\"Ali\",\"language_code\":\"uz\"},"
                + "\"contact\":{\"user_id\":77,\"phone_number\":\"998901234567\"}}"));

        List<Map<String, Object>> messages = sent("sendMessage");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("reply_markup")).isEqualTo(Map.of("remove_keyboard", true));
        assertThat(messages.get(1).get("reply_markup").toString()).contains("inline_keyboard", "GidroGo'ni ochish", URL);

        List<Map<String, Object>> menu = sent("setChatMenuButton");
        assertThat(menu).hasSize(1);
        assertThat(menu.get(0).get("chat_id")).isEqualTo(500L);
        assertThat(menu.get(0).get("menu_button").toString()).contains("web_app", "GidroGo", URL);
    }

    @Test
    void verifiedClientStartDoesNotAskContactAndSpeaksRussian() throws Exception {
        when(telegramUsers.findByTelegramId(78L)).thenReturn(Optional.of(
                TelegramUser.builder().telegramId(78L).phone("+998901234567").build()));

        bot.processUpdate(update("{\"chat\":{\"id\":501},\"from\":{\"id\":78,\"first_name\":\"Иван\",\"language_code\":\"ru\"},\"text\":\"/start\"}"));

        List<Map<String, Object>> messages = sent("sendMessage");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("text").toString()).contains("Здравствуйте");
        assertThat(messages.get(0).get("reply_markup").toString()).contains("Открыть GidroGo").doesNotContain("request_contact");
    }

    @Test
    void newUserStartStillAsksForContact() throws Exception {
        when(telegramUsers.findByTelegramId(79L)).thenReturn(Optional.empty());

        bot.processUpdate(update("{\"chat\":{\"id\":502},\"from\":{\"id\":79,\"first_name\":\"Vali\"},\"text\":\"/start\"}"));

        assertThat(sent("sendMessage").get(0).get("reply_markup").toString()).contains("request_contact");
    }
}
