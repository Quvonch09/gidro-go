package uz.gidrogo.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integratsiya testlari asosi: haqiqiy PostgreSQL (gidrogo_it, Flyway V1..V14), Redis, HTTP orqali so'rovlar.
 * Har bir test noyob ma'lumot (telefon, nom) bilan ishlaydi — bazani tozalash shart emas
 * (Audit Log va ledgerlar o'chirilmaydi).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class ItBase {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 10_000_000L);
    protected static final String PASSWORD = "Test1234pass";

    @Autowired protected TestRestTemplate rest;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ObjectMapper objectMapper;

    private String superAdminToken;

    protected String uniquePhone() {
        long n = SEQ.incrementAndGet() % 10_000_000L;
        return "+99899" + String.format("%07d", n);
    }

    protected String uniqueName(String prefix) {
        return "TEST " + prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    // ---------- HTTP ----------

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        return call(method, path, token, body, randomIp());
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body, String ip) {
        if (!(rest.getRestTemplate().getRequestFactory() instanceof HttpComponentsClientHttpRequestFactory)) {
            rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory()); // PATCH uchun
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Forwarded-For", ip);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    protected ResponseEntity<JsonNode> login(String phone, String password) {
        return call(HttpMethod.POST, "/api/auth/login", null, Map.of("phone", phone, "password", password));
    }

    protected String loginToken(String phone, String password) {
        ResponseEntity<JsonNode> r = login(phone, password);
        assertThat(r.getStatusCode().value()).as("login %s: %s", phone, r.getBody()).isEqualTo(200);
        return r.getBody().path("data").path("accessToken").asText();
    }

    // ---------- Ma'lumotlar ----------

    protected long createUser(String role, String phone, Long farmId, String activationStatus) {
        return jdbc.queryForObject("""
                INSERT INTO users (farm_id, role, full_name, phone, password_hash, status, activation_status, created_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, now()) RETURNING id
                """, Long.class, farmId, role, "TEST " + role + " " + phone, phone, passwordEncoder.encode(PASSWORD), activationStatus);
    }

    protected String superAdminToken() {
        if (superAdminToken == null) {
            String phone = uniquePhone();
            createUser("SUPER_ADMIN", phone, null, null);
            superAdminToken = loginToken(phone, PASSWORD);
        }
        return superAdminToken;
    }

    /** SuperAdmin API orqali ferma + boss yaratadi. Qaytaradi: {farmId, bossId, bossPhone}. */
    protected Map<String, Object> createFarmViaApi() {
        String bossPhone = uniquePhone();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", uniqueName("Farm"));
        body.put("phone", uniquePhone());
        body.put("address", "Toshkent, Chilonzor");
        body.put("latitude", 41.30);
        body.put("longitude", 69.24);
        body.put("city", "Toshkent");
        body.put("bossFullName", "TEST Boss");
        body.put("bossPhone", bossPhone);
        body.put("bossPassword", PASSWORD);
        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/superadmin/farms", superAdminToken(), body);
        assertThat(r.getStatusCode().value()).as("create farm: %s", r.getBody()).isEqualTo(200);
        JsonNode data = r.getBody().path("data");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("farmId", data.path("id").asLong());
        out.put("bossId", data.path("bossUserId").asLong());
        out.put("bossPhone", bossPhone);
        return out;
    }

    /** Mijoz (users + clients + manzil). Qaytaradi: {userId, clientId, addressId, phone}. */
    protected Map<String, Object> createClient(long farmId) {
        String phone = uniquePhone();
        long userId = createUser("CLIENT", phone, farmId, null);
        long clientId = jdbc.queryForObject(
                "INSERT INTO clients (user_id, farm_id, created_at) VALUES (?, ?, now()) RETURNING id", Long.class, userId, farmId);
        long addressId = jdbc.queryForObject("""
                INSERT INTO client_addresses (client_id, label, address, latitude, longitude, is_default)
                VALUES (?, 'Uy', 'Toshkent', 41.31, 69.25, true) RETURNING id
                """, Long.class, clientId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("clientId", clientId);
        out.put("addressId", addressId);
        out.put("phone", phone);
        return out;
    }

    protected long createProduct(long farmId, BigDecimal price) {
        return jdbc.queryForObject("""
                INSERT INTO products (farm_id, name, price, is_active, is_returnable, created_at)
                VALUES (?, ?, ?, true, false, now()) RETURNING id
                """, Long.class, farmId, uniqueName("Suv"), price);
    }

    /** Buyurtma (to'g'ridan-to'g'ri SQL). completedAt null bo'lishi mumkin. */
    protected long createOrder(long farmId, long clientId, String status, BigDecimal productsSum, BigDecimal taraSum,
                               Instant createdAt, Instant completedAt) {
        BigDecimal tara = taraSum != null ? taraSum : BigDecimal.ZERO;
        return jdbc.queryForObject("""
                INSERT INTO orders (order_number, farm_id, client_id, status, payment_method, payment_status, total_sum,
                                    products_sum, tara_sum, delivery_address, latitude, longitude, created_at, completed_at)
                VALUES (?, ?, ?, ?, 'CASH', 'PENDING', ?, ?, ?, 'Toshkent', 41.3, 69.2, ?, ?) RETURNING id
                """, Long.class,
                "IT-" + UUID.randomUUID().toString().substring(0, 12), farmId, clientId, status,
                productsSum.add(tara), productsSum, tara, Timestamp.from(createdAt),
                completedAt != null ? Timestamp.from(completedAt) : null);
    }
}
