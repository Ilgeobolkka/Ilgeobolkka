package com.example.ilgeobolkka.infra.openai;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenAiPropertiesTest {

    private static final String API_KEY = "sk-openai-secret-test-value";

    @Test
    void 설정이_모두_있으면_세_실행_모드의_검증을_통과한다() {
        OpenAiProperties properties = completeProperties();

        assertAll(
                () -> assertDoesNotThrow(properties::validateForServer),
                () -> assertDoesNotThrow(properties::validateForContentImport),
                () -> assertDoesNotThrow(properties::validateForEvaluation),
                () -> assertEquals(URI.create("https://api.openai.com/v1"), properties.baseUrl()),
                () -> assertEquals("project-test", properties.projectId()),
                () -> assertEquals(API_KEY, properties.apiKey()),
                () -> assertEquals("policy-v1", properties.dataPolicyVersion()));
    }

    @ParameterizedTest
    @MethodSource("missingSettings")
    void 활성_일반_서버는_누락된_설정의_환경변수_이름으로_실패한다(
            OpenAiProperties properties,
            String environmentVariable) {
        assertValidationFailure(
                properties::validateForServer,
                "AI 경로 활성화에는 " + environmentVariable + " 값이 필요합니다.");
    }

    @ParameterizedTest
    @MethodSource("missingSettings")
    void content_import는_누락된_설정의_환경변수_이름으로_실패한다(
            OpenAiProperties properties,
            String environmentVariable) {
        assertValidationFailure(
                properties::validateForContentImport,
                "content-import 실행에는 " + environmentVariable + " 값이 필요합니다.");
    }

    @ParameterizedTest
    @MethodSource("missingSettings")
    void evaluation은_누락된_설정의_환경변수_이름으로_실패한다(
            OpenAiProperties properties,
            String environmentVariable) {
        assertValidationFailure(
                properties::validateForEvaluation,
                "evaluation 실행에는 " + environmentVariable + " 값이 필요합니다.");
    }

    @Test
    void toString은_API_키_원문을_노출하지_않는다() {
        String text = completeProperties().toString();

        assertAll(
                () -> assertFalse(text.contains(API_KEY)),
                () -> assertTrue(text.contains("apiKey=[REDACTED]")));
    }

    private void assertValidationFailure(Runnable validation, String expectedMessage) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, validation::run);

        assertAll(
                () -> assertEquals(expectedMessage, exception.getMessage()),
                () -> assertFalse(exception.toString().contains(API_KEY)));
    }

    private static OpenAiProperties completeProperties() {
        return new OpenAiProperties("project-test", API_KEY, "policy-v1");
    }

    private static Stream<Arguments> missingSettings() {
        return Stream.of(
                Arguments.of(
                        new OpenAiProperties("", API_KEY, "policy-v1"),
                        "OPENAI_PROJECT_ID"),
                Arguments.of(
                        new OpenAiProperties("project-test", " ", "policy-v1"),
                        "OPENAI_API_KEY"),
                Arguments.of(
                        new OpenAiProperties("project-test", API_KEY, null),
                        "OPENAI_DATA_POLICY_VERSION"));
    }
}
