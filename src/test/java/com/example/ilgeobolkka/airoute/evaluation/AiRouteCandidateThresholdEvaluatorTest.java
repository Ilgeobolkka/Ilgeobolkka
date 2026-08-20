package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiRouteCandidateThresholdEvaluatorTest {

    private final AiRouteCandidateThresholdEvaluator evaluator =
            new AiRouteCandidateThresholdEvaluator();

    @Test
    void 재현율_95퍼센트를_만족하는_가장_높은_임계값을_고른다() {
        List<String> required = concepts(100);
        List<AiRouteCandidateThresholdEvaluator.ScoredCandidate> candidates = List.of(
                candidate(1, 0.30, required.subList(0, 94)),
                candidate(2, 0.25, required.subList(94, 95)),
                candidate(3, 0.20, required.subList(95, 100)));

        AiRouteCandidateThresholdEvaluator.Result result = evaluator.evaluate(List.of(
                new AiRouteCandidateThresholdEvaluator.CaseCandidates(required, candidates)));

        assertThat(result.selectedThreshold()).isEqualTo(0.25);
        assertThat(result.thresholds())
                .extracting(AiRouteCandidateThresholdEvaluator.ThresholdResult::recall)
                .containsExactly(1.0, 0.95, 0.94);
    }

    @Test
    void 세_임계값이_모두_미달이면_운영_기준_015를_유지한다() {
        List<String> required = concepts(100);
        AiRouteCandidateThresholdEvaluator.Result result = evaluator.evaluate(List.of(
                new AiRouteCandidateThresholdEvaluator.CaseCandidates(
                        required, List.of(candidate(1, 0.15, required)))));

        assertThat(result.selectedThreshold()).isEqualTo(0.15);
        assertThat(result.thresholds())
                .allSatisfy(threshold -> assertThat(threshold.recall()).isZero());
    }

    @Test
    void 동일_유사도는_pageNumber_오름차순으로_40개만_남긴다() {
        List<AiRouteCandidateThresholdEvaluator.ScoredCandidate> candidates = new ArrayList<>();
        for (int pageNumber = 41; pageNumber >= 1; pageNumber--) {
            candidates.add(candidate(
                    pageNumber,
                    0.30,
                    pageNumber == 41 ? List.of("필수") : List.of("다른-" + pageNumber)));
        }

        AiRouteCandidateThresholdEvaluator.Result result = evaluator.evaluate(List.of(
                new AiRouteCandidateThresholdEvaluator.CaseCandidates(
                        List.of("필수"), candidates)));

        assertThat(result.thresholds())
                .allSatisfy(threshold -> assertThat(threshold.matchedConcepts()).isZero());
    }

    @Test
    void 대소문자와_공백을_정규화하지_않고_완전_일치하지_않으면_case를_만들_때_거부한다() {
        List<AiRouteCandidateThresholdEvaluator.ScoredCandidate> candidates =
                List.of(candidate(1, 0.30, List.of(" concept ")));

        assertThatThrownBy(() -> new AiRouteCandidateThresholdEvaluator.CaseCandidates(
                        List.of("Concept"), candidates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Concept");
    }

    private List<String> concepts(int count) {
        List<String> concepts = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            concepts.add("개념-" + index);
        }
        return concepts;
    }

    private AiRouteCandidateThresholdEvaluator.ScoredCandidate candidate(
            int pageNumber, double similarity, List<String> concepts) {
        return new AiRouteCandidateThresholdEvaluator.ScoredCandidate(
                pageNumber, similarity, concepts);
    }
}
