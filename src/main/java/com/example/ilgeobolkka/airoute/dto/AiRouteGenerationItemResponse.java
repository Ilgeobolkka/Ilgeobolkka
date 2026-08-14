package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/** 임시 생성 결과의 경로 항목. 내부 페이지 식별자와 분석 텍스트는 노출하지 않는다. */
public record AiRouteGenerationItemResponse(
        int position,
        int pageNumber,
        AiRouteItemRelevance relevance,
        boolean prerequisite,
        AiRouteItemRole role,
        int estimatedMinutes,
        String guide,
        AiRouteAdditionalCostStatus additionalCostStatus) {}
