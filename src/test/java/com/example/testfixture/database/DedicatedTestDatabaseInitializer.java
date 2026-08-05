package com.example.testfixture.database;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class DedicatedTestDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern MYSQL_DATABASE_NAME =
            Pattern.compile("^jdbc:mysql://[^/]+/([^?;]+)(?:[?;].*)?$");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String jdbcUrl =
                applicationContext
                        .getEnvironment()
                        .getRequiredProperty("spring.datasource.url");
        Matcher matcher = MYSQL_DATABASE_NAME.matcher(jdbcUrl);

        if (!matcher.matches()) {
            throw new IllegalStateException("테스트 datasource는 MySQL JDBC URL이어야 합니다.");
        }

        String databaseName = matcher.group(1);
        if (!databaseName.endsWith("_test")) {
            throw new IllegalStateException(
                    "테스트 datasource는 _test로 끝나는 전용 데이터베이스여야 합니다: " + databaseName);
        }
    }
}
