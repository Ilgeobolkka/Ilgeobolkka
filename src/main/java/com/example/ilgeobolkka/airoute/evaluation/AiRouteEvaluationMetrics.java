package com.example.ilgeobolkka.airoute.evaluation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Q01 결과와 지정 검수자의 판정을 출시 정본 수식으로 계산한 지표다. */
public record AiRouteEvaluationMetrics(
        int totalCases,
        int coveredRequiredConcepts,
        int totalRequiredConcepts,
        double requiredConceptCoverageRate,
        int irrelevantOrDuplicatePages,
        int totalRecommendedPages,
        double irrelevantOrDuplicatePageRate,
        int prerequisiteViolations,
        int totalPrerequisites,
        double prerequisiteViolationRate,
        int usefulRoutes,
        double usefulRouteRate,
        long p95DurationNanos,
        int overTwentySecondsCases,
        boolean passed) {

    private static final long TEN_SECONDS_NANOS = Duration.ofSeconds(10).toNanos();
    private static final long TWENTY_SECONDS_NANOS = Duration.ofSeconds(20).toNanos();

    public AiRouteEvaluationMetrics {
        if (totalCases <= 0
                || totalRequiredConcepts <= 0
                || totalRecommendedPages <= 0
                || totalPrerequisites <= 0
                || coveredRequiredConcepts < 0
                || coveredRequiredConcepts > totalRequiredConcepts
                || irrelevantOrDuplicatePages < 0
                || irrelevantOrDuplicatePages > totalRecommendedPages
                || prerequisiteViolations < 0
                || prerequisiteViolations > totalPrerequisites
                || usefulRoutes < 0
                || usefulRoutes > totalCases
                || p95DurationNanos < 0
                || overTwentySecondsCases < 0
                || overTwentySecondsCases > totalCases) {
            throw new IllegalArgumentException("평가 지표의 분자·분모·시간 값이 유효하지 않습니다.");
        }
        if (Double.compare(
                                requiredConceptCoverageRate,
                                ratio(coveredRequiredConcepts, totalRequiredConcepts))
                        != 0
                || Double.compare(
                                irrelevantOrDuplicatePageRate,
                                ratio(irrelevantOrDuplicatePages, totalRecommendedPages))
                        != 0
                || Double.compare(
                                prerequisiteViolationRate,
                                ratio(prerequisiteViolations, totalPrerequisites))
                        != 0
                || Double.compare(usefulRouteRate, ratio(usefulRoutes, totalCases)) != 0) {
            throw new IllegalArgumentException("평가 지표의 비율이 분자·분모와 다릅니다.");
        }
        boolean expectedPass = atLeastPercent(coveredRequiredConcepts, totalRequiredConcepts, 80)
                && atMostPercent(irrelevantOrDuplicatePages, totalRecommendedPages, 20)
                && atMostPercent(prerequisiteViolations, totalPrerequisites, 5)
                && atLeastPercent(usefulRoutes, totalCases, 80)
                && p95DurationNanos <= TEN_SECONDS_NANOS
                && overTwentySecondsCases == 0;
        if (passed != expectedPass) {
            throw new IllegalArgumentException("평가 지표의 통과 여부가 정본 임계값과 다릅니다.");
        }
    }

    public static AiRouteEvaluationMetrics calculate(
            AiRouteEvaluationResult result, List<AiRouteHumanJudgment> judgments) {
        requireSuccessfulResult(result);
        consistentVersions(result);
        Map<String, Boolean> usefulnessByCase = validateJudgments(result, judgments);

        int coveredRequiredConcepts = 0;
        int totalRequiredConcepts = 0;
        int irrelevantOrDuplicatePages = 0;
        int totalRecommendedPages = 0;
        int prerequisiteViolations = 0;
        int totalPrerequisites = 0;
        int usefulRoutes = 0;
        int overTwentySecondsCases = 0;
        List<Long> durations = new ArrayList<>();

        for (AiRouteEvaluationResult.CompletedCase completed : result.completedCases()) {
            CaseMetrics caseMetrics = caseMetrics(completed);
            coveredRequiredConcepts += caseMetrics.coveredRequiredConcepts();
            totalRequiredConcepts += caseMetrics.totalRequiredConcepts();
            irrelevantOrDuplicatePages += caseMetrics.irrelevantOrDuplicatePages();
            totalRecommendedPages += caseMetrics.totalRecommendedPages();
            prerequisiteViolations += caseMetrics.prerequisiteViolations();
            totalPrerequisites += caseMetrics.totalPrerequisites();
            if (Boolean.TRUE.equals(usefulnessByCase.get(completed.caseId()))) {
                usefulRoutes++;
            }
            if (completed.durationNanos() > TWENTY_SECONDS_NANOS) {
                overTwentySecondsCases++;
            }
            durations.add(completed.durationNanos());
        }

        requirePositiveDenominator(totalRequiredConcepts, "필수 개념");
        requirePositiveDenominator(totalRecommendedPages, "추천 페이지");
        requirePositiveDenominator(totalPrerequisites, "선수 관계");

        durations.sort(Long::compareTo);
        int p95Index = Math.toIntExact((95L * durations.size() + 99L) / 100L) - 1;
        long p95DurationNanos = durations.get(p95Index);
        int totalCases = result.completedCases().size();
        boolean passed = atLeastPercent(coveredRequiredConcepts, totalRequiredConcepts, 80)
                && atMostPercent(irrelevantOrDuplicatePages, totalRecommendedPages, 20)
                && atMostPercent(prerequisiteViolations, totalPrerequisites, 5)
                && atLeastPercent(usefulRoutes, totalCases, 80)
                && p95DurationNanos <= TEN_SECONDS_NANOS
                && overTwentySecondsCases == 0;

        return new AiRouteEvaluationMetrics(
                totalCases,
                coveredRequiredConcepts,
                totalRequiredConcepts,
                ratio(coveredRequiredConcepts, totalRequiredConcepts),
                irrelevantOrDuplicatePages,
                totalRecommendedPages,
                ratio(irrelevantOrDuplicatePages, totalRecommendedPages),
                prerequisiteViolations,
                totalPrerequisites,
                ratio(prerequisiteViolations, totalPrerequisites),
                usefulRoutes,
                ratio(usefulRoutes, totalCases),
                p95DurationNanos,
                overTwentySecondsCases,
                passed);
    }

    static AiRouteEvaluationResult.Versions consistentVersions(AiRouteEvaluationResult result) {
        requireSuccessfulResult(result);
        AiRouteEvaluationResult.Versions expected =
                result.completedCases().getFirst().versions();
        for (AiRouteEvaluationResult.CompletedCase completed : result.completedCases()) {
            if (!expected.equals(completed.versions())) {
                throw new IllegalArgumentException(
                        "한 평가 결과에 서로 다른 모델·정책 버전이 섞여 있습니다: "
                                + completed.caseId());
            }
        }
        return expected;
    }

    private static void requireSuccessfulResult(AiRouteEvaluationResult result) {
        if (result == null || !result.successful()) {
            throw new IllegalArgumentException("모든 case가 ROUTE인 성공 평가 결과가 필요합니다.");
        }
        if (result.completedCases().isEmpty()) {
            throw new IllegalArgumentException("평가 완료 case가 필요합니다.");
        }
        Set<String> caseIds = new HashSet<>();
        Set<Long> bookIds = new HashSet<>();
        for (AiRouteEvaluationResult.CompletedCase completed : result.completedCases()) {
            if (!caseIds.add(completed.caseId())) {
                throw new IllegalArgumentException(
                        "평가 결과의 caseId가 중복됩니다: " + completed.caseId());
            }
            if (!bookIds.add(completed.bookId())) {
                throw new IllegalArgumentException(
                        "평가 결과의 bookId가 중복됩니다: " + completed.bookId());
            }
        }
    }

    private static Map<String, Boolean> validateJudgments(
            AiRouteEvaluationResult result, List<AiRouteHumanJudgment> judgments) {
        if (judgments == null) {
            throw new IllegalArgumentException("사람 유용성 판정이 필요합니다.");
        }
        Set<String> expectedCaseIds = new HashSet<>();
        result.completedCases()
                .forEach(completed -> expectedCaseIds.add(completed.caseId()));
        Map<String, Boolean> usefulnessByCase = new HashMap<>();
        for (AiRouteHumanJudgment judgment : judgments) {
            if (judgment == null) {
                throw new IllegalArgumentException("사람 유용성 판정에 null이 있습니다.");
            }
            if (usefulnessByCase.putIfAbsent(judgment.caseId(), judgment.useful()) != null) {
                throw new IllegalArgumentException(
                        "사람 유용성 판정의 caseId가 중복됩니다: " + judgment.caseId());
            }
            if (!expectedCaseIds.contains(judgment.caseId())) {
                throw new IllegalArgumentException(
                        "알 수 없는 caseId의 사람 유용성 판정입니다: " + judgment.caseId());
            }
        }
        Set<String> missing = new HashSet<>(expectedCaseIds);
        missing.removeAll(usefulnessByCase.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("사람 유용성 판정이 누락됐습니다: " + missing);
        }
        return Map.copyOf(usefulnessByCase);
    }

    private static CaseMetrics caseMetrics(AiRouteEvaluationResult.CompletedCase completed) {
        Map<Integer, Integer> routeIndexByPage = new HashMap<>();
        Set<String> routeConcepts = new HashSet<>();
        List<Integer> routePageNumbers = new ArrayList<>();
        for (int index = 0; index < completed.routePages().size(); index++) {
            AiRouteEvaluationResult.RoutePage page = completed.routePages().get(index);
            if (routeIndexByPage.putIfAbsent(page.pageNumber(), index) != null) {
                throw new IllegalArgumentException(
                        "평가 경로의 pageNumber가 중복됩니다: caseId=%s, pageNumber=%d"
                                .formatted(completed.caseId(), page.pageNumber()));
            }
            routePageNumbers.add(page.pageNumber());
            routeConcepts.addAll(page.primaryConcepts());
        }

        AiRouteEvaluationResult.Comparison comparison = completed.comparison();
        int coveredRequiredConcepts = (int) comparison.requiredConcepts().stream()
                .filter(routeConcepts::contains)
                .count();
        Set<Integer> irrelevantOrDuplicate = new LinkedHashSet<>();
        for (Integer pageNumber : routePageNumbers) {
            if (comparison.irrelevantPageNumbers().contains(pageNumber)) {
                irrelevantOrDuplicate.add(pageNumber);
            }
        }
        for (List<Integer> duplicateGroup : comparison.duplicatePageGroups()) {
            boolean firstSelected = true;
            Set<Integer> group = Set.copyOf(duplicateGroup);
            for (Integer pageNumber : routePageNumbers) {
                if (!group.contains(pageNumber)) {
                    continue;
                }
                if (firstSelected) {
                    firstSelected = false;
                } else {
                    irrelevantOrDuplicate.add(pageNumber);
                }
            }
        }

        int prerequisiteViolations = 0;
        for (AiRouteEvaluationResult.Prerequisite prerequisite :
                comparison.requiredPrerequisites()) {
            Integer afterIndex = routeIndexByPage.get(prerequisite.afterPageNumber());
            if (afterIndex == null) {
                continue;
            }
            Integer beforeIndex = routeIndexByPage.get(prerequisite.beforePageNumber());
            if (beforeIndex == null || beforeIndex > afterIndex) {
                prerequisiteViolations++;
            }
        }
        return new CaseMetrics(
                coveredRequiredConcepts,
                comparison.requiredConcepts().size(),
                irrelevantOrDuplicate.size(),
                completed.routePages().size(),
                prerequisiteViolations,
                comparison.requiredPrerequisites().size());
    }

    private static void requirePositiveDenominator(int denominator, String name) {
        if (denominator <= 0) {
            throw new IllegalArgumentException(name + " 지표의 분모는 1 이상이어야 합니다.");
        }
    }

    private static boolean atLeastPercent(int numerator, int denominator, int percent) {
        return numerator * 100L >= denominator * (long) percent;
    }

    private static boolean atMostPercent(int numerator, int denominator, int percent) {
        return numerator * 100L <= denominator * (long) percent;
    }

    private static double ratio(int numerator, int denominator) {
        return numerator / (double) denominator;
    }

    private record CaseMetrics(
            int coveredRequiredConcepts,
            int totalRequiredConcepts,
            int irrelevantOrDuplicatePages,
            int totalRecommendedPages,
            int prerequisiteViolations,
            int totalPrerequisites) {}
}
