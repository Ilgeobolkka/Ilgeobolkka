package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/**
 * 임시 결과의 경로 항목 하나. 저장된 값만 담는다.
 *
 * <p>{@code estimatedMinutes}·{@code guide}·{@code additionalCostStatus} 는 여기 없다. 셋 다 저장하는
 * 열이 없고 {@code src/main} 어디에도 아직 구현이 없다. 특히 {@code additionalCostStatus} 는 정본이
 * <b>생성 시점 스냅샷</b>으로 규정해(다시 조회해도 현재 권한으로 재계산하지 않는다) 조회 때 계산하면 안
 * 되는데, 그 스냅샷을 담을 자리가 스키마에 없다. 이 어긋남은 G06 인계에 미결로 남겼다.
 *
 * @param position 1부터 시작하는 추천 순서
 * @param pageNumber 화면에 보여 줄 원본 페이지 번호
 * @param bookPageId 스냅샷을 이력으로 재구성하는 방식을 고를 경우에 필요하다. G08 이 어떤 방식으로
 *     실현할지 정해지기 전까지 그 선택지를 살려 두려고 함께 넘긴다.
 */
public record AiRouteGenerationItemView(
        int position,
        int pageNumber,
        long bookPageId,
        AiRouteItemRelevance relevance,
        boolean prerequisite,
        AiRouteItemRole role) {

    static AiRouteGenerationItemView from(AiRouteGenerationItem item) {
        return new AiRouteGenerationItemView(
                item.getPosition(),
                item.getBookPage().getPageNumber(),
                item.getBookPageId(),
                item.getRelevance(),
                item.isPrerequisite(),
                item.getRole());
    }
}
