package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import com.example.ilgeobolkka.airoute.repository.AiRouteSummaryProjection;
import java.time.Instant;
import java.util.List;

/**
 * 저장 경로 상세 응답.
 *
 * <p>생성 결과의 도서·목적·항목에 {@code routeId}·{@code current}·{@code createdAt}·
 * {@code completedAt}·{@code rating}을 더한 형태다.
 *
 * <p>분석 text·embedding·선수 개념 그래프와 생성 fingerprint 는 여기에 없다. 저장 경로 상세는 독자에게
 * 보이는 계약이고, 그 넷은 어느 것도 화면이나 클라이언트가 쓰지 않는 내부 값이다.
 */
public record FindAiRouteResponse(
        long routeId,
        long bookId,
        String bookTitle,
        String purpose,
        boolean current,
        Instant createdAt,
        Instant completedAt,
        boolean evaluationAvailable,
        AiReadingRouteFeedback rating,
        List<AiRouteItemResponse> items) {

    public static FindAiRouteResponse of(
            AiRouteSummaryProjection route,
            List<AiRouteItemResponse> items) {
        return new FindAiRouteResponse(
                route.getRouteId(),
                route.getBookId(),
                route.getBookTitle(),
                route.getPurpose(),
                route.getCurrentRouteId() != null,
                route.getCreatedAt(),
                route.getCompletedAt(),
                route.getCompletedAt() != null
                        && !items.isEmpty()
                        && items.stream().allMatch(item -> item.openedAt() != null),
                route.getRating(),
                items);
    }
}
