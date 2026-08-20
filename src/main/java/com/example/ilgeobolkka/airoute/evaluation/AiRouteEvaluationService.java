package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotSupportedException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteEmbeddingException;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteEngineResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationEngine;
import com.example.ilgeobolkka.airoute.service.generation.GenerationTimeBudget;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingException;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 사용자 영속 상태 없이 평가 case를 운영 생성 Engine으로 순차 실행한다. */
@Service
@Profile("evaluation")
class AiRouteEvaluationService {

    private final AiRouteGenerationEngine generationEngine;
    private final AiRouteEvaluationPageReader pageReader;
    private final AiRouteEvaluationTicker ticker;
    private final Clock clock;
    private final AiRouteCandidateThresholdEvaluator thresholdEvaluator =
            new AiRouteCandidateThresholdEvaluator();

    AiRouteEvaluationService(
            AiRouteGenerationEngine generationEngine,
            AiRouteEvaluationPageReader pageReader,
            AiRouteEvaluationTicker ticker,
            Clock clock) {
        this.generationEngine = generationEngine;
        this.pageReader = pageReader;
        this.ticker = ticker;
        this.clock = clock;
    }

    AiRouteEvaluationResult evaluate(AiRouteEvaluationPlan plan) {
        Instant executedAt = clock.instant();
        List<PreparedCase> preparedCases = prepareAll(plan);
        List<AiRouteEvaluationResult.CompletedCase> completed = new ArrayList<>();
        List<AiRouteCandidateThresholdEvaluator.CaseCandidates> thresholdCases =
                new ArrayList<>();
        for (PreparedCase prepared : preparedCases) {
            long startedAt = ticker.readNanos();
            CaseStageTimer stageTimer = new CaseStageTimer();
            AiRouteEngineResult engineResult;
            try {
                engineResult = generationEngine.generateMeasured(
                        prepared.command(), prepared.entitlement(), stageTimer);
            } catch (RuntimeException exception) {
                return failed(
                        plan,
                        executedAt,
                        completed,
                        new AiRouteEvaluationResult.Failure(
                                prepared.caseId(),
                                failureReason(exception),
                                null,
                                prepared.preparationDurationNanos() + elapsedSince(startedAt),
                                stageTimer.result(prepared.preparationDurationNanos()),
                                List.of(),
                                failureDetailCode(exception)));
            }

            long durationNanos =
                    prepared.preparationDurationNanos() + elapsedSince(startedAt);
            try {
                if (engineResult.generation().status() == AiRouteGenerationResult.Status.NO_ROUTE) {
                    return failed(
                            plan,
                            executedAt,
                            completed,
                            new AiRouteEvaluationResult.Failure(
                                    prepared.caseId(),
                                    AiRouteEvaluationResult.FailureReason.NO_ROUTE,
                                    engineResult.generation().noRouteReason(),
                                    durationNanos,
                                    stageTimer.result(prepared.preparationDurationNanos()),
                                    topCandidateScores(engineResult)));
                }
                AiRouteEvaluationResult.CompletedCase completedCase =
                        completedCase(
                                prepared,
                                engineResult,
                                durationNanos,
                                stageTimer.result(prepared.preparationDurationNanos()));
                AiRouteCandidateThresholdEvaluator.CaseCandidates thresholdCase =
                        thresholdCase(prepared, engineResult);
                completed.add(completedCase);
                thresholdCases.add(thresholdCase);
            } catch (RuntimeException exception) {
                return failed(
                        plan,
                        executedAt,
                        completed,
                        new AiRouteEvaluationResult.Failure(
                                prepared.caseId(),
                                failureReason(exception),
                                null,
                                durationNanos,
                                stageTimer.result(prepared.preparationDurationNanos()),
                                List.of(),
                                failureDetailCode(exception)));
            }
        }
        return new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.SUCCESS,
                plan.contentVersion(),
                plan.manifestSha256(),
                plan.manifestGitRevision(),
                plan.evaluationGitRevision(),
                executedAt,
                completed,
                thresholdEvaluator.evaluate(thresholdCases),
                null);
    }

    private List<PreparedCase> prepareAll(AiRouteEvaluationPlan plan) {
        List<PreparedCase> prepared = new ArrayList<>();
        List<AiRouteEvaluationPlan.Case> orderedCases = plan.cases().stream()
                .sorted((left, right) ->
                        Long.compare(left.input().bookId(), right.input().bookId()))
                .toList();
        for (AiRouteEvaluationPlan.Case evaluationCase : orderedCases) {
            long startedAt = ticker.readNanos();
            AiRouteEvaluationPlan.Input input = evaluationCase.input();
            AiRouteGenerationCommand command;
            AiRouteEntitlementSnapshot entitlement;
            if (input.owned()) {
                command = AiRouteGenerationCommand.forOwnedDepth(
                        input.bookId(),
                        plan.contentVersion(),
                        input.purpose(),
                        AiRouteDepth.valueOf(input.depth().name()));
                entitlement = AiRouteEntitlementSnapshot.forOwned();
            } else {
                int budget = input.maxAdditionalInk();
                command = AiRouteGenerationCommand.forInkBudget(
                        input.bookId(), plan.contentVersion(), input.purpose(), budget, budget);
                entitlement = AiRouteEntitlementSnapshot.forNonOwned(
                        budget,
                        pageReader.activeRentalPageIds(
                                input.bookId(), input.activeRentalPageNumbers()));
            }
            prepared.add(new PreparedCase(
                    input.caseId(),
                    input.bookId(),
                    command,
                    entitlement,
                    evaluationCase.reference(),
                    elapsedSince(startedAt)));
        }
        return List.copyOf(prepared);
    }

    private AiRouteEvaluationResult.CompletedCase completedCase(
            PreparedCase prepared,
            AiRouteEngineResult engineResult,
            long durationNanos,
            AiRouteEvaluationResult.StageDurations stageDurations) {
        List<AiRouteEvaluationResult.RoutePage> routePages = engineResult.generation().items().stream()
                .map(item -> {
                    List<String> concepts = prepared.reference()
                            .primaryConceptsByPage()
                            .get(item.pageNumber());
                    if (concepts == null) {
                        throw new IllegalStateException(
                                "생성 결과 페이지의 manifest 개념 자료가 없습니다: pageNumber="
                                        + item.pageNumber());
                    }
                    return new AiRouteEvaluationResult.RoutePage(item.pageNumber(), concepts);
                })
                .toList();
        AiRouteEvaluationPlan.Reference reference = prepared.reference();
        AiRouteEvaluationResult.Comparison comparison = new AiRouteEvaluationResult.Comparison(
                reference.requiredConcepts(),
                reference.helpfulConcepts(),
                reference.requiredPrerequisites().stream()
                        .map(edge -> new AiRouteEvaluationResult.Prerequisite(
                                edge.beforePageNumber(), edge.afterPageNumber()))
                        .toList(),
                reference.irrelevantPageNumbers(),
                reference.duplicatePageGroups(),
                reference.referencePageNumbers(),
                reference.allowedAlternativePageNumbers());
        AiRouteEvaluationResult.Versions versions = new AiRouteEvaluationResult.Versions(
                engineResult.embeddingModel(),
                engineResult.routeModel(),
                engineResult.candidatePolicyVersion(),
                engineResult.promptVersion(),
                engineResult.schemaVersion());
        return new AiRouteEvaluationResult.CompletedCase(
                prepared.caseId(),
                prepared.bookId(),
                durationNanos,
                stageDurations,
                routePages,
                comparison,
                versions);
    }

    private AiRouteCandidateThresholdEvaluator.CaseCandidates thresholdCase(
            PreparedCase prepared, AiRouteEngineResult engineResult) {
        List<AiRouteCandidateThresholdEvaluator.ScoredCandidate> candidates =
                engineResult.candidateScores().stream()
                        .map(candidate -> {
                            List<String> concepts = prepared.reference()
                                    .primaryConceptsByPage()
                                    .get(candidate.pageNumber());
                            if (concepts == null) {
                                throw new IllegalStateException(
                                        "후보 점수 페이지의 manifest 개념 자료가 없습니다: pageNumber="
                                                + candidate.pageNumber());
                            }
                            return new AiRouteCandidateThresholdEvaluator.ScoredCandidate(
                                    candidate.pageNumber(), candidate.similarity(), concepts);
                        })
                        .toList();
        return new AiRouteCandidateThresholdEvaluator.CaseCandidates(
                prepared.reference().requiredConcepts(), candidates);
    }

    private List<AiRouteEvaluationResult.CandidateScore> topCandidateScores(
            AiRouteEngineResult engineResult) {
        return engineResult.candidateScores().stream()
                .limit(5)
                .map(candidate -> new AiRouteEvaluationResult.CandidateScore(
                        candidate.pageNumber(), candidate.similarity()))
                .toList();
    }

    private AiRouteEvaluationResult failed(
            AiRouteEvaluationPlan plan,
            Instant executedAt,
            List<AiRouteEvaluationResult.CompletedCase> completed,
            AiRouteEvaluationResult.Failure failure) {
        return new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.FAILED,
                plan.contentVersion(),
                plan.manifestSha256(),
                plan.manifestGitRevision(),
                plan.evaluationGitRevision(),
                executedAt,
                completed,
                null,
                failure);
    }

    private AiRouteEvaluationResult.FailureReason failureReason(RuntimeException exception) {
        if (exception instanceof GenerationTimeBudget.TimeLimitExceededException) {
            return AiRouteEvaluationResult.FailureReason.TIMEOUT;
        }
        if (exception instanceof OpenAiEmbeddingException
                || exception instanceof OpenAiRouteException
                || exception instanceof InvalidAiRouteEmbeddingException) {
            return AiRouteEvaluationResult.FailureReason.PROVIDER;
        }
        if (exception instanceof AiRouteInvalidOutputException) {
            return AiRouteEvaluationResult.FailureReason.INVALID_OUTPUT;
        }
        if (exception instanceof AiRouteNotSupportedException) {
            return AiRouteEvaluationResult.FailureReason.CONTENT;
        }
        return AiRouteEvaluationResult.FailureReason.EXECUTION;
    }

    private String failureDetailCode(RuntimeException exception) {
        if (exception instanceof AiRouteInvalidOutputException invalidOutputException) {
            return invalidOutputException.failure().name();
        }
        return null;
    }

    private long elapsedSince(long startedAt) {
        long elapsed = ticker.readNanos() - startedAt;
        if (elapsed < 0) {
            throw new IllegalStateException("평가 monotonic clock이 역행했습니다.");
        }
        return elapsed;
    }

    private final class CaseStageTimer implements AiRouteGenerationEngine.StageTimer {

        private final Map<AiRouteGenerationEngine.Stage, Long> durationNanos =
                new EnumMap<>(AiRouteGenerationEngine.Stage.class);

        @Override
        public <T> T measure(
                AiRouteGenerationEngine.Stage stage, Supplier<T> operation) {
            if (stage == null || operation == null) {
                throw new IllegalArgumentException("평가 구간과 측정 작업이 필요합니다.");
            }
            long startedAt = ticker.readNanos();
            try {
                return operation.get();
            } finally {
                durationNanos.merge(stage, elapsedSince(startedAt), Math::addExact);
            }
        }

        private AiRouteEvaluationResult.StageDurations result(
                long evaluationPreparationNanos) {
            return new AiRouteEvaluationResult.StageDurations(
                    evaluationPreparationNanos,
                    duration(AiRouteGenerationEngine.Stage.CONTENT_PREPARATION),
                    duration(AiRouteGenerationEngine.Stage.PURPOSE_EMBEDDING),
                    duration(AiRouteGenerationEngine.Stage.CANDIDATE_SELECTION),
                    duration(AiRouteGenerationEngine.Stage.ROUTE_RESPONSE),
                    duration(AiRouteGenerationEngine.Stage.OUTPUT_VALIDATION),
                    duration(AiRouteGenerationEngine.Stage.ROUTE_ASSEMBLY));
        }

        private long duration(AiRouteGenerationEngine.Stage stage) {
            return durationNanos.getOrDefault(stage, 0L);
        }
    }

    private record PreparedCase(
            String caseId,
            long bookId,
            AiRouteGenerationCommand command,
            AiRouteEntitlementSnapshot entitlement,
            AiRouteEvaluationPlan.Reference reference,
            long preparationDurationNanos) {}
}
