package uz.gidrogo.it;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Xavfsiz migratsiya (1-bo'lim): V10 boss–ferma nomuvofiqligida TO'XTAYDI va ro'yxat chiqaradi,
 * standart qiymat qo'ymaydi. Alohida vaqtinchalik baza ishlatiladi (Spring konteksti yo'q).
 */
class SafeMigrationIT {

    private static final String BASE = System.getProperty("it.pg.base", "jdbc:postgresql://localhost:5432/");
    private static final String DB = "gidrogo_mig_it";
    private static final String USER = System.getProperty("it.pg.user", "postgres");
    private static final String PASS = System.getProperty("it.pg.password", "postgres");

    private static DataSource ds(String db) {
        return new DriverManagerDataSource(BASE + db, USER, PASS);
    }

    @BeforeAll
    static void createDb() {
        JdbcTemplate admin = new JdbcTemplate(ds("postgres"));
        admin.execute("DROP DATABASE IF EXISTS " + DB);
        admin.execute("CREATE DATABASE " + DB);
    }

    @AfterAll
    static void dropDb() {
        new JdbcTemplate(ds("postgres")).execute("DROP DATABASE IF EXISTS " + DB);
    }

    @Test
    @DisplayName("V10: users.farm_id va farms.boss_user_id mos kelmasa — migratsiya to'xtaydi, juftlar ro'yxati chiqadi")
    void v10StopsOnMismatch() {
        Flyway toV9 = Flyway.configure().dataSource(ds(DB)).locations("classpath:db/migration").target("9").load();
        toV9.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(ds(DB));
        // BUG-016 holati: boss #? farmId = ferma B, lekin ferma B ning boss_user_id = NULL
        Long farmA = jdbc.queryForObject("INSERT INTO farms (name, phone, status) VALUES ('A', '1', 'ACTIVE') RETURNING id", Long.class);
        Long farmB = jdbc.queryForObject("INSERT INTO farms (name, phone, status) VALUES ('B', '2', 'ACTIVE') RETURNING id", Long.class);
        Long boss = jdbc.queryForObject("INSERT INTO users (farm_id, role, full_name, phone, password_hash, status) "
                + "VALUES (?, 'BOSS', 'Boss', '+998900000001', 'x', 'ACTIVE') RETURNING id", Long.class, farmB);
        jdbc.update("UPDATE farms SET boss_user_id = ? WHERE id = ?", boss, farmA);

        Flyway all = Flyway.configure().dataSource(ds(DB)).locations("classpath:db/migration").load();
        assertThatThrownBy(all::migrate)
                .hasMessageContaining("V10 to'xtatildi")
                .hasMessageContaining("boss #" + boss);

        // Hech narsa avtomatik o'zgartirilmagan
        assertThat(jdbc.queryForObject("SELECT boss_user_id FROM farms WHERE id = ?", Long.class, farmA)).isEqualTo(boss);
        assertThat(jdbc.queryForObject("SELECT boss_user_id FROM farms WHERE id = ?", Long.class, farmB)).isNull();
        assertThat(jdbc.queryForObject("SELECT farm_id FROM users WHERE id = ?", Long.class, boss)).isEqualTo(farmB);

        // Egasi qo'lda tuzatgach migratsiya davom etadi
        jdbc.update("UPDATE users SET farm_id = ? WHERE id = ?", farmA, boss);
        all.repair();
        all.migrate();
        assertThat(jdbc.queryForObject("SELECT activation_status FROM users WHERE id = ?", String.class, boss)).isEqualTo("APPROVED");
    }
}
