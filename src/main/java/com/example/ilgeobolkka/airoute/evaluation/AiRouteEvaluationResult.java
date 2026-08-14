package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import java.time.Instant;
import java.util.List;

/** Q02가 원시 판정 입력으로 사용하는 비민감 평가 artifact schema다. */
public record AiRouteEvaluationResult(
        Status status,
        String contentVersion,
        String manifestSha256,
        String manifestGitRevision,
        String evaluationGitRevision,
        Instant executedAt,
        List<CompletedCase> completedCases,
        AiRouteCandidateThresholdEvaluator.Result candidateThresholdReview,
        Failure failedCase) {

    public AiRouteEvaluationResult {
        if (status == null
                || contentVersion == null
                || manifestSha256 == null
                || manifestGitRevision == null
                || evaluationGitRevision == null
                || executedAt == null
                || completedCases == null) {
            throw new IllegalArgumentException("평가 결과의 상태·재현 정보·완료 case가 필요합니다.");
        }
        completedCases = List.copyOf(completedCases);
        if (status == Status.SUCCESS && failedCase != null) {
            throw new IllegalArgumentException("성공 평가에는 실패 case가 없어야 합니다.");
        }
        if (status == Status.SUCCESS && candidateThresholdReview == null) {
            throw new IllegalArgumentException("성공 평가에는 후보 임계값 검토 결과가 필요합니다.");
        }
        if (status == Status.FAILED && failedCase == null) {
            throw new IllegalArgumentException("실패 평가에는 실패 case가 필요합니다.");
        }
        if (status == Status.FAILED && candidateThresholdReview != null) {
            throw new IllegalArgumentException("실패 평가에는 후보 임계값 검토 결과가 없어야 합니다.");
        }
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        FAILED
    }

    public record CompletedCase(
            String caseId,
            long bookId,
            long durationNanos,
            List<RoutePage> routePages,
            Comparison comparison,
            Versions versions) {

        public CompletedCase {
            if (caseId == null
                    || caseId.isBlank()
                    || bookId <= 0
                    || durationNanos < 0
                    || routePages == null
                    || routePages.isEmpty()
                    || comparison == null
                    || versions == null) {
                throw new IllegalArgumentException("완료 평가 case의 식별자·시간·경로·비교 자료가 필요합니다.");
            }
            routePages = List.copyOf(routePages);
        }
    }

    public record RoutePage(int pageNumber, List<String> primaryConcepts) {

        public RoutePage {
            if (pageNumber <= 0 || primaryConcepts == null) {
                throw new IllegalArgumentException("평가 경로 페이지 번호와 개념 자료가 필요합니다.");
            }
            primaryConcepts = List.copyOf(primaryConcepts);
        }
    }

    public record Comparison(
            List<String> requiredConcepts,
            List<String> helpfulConcepts,
            List<Prerequisite> requiredPrerequisites,
            List<Integer> irrelevantPageNumbers,
            List<List<Integer>> duplicatePageGroups,
            List<Integer> referencePageNumbers,
            List<Integer> allowedAlternativePageNumbers) {

        public Comparison {
            requiredConcepts = List.copyOf(requiredConcepts);
            helpfulConcepts = List.copyOf(helpfulConcepts);
            requiredPrerequisites = List.copyOf(requiredPrerequisites);
            irrelevantPageNumbers = List.copyOf(irrelevantPageNumbers);
            duplicatePageGroups = duplicatePageGroups.stream().map(List::copyOf).toList();
            referencePageNumbers = List.copyOf(referencePageNumbers);
            allowedAlternativePageNumbers = List.copyOf(allowedAlternativePageNumbers);
        }
    }

    public record Prerequisite(int beforePageNumber, int afterPageNumber) {}

    public record Versions(
            String embeddingModel,
            String routeModel,
            String candidatePolicyVersion,
            String promptVersion,
            String schemaVersion) {

        public Versions {
            requireVersion(embeddingModel, "embedding model");
            requireVersion(routeModel, "route model");
            requireVersion(candidatePolicyVersion, "후보 정책");
            requireVersion(promptVersion, "prompt");
            requireVersion(schemaVersion, "schema");
        }

        private static void requireVersion(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 버전이 필요합니다.");
            }
        }
    }

    public record Failure(
            String caseId,
            FailureReason reason,
            AiRouteGenerationResult.NoRouteReason noRouteReason,
            long durationNanos) {

        public Failure {
            if (caseId == null || caseId.isBlank() || reason == null || durationNanos < 0) {
                throw new IllegalArgumentException("실패 평가 case의 식별자·사유·시간이 필요합니다.");
            }
            if ((reason == FailureReason.NO_ROUTE) != (noRouteReason != null)) {
                throw new IllegalArgumentException("NO_ROUTE 실패에만 경로 없음 사유가 있어야 합니다.");
            }
        }
    }

    public enum FailureReason {
        NO_ROUTE,
        TIMEOUT,
        PROVIDER,
        INVALID_OUTPUT,
        CONTENT,
        EXECUTION
    }
}
