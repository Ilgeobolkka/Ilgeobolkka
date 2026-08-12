package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/**
 * 임시 결과의 경로 항목 하나. 저장된 값만 담는다.
 *
 * <p>{@code estimatedMinutes}·{@code guide}·{@code additionalCostStatus} 는 여기 없다. 셋 다 저장하는
 * 열이 아니다.
 *
 * <p>앞의 둘은 이미 저장된 값에서 만든다. 예상 시간은 페이지 콘텐츠 길이·형식으로 계산하고, 가이드는
 * 검수된 공개 가이드 주제와 경로 역할을 고정 템플릿에 넣어 만든다. 둘 다 사용자 상태에 기대지 않아
 * 조회 때 만들어도 값이 달라지지 않는다.
 *
 * <p>{@code additionalCostStatus} 는 다르다. 정본이 <b>생성 시점 스냅샷</b>으로 규정해 재조회에도
 * 재계산하지 않는다. 그 실현 방식은 G08 leaf 가 자기 결정으로 명시해 두었다. 컬럼 없이
 * {@code page_rental} 의 과거 기간으로 재구성할 수도 있고, 저장 방식을 택하면 그때 F01 migration 승인을
 * 받는다. 여기서 미리 정하지 않는다.
 *
 * @param position 1부터 시작하는 추천 순서
 * @param pageNumber 화면에 보여 줄 원본 페이지 번호
 */
public record AiRouteGenerationItemView(
        int position,
        int pageNumber,
        AiRouteItemRelevance relevance,
        boolean prerequisite,
        AiRouteItemRole role) {

    static AiRouteGenerationItemView from(AiRouteGenerationItem item) {
        return new AiRouteGenerationItemView(
                item.getPosition(),
                item.getBookPage().getPageNumber(),
                item.getRelevance(),
                item.isPrerequisite(),
                item.getRole());
    }
}
