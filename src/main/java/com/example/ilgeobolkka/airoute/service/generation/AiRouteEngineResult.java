package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;

/** 사용자·HTTP·영속 계층과 독립적인 생성 결과와 재현 버전 묶음이다. */
public record AiRouteEngineResult(
        AiRouteGenerationResult generation,
        String embeddingModel,
        String routeModel,
        String candidatePolicyVersion,
        String promptVersion,
        String schemaVersion) {

    public AiRouteEngineResult {
        if (generation == null) {
            throw new IllegalArgumentException("생성 엔진 결과가 필요합니다.");
        }
        requireVersion(embeddingModel, "embedding model");
        requireVersion(routeModel, "route model");
        requireVersion(candidatePolicyVersion, "후보 정책");
        requireVersion(promptVersion, "prompt");
        requireVersion(schemaVersion, "schema");
    }

    private static void requireVersion(String version, String name) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(name + " 버전이 필요합니다.");
        }
    }
}
