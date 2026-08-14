package com.example.ilgeobolkka.airoute.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationFailureCode;
import com.example.ilgeobolkka.global.exception.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiRouteGenerationApiFacadeTest {

    @Test
    void 공개_생성_실패를_API_오류로_명시적으로_매핑한다() {
        assertEquals(
                ErrorCode.AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE,
                AiRouteGenerationApiFacade.failureErrorCode(
                        AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE));
        assertEquals(
                ErrorCode.AI_ROUTE_PROVIDER_UNAVAILABLE,
                AiRouteGenerationApiFacade.failureErrorCode(
                        AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE));
        assertEquals(
                ErrorCode.AI_ROUTE_INVALID_OUTPUT,
                AiRouteGenerationApiFacade.failureErrorCode(
                        AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT));
        assertEquals(
                ErrorCode.AI_ROUTE_GENERATION_TIMEOUT,
                AiRouteGenerationApiFacade.failureErrorCode(
                        AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT));
    }

    @Test
    void 알_수_없는_저장_실패_코드는_내부_서버_오류로_대체한다() {
        assertEquals(
                ErrorCode.INTERNAL_SERVER_ERROR,
                AiRouteGenerationApiFacade.failureErrorCode("UNKNOWN_ERROR"));
        assertEquals(
                ErrorCode.INTERNAL_SERVER_ERROR,
                AiRouteGenerationApiFacade.failureErrorCode((String) null));
    }

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
