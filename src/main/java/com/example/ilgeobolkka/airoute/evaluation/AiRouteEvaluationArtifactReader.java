package com.example.ilgeobolkka.airoute.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Q01 결과·사람 판정·Q02 report를 단계별 비웹 배치 입력으로 읽는다. */
@Component
@Profile("evaluation")
final class AiRouteEvaluationArtifactReader {

    private final ObjectMapper objectMapper;

    AiRouteEvaluationArtifactReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiRouteEvaluationResult readResult(Path input) {
        return read(input, AiRouteEvaluationResult.class, "Q01 평가 결과");
    }

    List<AiRouteHumanJudgment> readJudgments(Path input) {
        AiRouteHumanJudgment[] judgments =
                read(input, AiRouteHumanJudgment[].class, "사람 유용성 판정");
        return List.copyOf(Arrays.asList(judgments));
    }

    AiRouteEvaluationReport readReport(Path input) {
        return read(input, AiRouteEvaluationReport.class, "Q02 평가 report");
    }

    private <T> T read(Path input, Class<T> type, String name) {
        if (input == null) {
            throw new IllegalArgumentException(name + " input 경로가 필요합니다.");
        }
        Path target = input.toAbsolutePath().normalize();
        try {
            return objectMapper.readValue(Files.readAllBytes(target), type);
        } catch (JacksonException exception) {
            throw new IllegalStateException(name + " JSON을 읽을 수 없습니다: " + target, exception);
        } catch (IOException exception) {
            throw new IllegalStateException(name + " 파일을 읽을 수 없습니다: " + target, exception);
        }
    }
}
