package com.example.ilgeobolkka.airoute.service;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import java.util.List;

/** 권한 조회 방식과 무관하게 AI 경로 페이지의 추가 비용 상태를 판정하는 순수 규칙이다. */
public final class AiRouteAdditionalCostPolicy {

    private AiRouteAdditionalCostPolicy() {}

    /** 소장이 모든 페이지 대여보다 우선하고, 소장도 활성 대여도 없을 때만 1잉크가 필요하다. */
    public static AiRouteAdditionalCostStatus status(boolean owned, boolean activeRental) {
        if (owned) {
            return AiRouteAdditionalCostStatus.OWNED;
        }
        if (activeRental) {
            return AiRouteAdditionalCostStatus.ACTIVE_RENTAL;
        }
        return AiRouteAdditionalCostStatus.ONE_INK;
    }

    /** 페이지 묶음에서 실제로 1잉크가 필요한 항목 수를 계산한다. */
    public static int additionalInk(List<AiRouteAdditionalCostStatus> statuses) {
        if (statuses == null) {
            throw new IllegalArgumentException("추가 비용 상태 목록이 필요합니다.");
        }
        return (int) statuses.stream()
                .filter(status -> status == AiRouteAdditionalCostStatus.ONE_INK)
                .count();
    }
}
