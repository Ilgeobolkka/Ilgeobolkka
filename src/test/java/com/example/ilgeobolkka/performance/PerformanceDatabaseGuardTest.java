package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PerformanceDatabaseGuardTest {

    @Test
    void 정확한_성능_데이터베이스만_허용한다() {
        assertDoesNotThrow(() -> PerformanceDatabaseGuard.requirePerformanceDatabaseName(
                "ilgeobolkka_perf"));
    }

    @Test
    void 개발_테스트_유사_이름과_빈_이름을_거부한다() {
        assertThrows(
                IllegalStateException.class,
                () -> PerformanceDatabaseGuard.requirePerformanceDatabaseName("ilgeobolkka"));
        assertThrows(
                IllegalStateException.class,
                () -> PerformanceDatabaseGuard.requirePerformanceDatabaseName(
                        "ilgeobolkka_test"));
        assertThrows(
                IllegalStateException.class,
                () -> PerformanceDatabaseGuard.requirePerformanceDatabaseName(
                        "ilgeobolkka_perf_test"));
        assertThrows(
                IllegalStateException.class,
                () -> PerformanceDatabaseGuard.requirePerformanceDatabaseName(null));
    }
}
