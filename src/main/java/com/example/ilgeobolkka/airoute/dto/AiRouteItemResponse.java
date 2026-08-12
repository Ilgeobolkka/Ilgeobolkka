package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.repository.AiRouteItemProjection;
import com.example.ilgeobolkka.airoute.service.query.AiRouteItemGuideAssembler;
import java.time.Instant;

/**
 * 저장 경로 상세의 항목 한 건.
 *
 * <p>{@code pageNumber}는 원본 PDF 페이지 번호다. 내부 {@code book_page} 식별자는 응답 계약이 아니라서
 * 싣지 않는다.
 *
 * <p>{@code additionalCostStatus}만 조회 시점 권한으로 다시 계산한 값이고 나머지는 저장한 값이다.
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
            AiRouteAdditionalCostStatus additionalCostStatus) {
        return new AiRouteItemResponse(
                item.getPosition(),
                item.getPageNumber(),
                item.getRelevance(),
                item.isPrerequisite(),
                item.getRole(),
                AiRouteItemGuideAssembler.estimatedMinutes(item.getEstimatedReadingSeconds()),
                AiRouteItemGuideAssembler.guide(item.getRole(), item.getGuideTopic()),
                additionalCostStatus,
                item.getOpenedAt());
    }
}
