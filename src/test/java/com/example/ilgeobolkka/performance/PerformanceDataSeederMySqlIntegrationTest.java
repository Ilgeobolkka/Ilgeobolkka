package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class PerformanceDataSeederMySqlIntegrationTest {

    private static final String PERFORMANCE_PASSWORD = "Performance123!";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    PerformanceDataSeederMySqlIntegrationTest(
            JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void MySQL에_MVP_성능_데이터를_초기화하고_불변식을_검증한다() throws Exception {
        PerformanceDataSeeder seeder = new PerformanceDataSeeder(
                performanceDataSource(),
                jdbcTemplate,
                passwordEncoder,
                new PerformanceDatabaseGuard(),
                new PerformanceDatasetPlan());

        PerformanceDataSeeder.SeedSummary summary = seeder.resetMvp(PERFORMANCE_PASSWORD);

        assertEquals(
                Map.ofEntries(
                        Map.entry("book", 100),
                        Map.entry("book_page", 400),
                        Map.entry("reader", 1_000),
                        Map.entry("ink_account", 1_000),
                        Map.entry("ink_purchase", 1_000),
                        Map.entry("ink_ledger", 1_333),
                        Map.entry("page_rental", 333),
                        Map.entry("ownership_payment", 333),
                        Map.entry("book_ownership", 333),
                        Map.entry("library_entry", 666),
                        Map.entry("reading_session", 0)),
                summary.rowCounts());
        assertEquals(0, summary.balanceMismatchCount());
    }

    private DataSource performanceDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn(
                PerformanceDatabaseGuard.PERFORMANCE_DATABASE_NAME);
        return dataSource;
    }
}
