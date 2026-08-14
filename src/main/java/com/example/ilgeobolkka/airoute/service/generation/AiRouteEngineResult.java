package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import java.util.List;

/** 사용자·HTTP·영속 계층과 독립적인 생성 결과와 재현 버전 묶음이다. */
public record AiRouteEngineResult(
        AiRouteGenerationResult generation,
        String embeddingModel,
        String routeModel,
        String candidatePolicyVersion,
        String promptVersion,
        String schemaVersion,
        List<CandidateScore> candidateScores) {

    public AiRouteEngineResult {
        if (generation == null) {
            throw new IllegalArgumentException("생성 엔진 결과가 필요합니다.");
        }
        requireVersion(embeddingModel, "embedding model");
        requireVersion(routeModel, "route model");
        requireVersion(candidatePolicyVersion, "후보 정책");
        requireVersion(promptVersion, "prompt");
        requireVersion(schemaVersion, "schema");
        if (candidateScores == null) {
            throw new IllegalArgumentException("후보 점수 목록이 필요합니다.");
        }
        candidateScores = List.copyOf(candidateScores);
    }

    private static void requireVersion(String version, String name) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(name + " 버전이 필요합니다.");
        }
    }

    /** 평가가 정책 임계값을 비교할 때 쓰는 비민감 후보 점수다. */
    public record CandidateScore(int pageNumber, double similarity) {

        public CandidateScore {
            if (pageNumber <= 0 || !Double.isFinite(similarity)) {
                throw new IllegalArgumentException("후보 페이지 번호와 유한한 유사도가 필요합니다.");
            }
        }
    }
}
