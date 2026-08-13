package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationExecutionResultTest {

    @Test
    void 기존_진행_중_생성은_REPLAY와_GENERATING으로_구분한다() {
        GenerationExecutionResult result = GenerationExecutionResult.replayed(
                view(AiRouteGenerationStatus.GENERATING, null));

        assertEquals(GenerationExecutionResult.Execution.REPLAY, result.execution());
        assertEquals(GenerationExecutionResult.State.GENERATING, result.state());
        assertNull(result.failure());
    }

    @Test
    void 새_실패는_FINAL과_공개_실패_종류를_함께_넘긴다() {
        GenerationExecutionResult result = GenerationExecutionResult.created(view(
                AiRouteGenerationStatus.FAILED,
                AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT.name()));

        assertEquals(GenerationExecutionResult.Execution.NEW, result.execution());
        assertEquals(GenerationExecutionResult.State.FINAL, result.state());
        assertEquals(AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT, result.failure());
    }

    @Test
    void 생성_시작_전_시간_초과는_생성_없이_공개_실패를_넘긴다() {
        GenerationExecutionResult result = GenerationExecutionResult.timedOutBeforeStart();

        assertEquals(GenerationExecutionResult.Execution.REJECTED, result.execution());
        assertEquals(GenerationExecutionResult.State.TIMEOUT, result.state());
        assertNull(result.generation());
        assertEquals(
                AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT,
                result.failure());
    }

    private AiRouteGenerationView view(AiRouteGenerationStatus status, String failureCode) {
        return new AiRouteGenerationView(
                UUID.randomUUID(),
                1L,
                "v1",
                status,
                "목적",
                null,
                null,
                failureCode,
                null,
                status == AiRouteGenerationStatus.GENERATING
                        ? null
                        : Instant.parse("2026-08-13T00:15:00Z"),
                List.of());
    }
}
