package com.example.ilgeobolkka.airoute.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiRouteGenerationApiFacadeTest {

    @Test
    void 초기화_시각을_이미_지났으면_즉시_재시도할_수_있다() {
        Instant resetAt = Instant.parse("2026-08-14T00:00:00Z");

        assertEquals(
                0,
                AiRouteGenerationApiFacade.retryAfterSeconds(
                        resetAt.plusNanos(1), resetAt));
    }

    @Test
    void 초기화까지_남은_초는_올림한다() {
        Instant resetAt = Instant.parse("2026-08-14T00:00:00Z");

        assertEquals(
                1,
                AiRouteGenerationApiFacade.retryAfterSeconds(
                        resetAt.minusNanos(1), resetAt));
    }
}
