package com.example.ilgeobolkka.infra.openai;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openai")
public record OpenAiProperties(
        String projectId,
        String apiKey,
        String dataPolicyVersion) {

    private static final URI BASE_URL = URI.create("https://api.openai.com/v1");

    public URI baseUrl() {
        return BASE_URL;
    }

    public void validateForServer() {
        validateRequiredValues("AI 경로 활성화");
    }

    public void validateForContentImport() {
        validateRequiredValues("content-import 실행");
    }

    public void validateForEvaluation() {
        validateRequiredValues("evaluation 실행");
    }

    private void validateRequiredValues(String executionMode) {
        requireValue(projectId, "OPENAI_PROJECT_ID", executionMode);
        requireValue(apiKey, "OPENAI_API_KEY", executionMode);
        requireValue(dataPolicyVersion, "OPENAI_DATA_POLICY_VERSION", executionMode);
    }

    private void requireValue(String value, String environmentVariable, String executionMode) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    executionMode + "에는 " + environmentVariable + " 값이 필요합니다.");
        }
    }

    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + BASE_URL
                + ", projectId=" + projectId
                + ", apiKey=[REDACTED]"
                + ", dataPolicyVersion=" + dataPolicyVersion + "]";
    }
}
