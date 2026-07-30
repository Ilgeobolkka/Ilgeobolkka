package com.example.ilgeobolkka.infra.portone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PortOnePaymentPropertiesTest {

    @Test
    void 결제를_활성화할_때_필요한_설정이_모두_있으면_검증을_통과한다() {
        PortOnePaymentProperties properties = new PortOnePaymentProperties(
                true,
                "store-test",
                "channel-test",
                "api-secret-test",
                "webhook-secret-test");

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    @Test
    void 필수_설정이_없으면_값을_노출하지_않고_환경변수_이름으로_실패한다() {
        PortOnePaymentProperties properties = new PortOnePaymentProperties(
                true,
                "store-test",
                "channel-test",
                "api-secret-test",
                "");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                properties::validateEnabledConfiguration);

        assertEquals(
                "PortOne 결제 활성화에는 PORTONE_WEBHOOK_SECRET 값이 필요합니다.",
                exception.getMessage());
    }
}
