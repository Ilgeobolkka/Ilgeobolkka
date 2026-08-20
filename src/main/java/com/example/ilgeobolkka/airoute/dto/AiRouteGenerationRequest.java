package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 생성 HTTP 입력. 소장 여부에 따른 예산·깊이 조합은 서버 상태를 아는 Facade가 검증한다. */
public record AiRouteGenerationRequest(
        @NotNull String purpose,
        @PositiveOrZero Integer maxAdditionalInk,
        AiRouteDepth depth) {}
