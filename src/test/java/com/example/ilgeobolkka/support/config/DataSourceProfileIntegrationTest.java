package com.example.ilgeobolkka.support.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.IlgeobolkkaApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DataSourceProfileIntegrationTest {

    private static final Map<String, Object> SPRING_DATASOURCE_ENVIRONMENT =
            Map.of(
                    "SPRING_DATASOURCE_URL", "jdbc:mysql://127.0.0.1:1/ilgeobolkka",
                    "SPRING_DATASOURCE_USERNAME", "spring-profile-test-user",
                    "SPRING_DATASOURCE_PASSWORD", "spring-profile-test-password");
    private static final Map<String, Object> VALID_PROD_ENVIRONMENT =
            Map.of(
                    "DB_URL", "jdbc:mysql://db.example:3306/ilgeobolkka",
                    "DB_USERNAME", "profile-test-user",
                    "DB_PASSWORD", "profile-test-password");

    @Test
    void 로컬_프로파일은_dotenv의_DataSource_설정으로_MySQL에_연결한다() throws Exception {
        SpringApplication application =
                applicationFor(environmentWithoutSystemProperties(), "local");

        try (ConfigurableApplicationContext context = application.run()) {
            ConfigurableEnvironment environment = context.getEnvironment();
            DataSource dataSource = context.getBean(DataSource.class);
            PropertySource<?> dotenvPropertySource =
                    StreamSupport.stream(environment.getPropertySources().spliterator(), false)
                            .filter(this::isDotenvPropertySource)
                            .filter(propertySource -> propertySource.containsProperty("DB_URL"))
                            .findFirst()
                            .orElseThrow();

            assertAll(
                    () ->
                            assertArrayEquals(
                                    new String[] {"local"}, environment.getActiveProfiles()),
                    () ->
                            assertTrue(
                                    Objects.equals(
                                            dotenvPropertySource.getProperty("DB_URL"),
                                            environment.getProperty("spring.datasource.url")),
                                    ".env의 DB_URL이 Spring DataSource URL에 반영되어야 한다."),
                    () -> {
                        try (Connection connection = dataSource.getConnection()) {
                            assertTrue(connection.isValid(1));
                        }
                    });
        }
    }

    @ParameterizedTest(name = "{0} 누락 시 prod 시작 실패")
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"})
    void 운영_프로파일은_필수_DB_환경변수를_하나씩_검증한다(String missingVariable) {
        Map<String, Object> providedEnvironment = new HashMap<>(VALID_PROD_ENVIRONMENT);
        providedEnvironment.remove(missingVariable);

        assertProdStartFailsForInvalidVariable(
                providedEnvironment,
                missingVariable,
                requiredEnvironmentVariableMessage(missingVariable));
    }

    @ParameterizedTest(name = "{0} 공백 시 prod 시작 실패")
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"})
    void 운영_프로파일은_빈_DB_환경변수를_하나씩_검증한다(String blankVariable) {
        Map<String, Object> providedEnvironment = new HashMap<>(VALID_PROD_ENVIRONMENT);
        providedEnvironment.put(blankVariable, " ");

        assertProdStartFailsForInvalidVariable(
                providedEnvironment,
                blankVariable,
                requiredEnvironmentVariableMessage(blankVariable));
    }

    @Test
    void 운영_프로파일은_Spring_DataSource_설정으로_DB_환경변수_검증을_우회할_수_없다() {
        assertProdStartFailsForInvalidVariable(
                SPRING_DATASOURCE_ENVIRONMENT,
                "DB_URL",
                requiredEnvironmentVariableMessage("DB_URL"));
    }

    @ParameterizedTest(name = "{0} 충돌 시 prod 시작 실패")
    @CsvSource({
        "SPRING_DATASOURCE_URL, spring.datasource.url, DB_URL",
        "SPRING_DATASOURCE_USERNAME, spring.datasource.username, DB_USERNAME",
        "SPRING_DATASOURCE_PASSWORD, spring.datasource.password, DB_PASSWORD"
    })
    void 운영_프로파일은_DB_환경변수와_충돌하는_Spring_DataSource_설정을_하나씩_거부한다(
            String springDataSourceEnvironmentVariable,
            String dataSourceProperty,
            String environmentVariable) {
        Map<String, Object> providedEnvironment = new HashMap<>(VALID_PROD_ENVIRONMENT);
        providedEnvironment.put(
                springDataSourceEnvironmentVariable,
                SPRING_DATASOURCE_ENVIRONMENT.get(springDataSourceEnvironmentVariable));

        assertProdStartFails(
                providedEnvironment,
                conflictingDataSourcePropertyMessage(dataSourceProperty, environmentVariable),
                springDataSourceEnvironmentVariable
                        + "가 "
                        + environmentVariable
                        + "와 충돌하면 prod 시작에 실패해야 한다.");
    }

    @Test
    void 운영과_로컬_프로파일은_동시에_활성화할_수_없다() {
        SpringApplication application =
                applicationFor(environmentWithoutSystemProperties(), "prod", "local");

        Exception exception =
                assertThrows(
                        Exception.class,
                        () -> {
                            try (ConfigurableApplicationContext ignored = application.run()) {
                                // 시작 성공 시 컨텍스트를 닫고 테스트를 실패시킨다.
                            }
                        });

        assertTrue(
                hasCauseMessage(
                        exception,
                        "Profiles 'prod' and 'local' must not be active at the same time"),
                "prod와 local이 동시에 활성화되면 시작에 실패해야 한다.");
    }

    @Test
    void 운영_프로파일은_명령행_DB_설정을_환경변수로_인정하지_않는다() {
        StandardEnvironment prodEnvironment = environmentWithoutSystemProperties();
        prodEnvironment
                .getPropertySources()
                .addFirst(
                        new SimpleCommandLinePropertySource(
                                "--DB_URL=jdbc:mysql://127.0.0.1:1/ilgeobolkka",
                                "--DB_USERNAME=command-line-user",
                                "--DB_PASSWORD=command-line-password"));

        assertProdStartFails(
                prodEnvironment,
                requiredEnvironmentVariableMessage("DB_URL"),
                "명령행 DB_*만 제공하면 환경변수 누락으로 prod 시작에 실패해야 한다.");
    }

    @Test
    void 운영_DataSource는_다른_Spring_설정_소스에_의해_교체되지_않는다() {
        StandardEnvironment prodEnvironment = environmentWithoutSystemProperties();
        prodEnvironment
                .getPropertySources()
                .addFirst(
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                VALID_PROD_ENVIRONMENT));
        prodEnvironment
                .getPropertySources()
                .addFirst(
                        new PropertySource<>("non-enumerable-datasource") {
                            @Override
                            public Object getProperty(String name) {
                                return switch (name) {
                                    case "spring.datasource.jndi-name" ->
                                            "java:comp/env/jdbc/ilgeobolkka";
                                    case "spring.datasource.hikari.jdbc-url" ->
                                            "jdbc:mysql://127.0.0.1:1/ilgeobolkka";
                                    default -> null;
                                };
                            }
                        });
        prodEnvironment
                .getPropertySources()
                .addFirst(
                        new SimpleCommandLinePropertySource(
                                "--spring.autoconfigure.exclude="
                                        + "org.springframework.boot.hibernate.autoconfigure."
                                        + "HibernateJpaAutoConfiguration,"
                                        + "org.springframework.boot.data.jpa.autoconfigure."
                                        + "DataJpaRepositoriesAutoConfiguration"));
        SpringApplication application = applicationFor(prodEnvironment, "prod");

        try (ConfigurableApplicationContext context = application.run()) {
            HikariDataSource dataSource =
                    assertInstanceOf(HikariDataSource.class, context.getBean(DataSource.class));

            assertAll(
                    () -> assertEquals(VALID_PROD_ENVIRONMENT.get("DB_URL"), dataSource.getJdbcUrl()),
                    () ->
                            assertEquals(
                                    VALID_PROD_ENVIRONMENT.get("DB_USERNAME"),
                                    dataSource.getUsername()),
                    () ->
                            assertEquals(
                                    VALID_PROD_ENVIRONMENT.get("DB_PASSWORD"),
                                    dataSource.getPassword()));
        }
    }

    private void assertProdStartFailsForInvalidVariable(
            Map<String, Object> providedEnvironment,
            String invalidVariable,
            String expectedCauseMessage) {
        Object invalidValue = providedEnvironment.get(invalidVariable);
        assertTrue(invalidValue == null || invalidValue.toString().isBlank());
        assertProdStartFails(
                providedEnvironment,
                expectedCauseMessage,
                invalidVariable + " 누락 또는 공백으로 prod 시작에 실패해야 한다.");
    }

    private void assertProdStartFails(
            Map<String, Object> providedEnvironment,
            String expectedCauseMessage,
            String assertionMessage) {
        StandardEnvironment prodEnvironment = environmentWithoutSystemProperties();
        prodEnvironment
                .getPropertySources()
                .addFirst(
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                providedEnvironment));
        assertProdStartFails(prodEnvironment, expectedCauseMessage, assertionMessage);
    }

    private void assertProdStartFails(
            ConfigurableEnvironment prodEnvironment,
            String expectedCauseMessage,
            String assertionMessage) {
        SpringApplication application = applicationFor(prodEnvironment, "prod");

        Exception exception =
                assertThrows(
                        Exception.class,
                        () -> {
                            try (ConfigurableApplicationContext ignored = application.run()) {
                                // 시작 성공 시 컨텍스트를 닫고 테스트를 실패시킨다.
                            }
                        });

        assertTrue(
                StreamSupport.stream(prodEnvironment.getPropertySources().spliterator(), false)
                        .noneMatch(this::isDotenvPropertySource),
                "prod는 .env를 PropertySource로 불러오지 않아야 한다.");
        assertAll(
                () -> assertArrayEquals(new String[] {"prod"}, prodEnvironment.getActiveProfiles()),
                () ->
                        assertTrue(
                                hasCauseMessage(exception, expectedCauseMessage),
                                assertionMessage));
    }

    private SpringApplication applicationFor(
            ConfigurableEnvironment environment, String... profiles) {
        SpringApplication application = new SpringApplication(IlgeobolkkaApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles(profiles);
        application.setEnvironment(environment);
        return application;
    }

    private StandardEnvironment environmentWithoutSystemProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment
                .getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return environment;
    }

    private boolean isDotenvPropertySource(PropertySource<?> propertySource) {
        return propertySource.getName().contains(".env");
    }

    private boolean hasCauseMessage(Throwable throwable, String expectedMessage) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedMessage)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String requiredEnvironmentVariableMessage(String environmentVariable) {
        return "Required environment variable '"
                + environmentVariable
                + "' must be set and not blank";
    }

    private String conflictingDataSourcePropertyMessage(
            String dataSourceProperty, String environmentVariable) {
        return "Spring datasource property '"
                + dataSourceProperty
                + "' must match environment variable '"
                + environmentVariable
                + "' in the prod profile";
    }
}
