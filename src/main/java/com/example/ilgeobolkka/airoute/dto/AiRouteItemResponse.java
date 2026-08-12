package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.repository.AiRouteItemProjection;
import java.time.Instant;

/**
 * 저장 경로 상세의 항목 한 건.
 *
 * <p>{@code pageNumber}는 원본 PDF 페이지 번호다. 내부 {@code book_page} 식별자는 응답 계약이 아니라서
 * 싣지 않는다.
 *
 * <p>{@code additionalCostStatus}만 조회 시점 권한으로 다시 계산한 값이고 나머지는 저장한 값이다.
 *
 * <p>{@code guide}·{@code estimatedMinutes}는 조립을 마친 값으로 받는다. 문구 규칙을 여기서 부르면 dto 가
 * {@code service.query}를, {@code service.query}가 다시 dto 를 임포트해 두 패키지가 순환한다.
 */
public record AiRouteItemResponse(
        int position,
        int pageNumber,
        AiRouteItemRelevance relevance,
        boolean prerequisite,
        AiRouteItemRole role,
        int estimatedMinutes,
        String guide,
        AiRouteAdditionalCostStatus additionalCostStatus,
        Instant openedAt) {

    public static AiRouteItemResponse of(
            AiRouteItemProjection item,
            int estimatedMinutes,
            String guide,
            AiRouteAdditionalCostStatus additionalCostStatus) {
        return new AiRouteItemResponse(
                item.getPosition(),
                item.getPageNumber(),
                item.getRelevance(),
                item.isPrerequisite(),
                item.getRole(),
                estimatedMinutes,
                guide,
                additionalCostStatus,
                item.getOpenedAt());
    }
}
