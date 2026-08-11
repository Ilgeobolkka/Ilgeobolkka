package com.example.ilgeobolkka.performance;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
final class PerformanceDatabaseGuard {

    static final String PERFORMANCE_DATABASE_NAME = "ilgeobolkka_perf";

    void requirePerformanceDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            requirePerformanceDatabaseName(connection.getCatalog());
        } catch (SQLException exception) {
            throw new IllegalStateException("성능 데이터베이스 이름을 확인하지 못했습니다.", exception);
        }
    }

    static void requirePerformanceDatabaseName(String databaseName) {
        if (!PERFORMANCE_DATABASE_NAME.equals(databaseName)) {
            throw new IllegalStateException(
                    "성능 데이터 초기화는 ilgeobolkka_perf에서만 허용됩니다: " + databaseName);
        }
    }
}
