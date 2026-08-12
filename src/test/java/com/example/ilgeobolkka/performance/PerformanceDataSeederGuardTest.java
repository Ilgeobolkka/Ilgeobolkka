package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class PerformanceDataSeederGuardTest {

    @Test
    void 성능_DB가_아니면_조회와_초기화_전에_거부한다() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("ilgeobolkka_test");

        PerformanceDataSeeder seeder = new PerformanceDataSeeder(
                dataSource,
                jdbcTemplate,
                passwordEncoder,
                new PerformanceDatabaseGuard(),
                new PerformanceDatasetPlan());

        assertThrows(IllegalStateException.class, () -> seeder.resetMvp("performance123!"));
        verifyNoInteractions(jdbcTemplate, passwordEncoder);
    }
}
