package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 생성 POST와 소유자 GET이 공유하는 전체 응답 계약. nullable 필드도 JSON에서 생략하지 않는다. */
public record AiRouteGenerationResponse(
        UUID generationId,
        AiRouteGenerationStatus status,
        long bookId,
        String contentVersion,
        String purpose,
        Instant expiresAt,
        Long routeId,
        int remainingDailyGenerations,
        AiRouteNoRouteReason noRouteReason,
        Integer minimumRequiredInk,
        List<AiRouteGenerationItemResponse> items) {

    public AiRouteGenerationResponse {
        items = List.copyOf(items);
    }
}
