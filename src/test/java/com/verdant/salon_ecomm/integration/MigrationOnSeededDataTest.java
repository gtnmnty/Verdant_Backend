package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The data-changing migrations, run against rows written the way production holds them before the migration.
// Each test gets its own fresh database in the shared container and walks Flyway up step by step.
class MigrationOnSeededDataTest extends AbstractIntegrationTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate db;

    @BeforeEach
    void freshDatabase() {
        String name = "mig_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
            .execute("CREATE DATABASE " + name);
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        dataSource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        db = new JdbcTemplate(dataSource);
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
            .target(version).load().migrate();
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        db.update("insert into users (id, full_name, email, password_hash) values (?, 'Seed', ?, 'x')", id, email);
        return id;
    }

    // ── V12: case-insensitive unique email ───────────────

    @Test
    void v12NormalizesExistingEmailsAndPendingEmails() {
        migrateTo("11");
        UUID jane = insertUser("  Jane.Doe@Example.COM ");
        UUID bob = insertUser("bob@x.com");
        db.update("update users set pending_email = ' New.Address@X.com' where id = ?", bob);

        migrateTo("12");

        assertThat(db.queryForObject("select email from users where id = ?", String.class, jane))
            .isEqualTo("jane.doe@example.com");
        assertThat(db.queryForObject("select pending_email from users where id = ?", String.class, bob))
            .isEqualTo("new.address@x.com");
        assertThatThrownBy(() -> insertUser("JANE.DOE@example.com"))
            .hasMessageContaining("uq_users_email_lower");
    }

    @Test
    void v12StopsAndNamesTheCollisionInsteadOfMergingAccounts() {
        migrateTo("11");
        insertUser("Twin@X.com");
        insertUser("twin@x.com ");

        assertThatThrownBy(() -> migrateTo("12"))
            .isInstanceOf(FlywayException.class)
            .hasMessageContaining("M-36")
            .hasMessageContaining("twin@x.com");

        // Nothing was half-applied: both rows are untouched and Flyway is still at 11.
        assertThat(db.queryForList("select email from users order by email", String.class))
            .containsExactly("Twin@X.com", "twin@x.com ");
        assertThat(db.queryForObject(
            "select max(version::int) from flyway_schema_history where success", Integer.class)).isEqualTo(11);
    }

    // ── V13: hashed refresh tokens ───────────────────────

    // Same as RefreshTokenService.hash: hex SHA-256 of the UTF-8 bytes.
    private static String javaHash(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void v13HashesExistingTokensExactlyAsTheAppDoesSoNobodyIsSignedOut() throws Exception {
        migrateTo("12");
        UUID user = insertUser("tokens@x.com");
        String[] raws = { "plain-token", "eyJhbGciOiJIUzI1NiJ9.e30.sig", "ünïcødé-tökén" };
        for (String raw : raws) {
            db.update("insert into refresh_tokens (user_id, token, jti, expires_at) values (?, ?, ?, now() + interval '7 days')",
                user, raw, UUID.randomUUID().toString());
        }

        migrateTo("13");

        for (String raw : raws) {
            assertThat(db.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                javaHash(raw))).as("session for %s still recognized", raw).isEqualTo(1);
        }
        List<String> columns = db.queryForList(
            "select column_name from information_schema.columns where table_name = 'refresh_tokens'", String.class);
        assertThat(columns).contains("token_hash", "revoked_at").doesNotContain("token", "jti");
        assertThatThrownBy(() -> db.update(
            "insert into refresh_tokens (user_id, token_hash, expires_at) values (?, ?, now())", user, javaHash("plain-token")))
            .hasMessageContaining("uq_refresh_tokens_token_hash");
    }

    @Test
    void allMigrationsApplyOnSeededV1Data() {
        migrateTo("1");
        UUID user = insertUser("Seeded@Example.com");
        db.update("insert into refresh_tokens (user_id, token, jti, expires_at) values (?, 'raw', 'jti-1', now())", user);

        migrateTo("latest");

        assertThat(db.queryForObject("select email from users where id = ?", String.class, user))
            .isEqualTo("seeded@example.com");
        assertThat(db.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class))
            .isZero();
    }
}
