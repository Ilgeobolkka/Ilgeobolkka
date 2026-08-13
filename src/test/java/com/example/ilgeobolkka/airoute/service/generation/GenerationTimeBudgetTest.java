package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GenerationTimeBudgetTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.shutdownNow();
    }

    @Test
    void 제한_안에서_외부_호출_결과를_반환한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        GenerationTimeBudget budget = GenerationTimeBudget.start(clock, executor);

        assertEquals(
                Instant.parse("2026-08-13T00:00:20Z"),
                budget.deadline());
        assertEquals("완료", budget.call(() -> "완료"));
    }

    @Test
    void 외부_호출이_끝났어도_전체_20초를_넘었으면_실패한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        GenerationTimeBudget budget = GenerationTimeBudget.start(clock, executor);

        assertThrows(
                GenerationTimeBudget.TimeLimitExceededException.class,
                () -> budget.call(() -> {
                    clock.advanceSeconds(20);
                    return "늦은 결과";
                }));
    }

    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
