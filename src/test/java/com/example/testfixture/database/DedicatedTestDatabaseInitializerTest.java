package com.example.testfixture.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;

class DedicatedTestDatabaseInitializerTest {

    private final DedicatedTestDatabaseInitializer initializer =
            new DedicatedTestDatabaseInitializer();

    @Test
    void MySQL_테스트_데이터베이스_URL을_허용한다() {
        GenericApplicationContext applicationContext =
                contextWithUrl(
                        "jdbc:mysql://localhost:3307/ilgeobolkka_test"
                                + "?allowPublicKeyRetrieval=true&sslMode=DISABLED");

        assertDoesNotThrow(() -> initializer.initialize(applicationContext));
    }

    @Test
    void 테스트가_개발_데이터베이스를_가리키면_거부한다() {
        GenericApplicationContext applicationContext =
                contextWithUrl("jdbc:mysql://localhost:3307/ilgeobolkka");

        assertThrows(
                IllegalStateException.class,
                () -> initializer.initialize(applicationContext));
    }

    @Test
    void MySQL이_아닌_데이터베이스_URL을_거부한다() {
        GenericApplicationContext applicationContext =
                contextWithUrl("jdbc:h2:mem:ilgeobolkka_test");

        assertThrows(
                IllegalStateException.class,
                () -> initializer.initialize(applicationContext));
    }

    private GenericApplicationContext contextWithUrl(String jdbcUrl) {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        TestPropertyValues.of("spring.datasource.url=" + jdbcUrl).applyTo(applicationContext);
        return applicationContext;
    }
}
