package com.example.ilgeobolkka.airoute.evaluation;

/** 지정 검수자가 case별 표시 경로의 유용성을 판정한 비민감 입력이다. */
public record AiRouteHumanJudgment(String caseId, boolean useful) {

    public AiRouteHumanJudgment {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("사람 판정의 caseId가 필요합니다.");
        }
    }
}
