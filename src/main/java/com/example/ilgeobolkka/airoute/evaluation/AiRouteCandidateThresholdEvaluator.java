package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePolicy;
import java.util.Comparator;
import java.util.List;

/** 운영 후보 정책을 바꾸지 않고 승격 검토값 세 개의 필수 개념 재현율만 계산한다. */
public final class AiRouteCandidateThresholdEvaluator {

    public static final List<Double> REVIEW_THRESHOLDS = List.of(0.20, 0.25, 0.30);
    private static final double REQUIRED_RECALL = 0.95;

    public Result evaluate(List<CaseCandidates> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("후보 임계값 평가 case가 필요합니다.");
        }
        int totalRequiredConcepts = 0;
        for (CaseCandidates evaluationCase : cases) {
            totalRequiredConcepts += evaluationCase.requiredConcepts().size();
        }
        int denominator = totalRequiredConcepts;

        List<ThresholdResult> results = REVIEW_THRESHOLDS.stream()
                .map(threshold -> result(threshold, cases, denominator))
                .toList();
        double selected = results.stream()
                .filter(result -> result.recall() >= REQUIRED_RECALL)
                .mapToDouble(ThresholdResult::threshold)
                .max()
                .orElse(AiRouteCandidatePolicy.MINIMUM_SIMILARITY);
        return new Result(selected, results);
    }

    private ThresholdResult result(
            double threshold, List<CaseCandidates> cases, int totalRequiredConcepts) {
        int matched = 0;
        for (CaseCandidates evaluationCase : cases) {
            List<ScoredCandidate> selected = evaluationCase.candidates().stream()
                    .filter(candidate -> candidate.similarity() >= threshold)
                    .sorted(Comparator.comparingDouble(ScoredCandidate::similarity)
                            .reversed()
                            .thenComparingInt(ScoredCandidate::pageNumber))
                    .limit(AiRouteCandidatePolicy.MAXIMUM_CANDIDATES)
                    .toList();
            for (String required : evaluationCase.requiredConcepts()) {
                if (selected.stream()
                        .anyMatch(candidate -> candidate.primaryConcepts().contains(required))) {
                    matched++;
                }
            }
        }
        return new ThresholdResult(
                threshold, matched, totalRequiredConcepts, matched / (double) totalRequiredConcepts);
    }

    /**
     * case 하나의 필수 개념과 그 case에서 점수를 받은 전체 후보다.
     *
     * <p>필수 개념이 후보에 실제로 존재하는지는 case를 만들 때 확정한다. 평가 러너가 case 단위 실패로
     * 기록하고 남은 case의 artifact를 남길 수 있는 시점이 여기뿐이다.
     */
    public record CaseCandidates(
            List<String> requiredConcepts, List<ScoredCandidate> candidates) {

        public CaseCandidates {
            if (requiredConcepts == null || requiredConcepts.isEmpty() || candidates == null) {
                throw new IllegalArgumentException("후보 임계값 평가 입력과 필수 개념이 필요합니다.");
            }
            List<String> required = List.copyOf(requiredConcepts);
            List<ScoredCandidate> scored = List.copyOf(candidates);
            for (String concept : required) {
                if (scored.stream()
                        .noneMatch(candidate -> candidate.primaryConcepts().contains(concept))) {
                    throw new IllegalArgumentException(
                            "필수 개념이 후보 페이지 primaryConcepts에 없습니다: " + concept);
                }
            }
            requiredConcepts = required;
            candidates = scored;
        }
    }

    public record ScoredCandidate(
            int pageNumber, double similarity, List<String> primaryConcepts) {

        public ScoredCandidate {
            if (pageNumber <= 0
                    || !Double.isFinite(similarity)
                    || primaryConcepts == null) {
                throw new IllegalArgumentException("후보 페이지 번호·유사도·필수 개념 자료가 필요합니다.");
            }
            primaryConcepts = List.copyOf(primaryConcepts);
        }
    }

    public record ThresholdResult(
            double threshold, int matchedConcepts, int totalConcepts, double recall) {}

    public record Result(double selectedThreshold, List<ThresholdResult> thresholds) {

        public Result {
            thresholds = List.copyOf(thresholds);
        }
    }
}
