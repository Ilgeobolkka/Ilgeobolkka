package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Test
    void 외부_호출이_남은_시간보다_오래_걸리면_작업을_취소한다() throws InterruptedException {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-13T00:00:00Z"));
        GenerationTimeBudget budget = GenerationTimeBudget.start(clock, executor);
        clock.advance(Duration.ofSeconds(19).plusMillis(800));
        CountDownLatch operationStarted = new CountDownLatch(1);
        CountDownLatch operationInterrupted = new CountDownLatch(1);

        assertThrows(
                GenerationTimeBudget.TimeLimitExceededException.class,
                () -> budget.call(() -> {
                    operationStarted.countDown();
                    try {
                        new CountDownLatch(1).await();
                        return "도달할 수 없는 결과";
                    } catch (InterruptedException exception) {
                        operationInterrupted.countDown();
                        throw exception;
                    }
                }));

        assertTrue(operationStarted.await(1, TimeUnit.SECONDS));
        assertTrue(operationInterrupted.await(1, TimeUnit.SECONDS));
    }

    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            advance(Duration.ofSeconds(seconds));
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
