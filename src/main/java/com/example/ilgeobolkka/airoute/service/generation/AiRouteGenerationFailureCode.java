package com.example.ilgeobolkka.airoute.service.generation;

/** G07이 영속화하고 G08이 HTTP 오류로 변환할 공급자 중립 공개 실패 코드다. */
public enum AiRouteGenerationFailureCode {
    AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE,
    AI_ROUTE_PROVIDER_UNAVAILABLE,
    AI_ROUTE_INVALID_OUTPUT,
    AI_ROUTE_GENERATION_TIMEOUT
}
