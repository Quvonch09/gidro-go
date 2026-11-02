package uz.gidrogo.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Band 18 va 19: /ws/client handshake, bir martalik ticket, obuna egaligi va heart-beat. */
class ClientWebSocketIT extends ItBase {

    @LocalServerPort private int port;

    private static class Frames implements WebSocket.Listener {
        final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                frames.add(buffer.toString());
                buffer.setLength(0);
            }
            ws.request(1);
            return null;
        }

        String nextNonHeartbeat() throws InterruptedException {
            while (true) {
                String frame = frames.poll(5, TimeUnit.SECONDS);
                assertThat(frame).as("STOMP kadri kelmadi").isNotNull();
                if (!frame.isBlank()) return frame;
            }
        }
    }

    private WebSocket open(String query, Frames listener) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/client" + query), listener)
                .join();
    }

    private void assertRejected(String query) {
        assertThatThrownBy(() -> open(query, new Frames()))
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class)
                .satisfies(e -> assertThat(((WebSocketHandshakeException) e.getCause()).getResponse().statusCode()).isEqualTo(401));
    }

    private String ticket(String clientToken) {
        return call(HttpMethod.POST, "/api/client/ws-ticket", clientToken, null).getBody().path("data").path("ticket").asText();
    }

    private void connect(WebSocket ws, Frames frames) throws InterruptedException {
        ws.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:10000,10000\n\n\0", true).join();
        String connected = frames.nextNonHeartbeat();
        assertThat(connected).startsWith("CONNECTED").contains("heart-beat:10000,10000");
    }

    @Test
    @DisplayName("Ticket'siz, soxta ticket va mijoz JWT bilan handshake rad etiladi")
    void handshakeRequiresTicket() {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);

        assertRejected("");
        assertRejected("?ticket=bad");
        assertRejected("?token=" + loginToken((String) client.get("phone"), PASSWORD));
    }

    @Test
    @DisplayName("Ticket bir martalik: ikkinchi ulanish rad etiladi")
    void ticketIsSingleUse() throws Exception {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> client = createClient(farmId);
        String ticket = ticket(loginToken((String) client.get("phone"), PASSWORD));

        Frames frames = new Frames();
        WebSocket ws = open("?ticket=" + ticket, frames);
        connect(ws, frames);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "");

        assertRejected("?ticket=" + ticket);
    }

    @Test
    @DisplayName("Mijoz o'z buyurtmasiga obuna bo'ladi, begona buyurtmaga obuna ERROR qaytaradi")
    void subscriptionOwnershipIsEnforced() throws Exception {
        long farmId = (Long) createFarmViaApi().get("farmId");
        Map<String, Object> a = createClient(farmId);
        Map<String, Object> b = createClient(farmId);
        long ownOrder = createOrder(farmId, (Long) a.get("clientId"), "SEARCHING", new BigDecimal("10000"), null, Instant.now(), null);
        long foreignOrder = createOrder(farmId, (Long) b.get("clientId"), "SEARCHING", new BigDecimal("10000"), null, Instant.now(), null);

        String clientToken = loginToken((String) a.get("phone"), PASSWORD);

        // O'z buyurtmasi: ERROR kelmaydi, DISCONNECT tasdig'i (RECEIPT) keladi
        Frames frames = new Frames();
        WebSocket ws = open("?ticket=" + ticket(clientToken), frames);
        connect(ws, frames);
        ws.sendText("SUBSCRIBE\nid:own\ndestination:/topic/orders/" + ownOrder + "/tracking\n\n\0", true).join();
        ws.sendText("DISCONNECT\nreceipt:done\n\n\0", true).join();
        assertThat(frames.nextNonHeartbeat()).startsWith("RECEIPT");

        // Begona buyurtma: ERROR
        Frames foreignFrames = new Frames();
        WebSocket foreignWs = open("?ticket=" + ticket(clientToken), foreignFrames);
        connect(foreignWs, foreignFrames);
        foreignWs.sendText("SUBSCRIBE\nid:foreign\ndestination:/topic/orders/" + foreignOrder + "/tracking\n\n\0", true).join();
        assertThat(foreignFrames.nextNonHeartbeat()).startsWith("ERROR");
    }

    @Test
    @DisplayName("Boss paneli JWT bilan ulanadi, bloklangan foydalanuvchi ulanmaydi")
    void dashboardJwtRespectsAccountStatus() throws Exception {
        Map<String, Object> farm = createFarmViaApi();
        String managerPhone = uniquePhone();
        long managerId = createUser("MANAGER", managerPhone, (Long) farm.get("farmId"), null);
        String token = loginToken(managerPhone, PASSWORD);

        Frames frames = new Frames();
        WebSocket ws = open("?token=" + token, frames);
        connect(ws, frames);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "");

        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE id = ?", managerId);
        assertRejected("?token=" + token);
    }
}
