package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/**
 * 임시 결과의 경로 항목 하나. 저장된 값만 담는다.
 *
 * <p>{@code estimatedMinutes}와 {@code guide}는 이미 저장된 페이지 값에서 만든다. 예상 시간은 페이지
 * 콘텐츠 길이·형식으로 계산하고, 가이드는
 * 검수된 공개 가이드 주제와 경로 역할을 고정 템플릿에 넣어 만든다. 둘 다 사용자 상태에 기대지 않아
 * 조회 때 만들어도 값이 달라지지 않는다.
 *
 * <p>{@code additionalCostStatus}는 조립기가 계산한 생성 시점 값을 항목에 저장한 것이다. 업무 시각 기반
 * 권한 이력은 동시 transaction의 커밋 순서를 복원하지 못하므로 조회에서 재계산하지 않는다.
 *
 * @param position 1부터 시작하는 추천 순서
 * @param pageNumber 화면에 보여 줄 원본 페이지 번호
 */
public record AiRouteGenerationItemView(
        int position,
        int pageNumber,
        AiRouteItemRelevance relevance,
        boolean prerequisite,
        AiRouteItemRole role,
        Integer estimatedReadingSeconds,
        String publicGuideTopic,
        AiRouteAdditionalCostStatus additionalCostStatus) {

    static AiRouteGenerationItemView from(AiRouteGenerationItem item) {
        return new AiRouteGenerationItemView(
                item.getPosition(),
                item.getBookPage().getPageNumber(),
                item.getRelevance(),
                item.isPrerequisite(),
                item.getRole(),
                item.getBookPage().getEstimatedReadingSeconds(),
                item.getBookPage().getAiPublicGuideTopic(),
                item.getAdditionalCostStatus());
    }
}
