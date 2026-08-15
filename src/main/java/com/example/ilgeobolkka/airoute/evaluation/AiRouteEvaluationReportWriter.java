package com.example.ilgeobolkka.airoute.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 평가 report를 기존 파일을 덮어쓰지 않고 JSON artifact로 기록한다. */
@Component
@Profile("evaluation")
final class AiRouteEvaluationReportWriter {

    private final ObjectMapper objectMapper;

    AiRouteEvaluationReportWriter(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("평가 report ObjectMapper가 필요합니다.");
        }
        this.objectMapper = objectMapper;
    }

    void requireAvailable(Path output) {
        if (output == null) {
            throw new IllegalArgumentException("평가 report output 경로가 필요합니다.");
        }
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new IllegalStateException("기존 평가 report를 덮어쓰지 않습니다: " + target);
        }
    }

    Artifact write(Path output, AiRouteEvaluationReport report) {
        if (output == null || report == null) {
            throw new IllegalArgumentException("평가 report와 output 경로가 필요합니다.");
        }
        Path target = output.toAbsolutePath().normalize();
        requireAvailable(target);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] bytes =
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
            Files.write(
                    target,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return new Artifact(target, sha256(bytes));
        } catch (JacksonException exception) {
            throw new IllegalStateException("평가 report를 JSON으로 만들 수 없습니다.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("평가 report 파일을 쓸 수 없습니다: " + target, exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    record Artifact(Path path, String sha256) {}
}
