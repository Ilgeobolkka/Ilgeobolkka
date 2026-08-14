package com.example.ilgeobolkka.airoute.dto;

/** Controller가 생성 결과 바디의 200·201·202를 선택하는 데 필요한 HTTP 독립 판정이다. */
public record AiRouteGenerationApiResult(
        AiRouteGenerationResponse response,
        boolean created,
        boolean generating) {}
