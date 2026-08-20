package com.example.ilgeobolkka.airoute.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("evaluation")
class AiRouteEvaluationResultWriter {

    private final Path output;
    private final ObjectMapper objectMapper;

    AiRouteEvaluationResultWriter(
            AiRouteEvaluationProperties properties, ObjectMapper objectMapper) {
        this.output = properties.output();
        this.objectMapper = objectMapper;
    }

    void requireAvailable() {
        if (output == null) {
            throw new IllegalArgumentException("평가 결과 output 경로가 필요합니다.");
        }
        if (Files.exists(output)) {
            throw new IllegalStateException("기존 평가 결과를 덮어쓰지 않습니다: " + output);
        }
    }

    void write(AiRouteEvaluationResult result) {
        try {
            Path parent = output.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] artifact = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
            Files.write(output, artifact, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("평가 결과를 JSON으로 만들 수 없습니다.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("평가 결과 파일을 쓸 수 없습니다: " + output, exception);
        }
    }

    Path output() {
        return output;
    }
}
