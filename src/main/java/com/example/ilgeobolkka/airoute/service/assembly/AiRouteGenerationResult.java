package com.example.ilgeobolkka.airoute.service.assembly;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import java.util.List;

/** 공급자와 영속 계층에 독립적인 경로 조립 결과. */
public record AiRouteGenerationResult(
        Status status,
        List<Item> items,
        NoRouteReason noRouteReason,
        Integer minimumRequiredInk) {

    public AiRouteGenerationResult {
        if (status == null) {
            throw new IllegalArgumentException("경로 조립 결과 상태가 필요합니다.");
        }
        if (items == null) {
            throw new IllegalArgumentException("경로 조립 결과 항목이 필요합니다.");
        }
        items = List.copyOf(items);

        if (status == Status.ROUTE
                && (items.isEmpty() || noRouteReason != null || minimumRequiredInk != null)) {
            throw new IllegalArgumentException("ROUTE 결과는 항목만 가져야 합니다.");
        }
        if (status == Status.NO_ROUTE && (!items.isEmpty() || noRouteReason == null)) {
            throw new IllegalArgumentException("NO_ROUTE 결과는 사유만 가져야 합니다.");
        }
        if (noRouteReason != NoRouteReason.INSUFFICIENT_BUDGET
                && minimumRequiredInk != null) {
            throw new IllegalArgumentException("예산 부족 외의 경로 없음에는 최소 필요 잉크가 없습니다.");
        }
        if (noRouteReason == NoRouteReason.INSUFFICIENT_BUDGET
                && (minimumRequiredInk == null || minimumRequiredInk <= 0)) {
            throw new IllegalArgumentException("예산 부족에는 양수인 최소 필요 잉크가 필요합니다.");
        }
    }

    public static AiRouteGenerationResult route(List<Item> items) {
        return new AiRouteGenerationResult(Status.ROUTE, items, null, null);
    }

    public static AiRouteGenerationResult noRelevantPages() {
        return new AiRouteGenerationResult(
                Status.NO_ROUTE, List.of(), NoRouteReason.NO_RELEVANT_PAGES, null);
    }

    public static AiRouteGenerationResult insufficientBudget(int minimumRequiredInk) {
        return new AiRouteGenerationResult(
                Status.NO_ROUTE,
                List.of(),
                NoRouteReason.INSUFFICIENT_BUDGET,
                minimumRequiredInk);
    }

    public enum Status {
        ROUTE,
        NO_ROUTE
    }

    /** G04 조립 단계 안에서만 사용하는 경로 없음 사유. 공개 API·DB 계약과 독립적이다. */
    public enum NoRouteReason {
        NO_RELEVANT_PAGES,
        INSUFFICIENT_BUDGET
    }

    /**
     * 최종 경로 항목. {@code additionalCostStatus}는 조회 시 다시 계산하지 않는 생성 시점 사본이다.
     */
    public record Item(
            long pageId,
            int pageNumber,
            int position,
            AiRouteItemRelevance relevance,
            boolean prerequisite,
            AiRouteItemRole role,
            int estimatedMinutes,
            String guide,
            AiRouteAdditionalCostStatus additionalCostStatus) {}
}
