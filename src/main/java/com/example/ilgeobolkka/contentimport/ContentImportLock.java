package com.example.ilgeobolkka.contentimport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod & (content-import | test)")
class ContentImportLock {

    private static final String LOCK_NAME_SQL =
            "CONCAT('ilgeobolkka:content-import:', LEFT(SHA2(DATABASE(), 256), 32))";
    private static final String ACQUIRE_SQL = "SELECT GET_LOCK(" + LOCK_NAME_SQL + ", 0)";
    private static final String RELEASE_SQL = "SELECT RELEASE_LOCK(" + LOCK_NAME_SQL + ")";

    private final JdbcTemplate jdbcTemplate;

    ContentImportLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 같은 MySQL database의 콘텐츠 적재 전체를 프로세스와 호스트 사이에서 단일 실행으로 제한한다. */
    <T> T executeLocked(Supplier<T> action) {
        return jdbcTemplate.execute(
                (ConnectionCallback<T>) connection -> executeLocked(connection, action));
    }

    private <T> T executeLocked(Connection connection, Supplier<T> action) throws SQLException {
        if (queryLockResult(connection, ACQUIRE_SQL) != 1) {
            throw new IllegalStateException("같은 DB에서 다른 콘텐츠 적재가 실행 중입니다.");
        }

        Throwable actionFailure = null;
        try {
            return action.get();
        } catch (RuntimeException | Error failure) {
            actionFailure = failure;
            throw failure;
        } finally {
            try {
                if (queryLockResult(connection, RELEASE_SQL) != 1) {
                    throw new SQLException("콘텐츠 적재 advisory lock을 해제할 수 없습니다.");
                }
            } catch (SQLException releaseFailure) {
                if (actionFailure != null) {
                    actionFailure.addSuppressed(releaseFailure);
                } else {
                    throw releaseFailure;
                }
            }
        }
    }

    private int queryLockResult(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return -1;
            }
            int result = resultSet.getInt(1);
            return resultSet.wasNull() ? -1 : result;
        }
    }
}
