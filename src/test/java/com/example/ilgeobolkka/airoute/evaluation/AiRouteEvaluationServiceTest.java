package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteEngineResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationEngine;
import com.example.ilgeobolkka.airoute.service.generation.GenerationTimeBudget;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class AiRouteEvaluationServiceTest {

    private AiRouteGenerationEngine engine;
    private AiRouteEvaluationPageReader pageReader;

    @BeforeEach
    void setUp() {
        engine = mock(AiRouteGenerationEngine.class);
        pageReader = mock(AiRouteEvaluationPageReader.class);
        when(pageReader.activeRentalPageIds(any(Long.class), any())).thenReturn(Set.of());
    }

    @Test
    void 엔진에는_정규화된_목적과_권한_사본만_전달하고_결과에는_정답_비교_자료를_합친다()
            throws Exception {
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenReturn(routeResult(101L, 1));
        AiRouteEvaluationService service = service(new SequenceTicker(10, 20, 100, 160));

        AiRouteEvaluationResult result = service.evaluate(plan(evaluationCase(1, "  핵심\n 개념  ")));

        ArgumentCaptor<AiRouteGenerationCommand> commandCaptor =
                ArgumentCaptor.forClass(AiRouteGenerationCommand.class);
        ArgumentCaptor<AiRouteEntitlementSnapshot> entitlementCaptor =
                ArgumentCaptor.forClass(AiRouteEntitlementSnapshot.class);
        verify(engine).generateMeasured(
                commandCaptor.capture(),
                entitlementCaptor.capture(),
                any(AiRouteGenerationEngine.StageTimer.class));
        assertThat(commandCaptor.getValue().normalizedPurpose()).isEqualTo("핵심 개념");
        assertThat(commandCaptor.getValue().maxAdditionalInk()).isEqualTo(5);
        assertThat(entitlementCaptor.getValue().inkBalance()).isEqualTo(5);
        assertThat(result.successful()).isTrue();
        assertThat(result.completedCases().getFirst().durationNanos()).isEqualTo(70);
        assertThat(result.completedCases().getFirst().routePages().getFirst().primaryConcepts())
                .containsExactly("개념");
        assertThat(result.completedCases().getFirst().comparison().requiredConcepts())
                .containsExactly("개념");
        AiRouteEvaluationResult.Versions versions =
                result.completedCases().getFirst().versions();
        assertThat(versions.embeddingModel()).isEqualTo("embedding-v1");
        assertThat(versions.routeModel()).isEqualTo("route-v1");
        assertThat(versions.candidatePolicyVersion()).isEqualTo("air-candidate-v1");
        assertThat(versions.promptVersion()).isEqualTo("prompt-v1");
        assertThat(versions.schemaVersion()).isEqualTo("schema-v1");
        assertThat(result.candidateThresholdReview().selectedThreshold()).isEqualTo(0.45);
        assertThat(result.candidateThresholdReview().thresholds())
                .allSatisfy(threshold -> assertThat(threshold.recall()).isEqualTo(1.0));
        assertThat(new ObjectMapper().writeValueAsString(result))
                .contains("candidateThresholdReview", "selectedThreshold", "thresholds");
    }

    @Test
    void 모든_case의_권한_입력을_준비하기_전에_엔진을_호출하지_않는다() {
        when(pageReader.activeRentalPageIds(any(Long.class), any()))
                .thenReturn(Set.of())
                .thenThrow(new IllegalArgumentException("두 번째 case의 대여 페이지 오류"));
        AiRouteEvaluationService service = service(new SequenceTicker(10, 20, 30));

        assertThatThrownBy(() -> service.evaluate(plan(
                        evaluationCase(1, "첫 목적"), evaluationCase(2, "둘째 목적"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("두 번째 case");
        verify(engine, never()).generateMeasured(
                any(AiRouteGenerationCommand.class),
                any(AiRouteEntitlementSnapshot.class),
                any(AiRouteGenerationEngine.StageTimer.class));
    }

    @Test
    void 두_번째_case가_NO_ROUTE면_첫_case만_완료_목록에_보존하고_전체_실패한다() {
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenReturn(routeResult(101L, 1))
                .thenReturn(noRouteResult());
        AiRouteEvaluationService service =
                service(new SequenceTicker(1, 11, 20, 40, 50, 70, 80, 130));

        AiRouteEvaluationResult result = service.evaluate(plan(
                evaluationCase(1, "첫 목적"), evaluationCase(2, "둘째 목적")));

        assertThat(result.successful()).isFalse();
        assertThat(result.completedCases()).singleElement().satisfies(completed -> {
            assertThat(completed.caseId()).isEqualTo("case-1");
            assertThat(completed.durationNanos()).isEqualTo(30);
        });
        assertThat(result.failedCase().caseId()).isEqualTo("case-2");
        assertThat(result.failedCase().reason())
                .isEqualTo(AiRouteEvaluationResult.FailureReason.NO_ROUTE);
        assertThat(result.failedCase().noRouteReason())
                .isEqualTo(AiRouteGenerationResult.NoRouteReason.NO_RELEVANT_PAGES);
        assertThat(result.failedCase().durationNanos()).isEqualTo(70);
        assertThat(result.failedCase().topCandidateScores())
                .containsExactly(
                        new AiRouteEvaluationResult.CandidateScore(7, 0.29),
                        new AiRouteEvaluationResult.CandidateScore(3, 0.28));
    }

    @Test
    void 후보_점수가_필수_개념을_덮지_않으면_해당_case만_실패로_남기고_결과를_돌려준다() {
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenReturn(routeResult(101L, 1))
                .thenReturn(routeResult(201L, 1));
        AiRouteEvaluationService service =
                service(new SequenceTicker(1, 11, 20, 40, 50, 70, 80, 130));

        AiRouteEvaluationResult result = service.evaluate(plan(
                evaluationCase(1, "첫 목적"), uncoveredConceptCase(2, "둘째 목적")));

        assertThat(result.successful()).isFalse();
        assertThat(result.completedCases()).singleElement().satisfies(completed ->
                assertThat(completed.caseId()).isEqualTo("case-1"));
        assertThat(result.failedCase().caseId()).isEqualTo("case-2");
        assertThat(result.failedCase().reason())
                .isEqualTo(AiRouteEvaluationResult.FailureReason.EXECUTION);
        assertThat(result.failedCase().durationNanos()).isEqualTo(70);
        assertThat(result.candidateThresholdReview()).isNull();
    }

    @Test
    void provider_실패와_timeout을_구분하고_완료_case를_보존한다() {
        GenerationTimeBudget.TimeLimitExceededException timeout =
                mock(GenerationTimeBudget.TimeLimitExceededException.class);
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenReturn(routeResult(101L, 1))
                .thenThrow(new OpenAiRouteException(OpenAiRouteException.Failure.TEMPORARY));
        AiRouteEvaluationResult providerFailure =
                service(new SequenceTicker(1, 11, 20, 30, 40, 50, 60, 70))
                .evaluate(plan(evaluationCase(1, "첫 목적"), evaluationCase(2, "둘째 목적")));

        assertThat(providerFailure.completedCases()).hasSize(1);
        assertThat(providerFailure.failedCase().reason())
                .isEqualTo(AiRouteEvaluationResult.FailureReason.PROVIDER);

        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenThrow(timeout);
        AiRouteEvaluationResult timeoutFailure = service(new SequenceTicker(10, 20, 50, 80))
                .evaluate(plan(evaluationCase(1, "첫 목적")));

        assertThat(timeoutFailure.completedCases()).isEmpty();
        assertThat(timeoutFailure.failedCase().reason())
                .isEqualTo(AiRouteEvaluationResult.FailureReason.TIMEOUT);
    }

    @Test
    void timeout이어도_완료한_구간별_처리_시간을_실패_artifact에_남긴다() {
        GenerationTimeBudget.TimeLimitExceededException timeout =
                mock(GenerationTimeBudget.TimeLimitExceededException.class);
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenAnswer(invocation -> {
                    AiRouteGenerationEngine.StageTimer stageTimer = invocation.getArgument(2);
                    stageTimer.measure(
                            AiRouteGenerationEngine.Stage.CONTENT_PREPARATION, () -> null);
                    stageTimer.measure(
                            AiRouteGenerationEngine.Stage.PURPOSE_EMBEDDING, () -> null);
                    return stageTimer.measure(
                            AiRouteGenerationEngine.Stage.ROUTE_RESPONSE, () -> {
                                throw timeout;
                            });
                });
        AiRouteEvaluationService service =
                service(new SequenceTicker(
                        10, 20, 100, 110, 140, 150, 200, 210, 290, 310));

        AiRouteEvaluationResult result =
                service.evaluate(plan(evaluationCase(1, "목적")));

        assertThat(result.failedCase().reason())
                .isEqualTo(AiRouteEvaluationResult.FailureReason.TIMEOUT);
        assertThat(result.failedCase().durationNanos()).isEqualTo(220);
        assertThat(result.failedCase().stageDurations())
                .isEqualTo(new AiRouteEvaluationResult.StageDurations(
                        10,
                        30,
                        50,
                        0,
                        80,
                        0,
                        0));
    }

    @Test
    void invalid_output은_안전한_검증_실패_code를_artifact에_남긴다() {
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenThrow(AiRouteInvalidOutputException.retryContractChanged());
        AiRouteEvaluationService service = service(new SequenceTicker(10, 20, 50, 80));

        AiRouteEvaluationResult result =
                service.evaluate(plan(evaluationCase(1, "목적")));

        assertThat(result.failedCase().reason())
                .isEqualTo(AiRouteEvaluationResult.FailureReason.INVALID_OUTPUT);
        assertThat(result.failedCase().detailCode()).isEqualTo("RETRY_CONTRACT_CHANGED");
    }

    @Test
    void 전달된_계획_순서와_무관하게_bookId_오름차순으로_엔진을_호출한다() {
        List<Long> calledBookIds = new java.util.ArrayList<>();
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenAnswer(invocation -> {
                    AiRouteGenerationCommand command = invocation.getArgument(0);
                    calledBookIds.add(command.bookId());
                    return routeResult(command.bookId() * 100 + 1, 1);
                });
        AiRouteEvaluationService service = service(new SequenceTicker(
                1, 2, 3, 4, 5, 6,
                10, 20, 30, 40, 50, 60));

        AiRouteEvaluationResult result = service.evaluate(plan(
                evaluationCase(3, "셋째"),
                evaluationCase(1, "첫째"),
                evaluationCase(2, "둘째")));

        assertThat(result.successful()).isTrue();
        assertThat(calledBookIds).containsExactly(1L, 2L, 3L);
        assertThat(result.completedCases().stream()
                        .map(AiRouteEvaluationResult.CompletedCase::bookId)
                        .collect(Collectors.toList()))
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void monotonic_clock이_역행하면_평가를_거부한다() {
        when(engine.generateMeasured(
                        any(AiRouteGenerationCommand.class),
                        any(AiRouteEntitlementSnapshot.class),
                        any(AiRouteGenerationEngine.StageTimer.class)))
                .thenReturn(routeResult(101L, 1));
        AiRouteEvaluationService service = service(new SequenceTicker(100, 99));

        assertThatThrownBy(() -> service.evaluate(plan(evaluationCase(1, "목적"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("monotonic clock이 역행");
    }

    private AiRouteEvaluationService service(AiRouteEvaluationTicker ticker) {
        return new AiRouteEvaluationService(
                engine,
                pageReader,
                ticker,
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC));
    }

    private AiRouteEvaluationPlan plan(AiRouteEvaluationPlan.Case... cases) {
        return new AiRouteEvaluationPlan(
                "ai-route-v2",
                "a".repeat(64),
                "manifest-revision",
                "evaluation-revision",
                List.of(cases));
    }

    private AiRouteEvaluationPlan.Case evaluationCase(long bookId, String purpose) {
        return new AiRouteEvaluationPlan.Case(
                new AiRouteEvaluationPlan.Input(
                        "case-" + bookId,
                        bookId,
                        purpose,
                        false,
                        5,
                        null,
                        List.of()),
                new AiRouteEvaluationPlan.Reference(
                        List.of("개념"),
                        List.of("도움"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(1),
                        List.of(),
                        Map.of(1, List.of("개념"))));
    }

    /** manifest는 후보로 표시했지만 DB 후보 페이지에는 필수 개념이 없는 불일치 case다. */
    private AiRouteEvaluationPlan.Case uncoveredConceptCase(long bookId, String purpose) {
        AiRouteEvaluationPlan.Case covered = evaluationCase(bookId, purpose);
        AiRouteEvaluationPlan.Reference reference = covered.reference();
        return new AiRouteEvaluationPlan.Case(
                covered.input(),
                new AiRouteEvaluationPlan.Reference(
                        reference.requiredConcepts(),
                        reference.helpfulConcepts(),
                        reference.requiredPrerequisites(),
                        reference.irrelevantPageNumbers(),
                        reference.duplicatePageGroups(),
                        reference.referencePageNumbers(),
                        reference.allowedAlternativePageNumbers(),
                        Map.of(1, List.of("다른 개념"))));
    }

    private AiRouteEngineResult routeResult(long pageId, int pageNumber) {
        AiRouteGenerationResult.Item item = new AiRouteGenerationResult.Item(
                pageId,
                pageNumber,
                1,
                AiRouteItemRelevance.HIGH,
                false,
                AiRouteItemRole.CORE,
                1,
                "공개 가이드",
                AiRouteAdditionalCostStatus.ONE_INK);
        return new AiRouteEngineResult(
                AiRouteGenerationResult.route(List.of(item)),
                "embedding-v1",
                "route-v1",
                "air-candidate-v1",
                "prompt-v1",
                "schema-v1",
                List.of(new AiRouteEngineResult.CandidateScore(pageNumber, 0.60)));
    }

    private AiRouteEngineResult noRouteResult() {
        return new AiRouteEngineResult(
                AiRouteGenerationResult.noRelevantPages(),
                "embedding-v1",
                "route-v1",
                "air-candidate-v1",
                "prompt-v1",
                "schema-v1",
                List.of(
                        new AiRouteEngineResult.CandidateScore(7, 0.29),
                        new AiRouteEngineResult.CandidateScore(3, 0.28)));
    }

    private static final class SequenceTicker implements AiRouteEvaluationTicker {

        private final Deque<Long> values;

        private SequenceTicker(long... values) {
            this.values = new ArrayDeque<>();
            for (long value : values) {
                this.values.add(value);
            }
        }

        @Override
        public long readNanos() {
            return values.removeFirst();
        }
    }
}
