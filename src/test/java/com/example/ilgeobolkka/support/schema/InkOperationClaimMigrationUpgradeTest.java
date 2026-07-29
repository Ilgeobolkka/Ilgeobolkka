package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class InkOperationClaimMigrationUpgradeTest {

    private static final Pattern MYSQL_JDBC_URL =
            Pattern.compile("^(jdbc:mysql://[^/]+/)([^?;]+)(.*)$");

    private final Environment environment;

    @Autowired
    InkOperationClaimMigrationUpgradeTest(Environment environment) {
        this.environment = environment;
    }

    @Test
    void T_INK_006_V1_원장은_V2_적용_후_claim으로_이관된다() throws SQLException {
        String sourceUrl =
                environment.getRequiredProperty("spring.datasource.url");
        String rootPassword =
                environment.getRequiredProperty("DB_ROOT_PASSWORD");
        String databaseName =
                "ilgeobolkka_migration_"
                        + UUID.randomUUID().toString().replace("-", "")
                        + "_test";
        String adminUrl = replaceDatabase(sourceUrl, "mysql");
        String migrationUrl = replaceDatabase(sourceUrl, databaseName);

        createDatabase(adminUrl, rootPassword, databaseName);
        try {
            migrateToV1(migrationUrl, rootPassword);
            JdbcTemplate jdbcTemplate = jdbcTemplate(migrationUrl, rootPassword);
            createV1LedgerEntries(jdbcTemplate);

            migrateToLatest(migrationUrl, rootPassword);

            assertAll(
                    () ->
                            assertEquals(
                                    2,
                                    jdbcTemplate.queryForObject(
                                            "SELECT COUNT(*) FROM ink_operation_claim",
                                            Integer.class)),
                    () ->
                            assertEquals(
                                    1,
                                    jdbcTemplate.queryForObject(
                                            """
                                            SELECT COUNT(*)
                                            FROM ink_operation_claim
                                            WHERE reader_id = 1
                                              AND ink_purchase_id = 1
                                              AND page_rental_id IS NULL
                                            """,
                                            Integer.class)),
                    () ->
                            assertEquals(
                                    1,
                                    jdbcTemplate.queryForObject(
                                            """
                                            SELECT COUNT(*)
                                            FROM ink_operation_claim
                                            WHERE reader_id = 1
                                              AND ink_purchase_id IS NULL
                                              AND page_rental_id = 1
                                            """,
                                            Integer.class)),
                    () ->
                            assertEquals(
                                    2,
                                    jdbcTemplate.queryForObject(
                                            """
                                            SELECT COUNT(DISTINCT claim_token)
                                            FROM ink_operation_claim
                                            WHERE CHAR_LENGTH(claim_token) = 36
                                            """,
                                            Integer.class)),
                    () ->
                            assertEquals(
                                    List.of("1", "2"),
                                    jdbcTemplate.queryForList(
                                            """
                                            SELECT version
                                            FROM flyway_schema_history
                                            WHERE success = 1
                                            ORDER BY installed_rank
                                            """,
                                            String.class)));
        } finally {
            dropDatabase(adminUrl, rootPassword, databaseName);
        }
    }

    private String replaceDatabase(String jdbcUrl, String databaseName) {
        Matcher matcher = MYSQL_JDBC_URL.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalStateException("테스트 datasource는 MySQL JDBC URL이어야 합니다.");
        }
        return matcher.group(1) + databaseName + matcher.group(3);
    }

    private void createDatabase(
            String adminUrl,
            String rootPassword,
            String databaseName) throws SQLException {
        executeDatabaseStatement(
                adminUrl,
                rootPassword,
                """
                CREATE DATABASE `%s`
                    CHARACTER SET utf8mb4
                    COLLATE utf8mb4_0900_ai_ci
                """.formatted(databaseName));
    }

    private void dropDatabase(
            String adminUrl,
            String rootPassword,
            String databaseName) throws SQLException {
        executeDatabaseStatement(
                adminUrl,
                rootPassword,
                "DROP DATABASE `%s`".formatted(databaseName));
    }

    private void executeDatabaseStatement(
            String adminUrl,
            String rootPassword,
            String sql) throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection(adminUrl, "root", rootPassword);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void migrateToV1(String migrationUrl, String rootPassword) {
        Flyway.configure()
                .dataSource(migrationUrl, "root", rootPassword)
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();
    }

    private void migrateToLatest(String migrationUrl, String rootPassword) {
        Flyway.configure()
                .dataSource(migrationUrl, "root", rootPassword)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private JdbcTemplate jdbcTemplate(String migrationUrl, String rootPassword) {
        return new JdbcTemplate(
                new DriverManagerDataSource(migrationUrl, "root", rootPassword));
    }

    private void createV1LedgerEntries(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (1, 'migration-reader@example.com', 'encoded-password',
                        '2026-07-29 00:00:00.000000')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (1, '기술', '마이그레이션 도서', '테스트 저자', 1, 10000)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content)
                VALUES (1, 1, 1, 'TEXT', '테스트 페이지')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (1, 1, '00000000-0000-0000-0000-000000000001',
                        'PAID', 1000, 100,
                        '2026-07-29 00:00:00.000000',
                        '2026-07-29 00:01:00.000000')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (1, 1, 1,
                        '2026-07-29 00:02:00.000000',
                        '2026-08-28 00:02:00.000000')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, occurred_at)
                VALUES (1, 1, 'GRANT', 100, 100, 1,
                        '2026-07-29 00:01:00.000000')
                """);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     page_rental_id, occurred_at)
                VALUES (2, 1, 'DEDUCTION', 1, 99, 1,
                        '2026-07-29 00:02:00.000000')
                """);
    }
}
