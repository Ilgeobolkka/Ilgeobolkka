package com.example.ilgeobolkka.airoute.evaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Q01 원시 결과에서 비공개 입력을 제외하고 출시 판정과 재현 정보만 남긴 report다. */
public record AiRouteEvaluationReport(
        String contentVersion,
        String dataPolicyVersion,
        String manifestSha256,
        String manifestGitRevision,
        String evaluationGitRevision,
        Instant executedAt,
        AiRouteEvaluationResult.Versions versions,
        AiRouteCandidateThresholdEvaluator.Result candidateThresholdReview,
        AiRouteEvaluationMetrics metrics,
        List<CaseJudgment> humanJudgments) {

    public AiRouteEvaluationReport {
        requireValue(contentVersion, "contentVersion");
        requireValue(dataPolicyVersion, "dataPolicyVersion");
        requireValue(manifestSha256, "manifest SHA-256");
        requireValue(manifestGitRevision, "manifest Git revision");
        requireValue(evaluationGitRevision, "evaluation Git revision");
        if (executedAt == null
                || versions == null
                || candidateThresholdReview == null
                || metrics == null
                || humanJudgments == null
                || humanJudgments.isEmpty()) {
            throw new IllegalArgumentException("report의 실행 시각·버전·지표·사람 판정이 필요합니다.");
        }
        humanJudgments = List.copyOf(humanJudgments);
        Set<String> caseIds = new HashSet<>();
        Set<Long> bookIds = new HashSet<>();
        for (CaseJudgment judgment : humanJudgments) {
            if (judgment == null || !caseIds.add(judgment.caseId())) {
                throw new IllegalArgumentException("report의 사람 판정 caseId가 중복되거나 비어 있습니다.");
            }
            if (!bookIds.add(judgment.bookId())) {
                throw new IllegalArgumentException(
                        "report의 사람 판정 bookId가 중복됩니다: " + judgment.bookId());
            }
        }
        if (metrics.totalCases() != humanJudgments.size()) {
            throw new IllegalArgumentException("report 지표와 사람 판정의 case 수가 다릅니다.");
        }
        long usefulJudgments = humanJudgments.stream().filter(CaseJudgment::useful).count();
        if (metrics.usefulRoutes() != usefulJudgments) {
            throw new IllegalArgumentException("report 지표와 사람 유용성 판정 수가 다릅니다.");
        }
    }

    public static AiRouteEvaluationReport create(
            AiRouteEvaluationResult result,
            List<AiRouteHumanJudgment> judgments,
            String dataPolicyVersion) {
        AiRouteEvaluationMetrics metrics = AiRouteEvaluationMetrics.calculate(result, judgments);
        Map<String, Boolean> usefulnessByCase = new HashMap<>();
        judgments.forEach(judgment -> usefulnessByCase.put(judgment.caseId(), judgment.useful()));
        List<CaseJudgment> caseJudgments = new ArrayList<>();
        for (AiRouteEvaluationResult.CompletedCase completed : result.completedCases()) {
            caseJudgments.add(new CaseJudgment(
                    completed.caseId(),
                    completed.bookId(),
                    usefulnessByCase.get(completed.caseId())));
        }
        return new AiRouteEvaluationReport(
                result.contentVersion(),
                dataPolicyVersion,
                result.manifestSha256(),
                result.manifestGitRevision(),
                result.evaluationGitRevision(),
                result.executedAt(),
                AiRouteEvaluationMetrics.consistentVersions(result),
                result.candidateThresholdReview(),
                metrics,
                caseJudgments);
    }

    /** 재사용할 수 없으면 어떤 재현 정보가 달라졌는지 담은 사유를, 재사용할 수 있으면 빈 값을 준다. */
    public Optional<String> reuseRejection(AiRouteEvaluationResult candidate) {
        if (candidate == null || !candidate.successful()) {
            return Optional.of("현재 Q01 평가 결과가 모든 case를 ROUTE로 끝내지 못했습니다.");
        }
        AiRouteEvaluationResult.Versions candidateVersions;
        try {
            candidateVersions = AiRouteEvaluationMetrics.consistentVersions(candidate);
        } catch (IllegalArgumentException exception) {
            return Optional.of(exception.getMessage());
        }
        if (!contentVersion.equals(candidate.contentVersion())) {
            return Optional.of(mismatch(
                    "contentVersion", contentVersion, candidate.contentVersion()));
        }
        if (!manifestSha256.equals(candidate.manifestSha256())) {
            return Optional.of(mismatch(
                    "manifest SHA-256", manifestSha256, candidate.manifestSha256()));
        }
        if (!evaluationGitRevision.equals(candidate.evaluationGitRevision())) {
            return Optional.of(mismatch(
                    "평가 데이터 Git revision",
                    evaluationGitRevision,
                    candidate.evaluationGitRevision()));
        }
        if (!versions.equals(candidateVersions)) {
            return Optional.of(mismatch("모델·정책 버전", versions, candidateVersions));
        }
        return Optional.empty();
    }

    private static String mismatch(String name, Object reportValue, Object candidateValue) {
        return "%s이 다릅니다: report=%s, 현재=%s".formatted(name, reportValue, candidateValue);
    }

    List<Long> targetBookIds() {
        return humanJudgments.stream().map(CaseJudgment::bookId).sorted().toList();
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("report의 " + name + "이 필요합니다.");
        }
    }

    public record CaseJudgment(String caseId, long bookId, boolean useful) {

        public CaseJudgment {
            if (caseId == null || caseId.isBlank() || bookId <= 0) {
                throw new IllegalArgumentException("report 사람 판정의 caseId와 bookId가 필요합니다.");
            }
        }
    }
}
