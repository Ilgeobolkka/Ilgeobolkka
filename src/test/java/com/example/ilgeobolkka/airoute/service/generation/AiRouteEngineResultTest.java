package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import org.junit.jupiter.api.Test;

class AiRouteEngineResultTest {

    private static final AiRouteGenerationResult GENERATION =
            AiRouteGenerationResult.noRelevantPages();

    @Test
    void 생성_결과와_재현_버전을_함께_보존한다() {
        AiRouteEngineResult result = result(
                GENERATION,
                "embedding-v1",
                "route-v1",
                "candidate-v1",
                "prompt-v1",
                "schema-v1");

        assertEquals(GENERATION, result.generation());
        assertEquals("embedding-v1", result.embeddingModel());
        assertEquals("route-v1", result.routeModel());
        assertEquals("candidate-v1", result.candidatePolicyVersion());
        assertEquals("prompt-v1", result.promptVersion());
        assertEquals("schema-v1", result.schemaVersion());
    }

    @Test
    void 생성_결과가_없으면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> result(
                        null,
                        "embedding-v1",
                        "route-v1",
                        "candidate-v1",
                        "prompt-v1",
                        "schema-v1"));
    }

    @Test
    void 재현_버전이_하나라도_비면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> result(
                        GENERATION,
                        " ",
                        "route-v1",
                        "candidate-v1",
                        "prompt-v1",
                        "schema-v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> result(
                        GENERATION,
                        "embedding-v1",
                        null,
                        "candidate-v1",
                        "prompt-v1",
                        "schema-v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> result(
                        GENERATION,
                        "embedding-v1",
                        "route-v1",
                        "",
                        "prompt-v1",
                        "schema-v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> result(
                        GENERATION,
                        "embedding-v1",
                        "route-v1",
                        "candidate-v1",
                        " ",
                        "schema-v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> result(
                        GENERATION,
                        "embedding-v1",
                        "route-v1",
                        "candidate-v1",
                        "prompt-v1",
                        null));
    }

    private AiRouteEngineResult result(
            AiRouteGenerationResult generation,
            String embeddingModel,
            String routeModel,
            String candidatePolicyVersion,
            String promptVersion,
            String schemaVersion) {
        return new AiRouteEngineResult(
                generation,
                embeddingModel,
                routeModel,
                candidatePolicyVersion,
                promptVersion,
                schemaVersion);
    }
}
