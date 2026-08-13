package com.example.ilgeobolkka.airoute.service.assembly;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.service.query.AiRouteItemGuideAssembler;

/** 검수된 공개 주제와 서버 역할 템플릿만으로 페이지를 열기 전 가이드를 만든다. */
public final class AiRouteGuideFactory {

    public String create(String publicGuideTopic, AiRouteItemRole role) {
        if (publicGuideTopic == null || publicGuideTopic.isBlank()) {
            throw new IllegalArgumentException("공개 가이드 주제가 필요합니다.");
        }
        if (role == null) {
            throw new IllegalArgumentException("경로 역할이 필요합니다.");
        }

        return AiRouteItemGuideAssembler.guide(role, publicGuideTopic);
    }
}
