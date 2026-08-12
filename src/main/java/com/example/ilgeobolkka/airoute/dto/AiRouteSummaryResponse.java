package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import com.example.ilgeobolkka.airoute.repository.AiRouteSummaryProjection;
import java.time.Instant;

/**
 * 저장 경로 목록의 한 행.
 *
 * <p>{@code current}는 조회 시점에 이 경로가 해당 도서의 현재 경로인지다. 목록과 상세가 같은 판정을 쓴다.
 */
public record AiRouteSummaryResponse(
        long routeId,
        long bookId,
        String bookTitle,
        String purpose,
        boolean current,
        Instant createdAt,
        Instant completedAt,
        AiReadingRouteFeedback rating) {

    public static AiRouteSummaryResponse from(AiRouteSummaryProjection route) {
        return new AiRouteSummaryResponse(
                route.getRouteId(),
                route.getBookId(),
                route.getBookTitle(),
                route.getPurpose(),
                route.getCurrentRouteId() != null,
                route.getCreatedAt(),
                route.getCompletedAt(),
                route.getRating());
    }
}
