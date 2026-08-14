package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/**
 * 완성된 경로의 항목 하나. 생성 결과를 저장할 때 호출자가 넘기는 값이며 Entity 가 아니다.
 *
 * <p>{@code bookId} 는 받지 않는다. 생성 행이 이미 대상 도서를 들고 있으므로, 여기서 또 받으면 호출자가
 * 다른 도서의 페이지를 섞어 넣을 수 있는 입구가 하나 더 생긴다.
 *
 * @param bookPageId 경로에 넣을 페이지
 * @param position 1부터 시작하는 추천 순서
 * @param relevance 목적과의 관련도
 * @param prerequisite 선수 페이지로 끌려 들어온 항목인지
 * @param role 경로 안에서의 역할
 * @param additionalCostStatus 생성 권한 사본으로 계산한 추가 비용 상태
 */
public record AiRouteResultItem(
        long bookPageId,
        int position,
        AiRouteItemRelevance relevance,
        boolean prerequisite,
        AiRouteItemRole role,
        AiRouteAdditionalCostStatus additionalCostStatus) {}
