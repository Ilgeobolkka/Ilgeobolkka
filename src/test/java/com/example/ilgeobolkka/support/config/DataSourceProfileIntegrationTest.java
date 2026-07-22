package com.example.ilgeobolkka.support.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.IlgeobolkkaApplication;
import java.sql.Connection;
import java.util.Objects;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

class DataSourceProfileIntegrationTest {

    @Test
    void 로컬_프로파일은_dotenv의_DataSource_설정으로_MySQL에_연결한다() throws Exception {
        SpringApplication application =
                applicationFor("local", environmentWithoutSystemProperties());

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

    @Test
    void 운영_프로파일은_dotenv를_무시하고_필수_환경변수가_없으면_시작에_실패한다() {
        StandardEnvironment prodEnvironment = environmentWithoutSystemProperties();
        SpringApplication application = applicationFor("prod", prodEnvironment);

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
                () -> assertNull(prodEnvironment.getProperty("DB_URL")),
                () ->
                        assertTrue(
                                hasCauseMessage(exception, "'url' must start with \"jdbc\""),
                                "prod는 필수 DB_URL 누락으로 DataSource 생성에 실패해야 한다."));
    }

    private SpringApplication applicationFor(
            String profile, ConfigurableEnvironment environment) {
        SpringApplication application = new SpringApplication(IlgeobolkkaApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles(profile);
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
}
