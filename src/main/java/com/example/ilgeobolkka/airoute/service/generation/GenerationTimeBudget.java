package com.example.ilgeobolkka.airoute.service.generation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 최초 요청부터 20초인 전체 생성 제한을 외부 호출 사이에서 공유한다. */
public final class GenerationTimeBudget {

    private final Clock clock;
    private final ExecutorService executor;
    private final Instant deadline;

    private GenerationTimeBudget(Clock clock, ExecutorService executor, Instant deadline) {
        this.clock = clock;
        this.executor = executor;
        this.deadline = deadline;
    }

    public static GenerationTimeBudget start(Clock clock, ExecutorService executor) {
        if (clock == null || executor == null) {
            throw new IllegalArgumentException("생성 제한 시간에 Clock과 실행기가 필요합니다.");
        }
        return new GenerationTimeBudget(
                clock,
                executor,
                clock.instant().plus(AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT));
    }

    public <T> T call(Callable<T> operation) {
        Duration remaining = remaining();
        if (remaining.isZero() || remaining.isNegative()) {
            throw new TimeLimitExceededException();
        }

        Future<T> future = executor.submit(operation);
        try {
            T result = future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
            requireRemaining();
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new TimeLimitExceededException();
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new TimeLimitExceededException();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("외부 호출 실행에 실패했습니다.", cause);
        }
    }

    public void requireRemaining() {
        if (expired()) {
            throw new TimeLimitExceededException();
        }
    }

    public boolean expired() {
        return !clock.instant().isBefore(deadline);
    }

    public Instant deadline() {
        return deadline;
    }

    private Duration remaining() {
        Duration remaining = Duration.between(clock.instant(), deadline);
        Duration limit = AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT;
        return remaining.compareTo(limit) > 0 ? limit : remaining;
    }

    public static final class TimeLimitExceededException extends RuntimeException {

        private TimeLimitExceededException() {
            super("AI 경로 생성 전체 제한 시간을 초과했습니다.");
        }
    }
}
