package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class AiRouteEvaluationRunnerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void 실패_artifact와_로그에_목적_provider_원문_API_key를_남기지_않는다(
            CapturedOutput output) throws Exception {
        String purpose = "목적-SENSITIVE-PURPOSE";
        String providerRaw = "provider-SENSITIVE-RAW";
        String apiKey = "sk-SENSITIVE-API-KEY";
        AiRouteEvaluationPlan plan = plan(purpose);
        AiRouteEvaluationReader reader = mock(AiRouteEvaluationReader.class);
        when(reader.read()).thenReturn(plan);
        AiRouteGenerationEngine engine = mock(AiRouteGenerationEngine.class);
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenThrow(new IllegalStateException(providerRaw + " " + apiKey));
        AiRouteEvaluationPageReader pageReader = mock(AiRouteEvaluationPageReader.class);
        when(pageReader.activeRentalPageIds(any(Long.class), any())).thenReturn(Set.of());
        AiRouteEvaluationService service = new AiRouteEvaluationService(
                engine,
                pageReader,
                new SequenceTicker(),
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC));
        Path artifact = tempDirectory.resolve("result.json");
        AiRouteEvaluationProperties properties = new AiRouteEvaluationProperties();
        properties.setOutput(artifact);
        AiRouteEvaluationResultWriter writer =
                new AiRouteEvaluationResultWriter(properties, new ObjectMapper());
        AiRouteEvaluationRunner runner = new AiRouteEvaluationRunner(reader, service, writer);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("case-1")
                .hasMessageNotContaining(providerRaw)
                .hasMessageNotContaining(apiKey);

        String json = Files.readString(artifact);
        assertThat(json)
                .contains("case-1", "EXECUTION")
                .doesNotContain(purpose, providerRaw, apiKey, "aiAnalysisText");
        assertThat(output.getAll())
                .doesNotContain(purpose, providerRaw, apiKey);
    }

    @Test
    void 기존_artifact는_덮어쓰지_않고_엔진도_호출하지_않는다() throws Exception {
        Path artifact = tempDirectory.resolve("existing.json");
        Files.writeString(artifact, "기존 결과");
        AiRouteEvaluationProperties properties = new AiRouteEvaluationProperties();
        properties.setOutput(artifact);
        AiRouteEvaluationResultWriter writer =
                new AiRouteEvaluationResultWriter(properties, new ObjectMapper());
        AiRouteEvaluationReader reader = mock(AiRouteEvaluationReader.class);
        AiRouteEvaluationService service = mock(AiRouteEvaluationService.class);
        AiRouteEvaluationRunner runner = new AiRouteEvaluationRunner(reader, service, writer);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .hasMessageContaining("덮어쓰지 않습니다");
        assertThat(Files.readString(artifact)).isEqualTo("기존 결과");
    }

    private AiRouteEvaluationPlan plan(String purpose) {
        AiRouteEvaluationPlan.Case evaluationCase = new AiRouteEvaluationPlan.Case(
                new AiRouteEvaluationPlan.Input(
                        "case-1", 1, purpose, false, 5, null, List.of()),
                new AiRouteEvaluationPlan.Reference(
                        List.of("개념"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(1),
                        List.of(),
                        Map.of(1, List.of("개념"))));
        return new AiRouteEvaluationPlan(
                "ai-route-v2",
                "a".repeat(64),
                "manifest-revision",
                "evaluation-revision",
                List.of(evaluationCase));
    }

    private static final class SequenceTicker implements AiRouteEvaluationTicker {

        private int call;

        @Override
        public long readNanos() {
            call++;
            return call * 10L;
        }
    }
}
