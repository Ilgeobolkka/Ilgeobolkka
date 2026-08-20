package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import java.time.Instant;

/** HTTP를 모르는 생성 실행 결과. G08은 이 값만 상태 코드와 응답 DTO로 변환한다. */
public record GenerationExecutionResult(
        Execution execution,
        State state,
        AiRouteGenerationView generation,
        AiRouteGenerationFailureCode failure,
        Instant retryAfterAt) {

    public static GenerationExecutionResult created(AiRouteGenerationView generation) {
        return finalResult(Execution.NEW, generation);
    }

    public static GenerationExecutionResult replayed(AiRouteGenerationView generation) {
        State state = generation.status() == AiRouteGenerationStatus.GENERATING
                ? State.GENERATING
                : State.FINAL;
        return new GenerationExecutionResult(
                Execution.REPLAY, state, generation, failureOf(generation), null);
    }

    public static GenerationExecutionResult keyReused() {
        return new GenerationExecutionResult(
                Execution.REJECTED, State.KEY_REUSED, null, null, null);
    }

    public static GenerationExecutionResult dailyLimit(Instant retryAfterAt) {
        if (retryAfterAt == null) {
            throw new IllegalArgumentException("일일 생성 횟수 초기화 시각은 필수입니다.");
        }
        return new GenerationExecutionResult(
                Execution.REJECTED, State.DAILY_LIMIT, null, null, retryAfterAt);
    }

    public static GenerationExecutionResult timedOutBeforeStart() {
        return new GenerationExecutionResult(
                Execution.REJECTED,
                State.TIMEOUT,
                null,
                AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT,
                null);
    }

    private static GenerationExecutionResult finalResult(
            Execution execution, AiRouteGenerationView generation) {
        return new GenerationExecutionResult(
                execution, State.FINAL, generation, failureOf(generation), null);
    }

    private static AiRouteGenerationFailureCode failureOf(AiRouteGenerationView generation) {
        return generation.failureCode() == null
                ? null
                : AiRouteGenerationFailureCode.valueOf(generation.failureCode());
    }

    public enum Execution {
        NEW,
        REPLAY,
        REJECTED
    }

    public enum State {
        GENERATING,
        FINAL,
        KEY_REUSED,
        DAILY_LIMIT,
        TIMEOUT
    }
}
