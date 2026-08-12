package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PerformanceExternalServiceGuardTest {

    @Test
    void PortOne과_OpenAI가_모두_꺼져_있으면_허용한다() {
        assertDoesNotThrow(() ->
                PerformanceExternalServiceGuard.requireExternalServicesDisabled(false, false));
    }

    @Test
    void 외부_서비스가_하나라도_켜지면_거부한다() {
        assertThrows(
                IllegalStateException.class,
                () -> PerformanceExternalServiceGuard.requireExternalServicesDisabled(true, false));
        assertThrows(
                IllegalStateException.class,
                () -> PerformanceExternalServiceGuard.requireExternalServicesDisabled(false, true));
    }
}
