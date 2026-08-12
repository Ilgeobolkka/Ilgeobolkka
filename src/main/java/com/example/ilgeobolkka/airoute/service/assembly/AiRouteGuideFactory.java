package com.example.ilgeobolkka.airoute.service.assembly;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/** 검수된 공개 주제와 서버 역할 템플릿만으로 페이지를 열기 전 가이드를 만든다. */
public final class AiRouteGuideFactory {

    public String create(String publicGuideTopic, AiRouteItemRole role) {
        if (publicGuideTopic == null || publicGuideTopic.isBlank()) {
            throw new IllegalArgumentException("공개 가이드 주제가 필요합니다.");
        }
        if (role == null) {
            throw new IllegalArgumentException("경로 역할이 필요합니다.");
        }

        String roleGuide = switch (role) {
            case PREREQUISITE -> "선수 개념을 먼저 살펴보는 페이지입니다.";
            case CORE -> "핵심 개념을 살펴보는 페이지입니다.";
            case EXAMPLE -> "개념이 사례에 적용되는 방식을 살펴보는 페이지입니다.";
            case COUNTERPOINT -> "다른 관점과 반론을 살펴보는 페이지입니다.";
            case CONCLUSION -> "앞선 내용을 정리하는 페이지입니다.";
        };

        return roleGuide + " 주제는 \"" + publicGuideTopic + "\"입니다.";
    }
}
