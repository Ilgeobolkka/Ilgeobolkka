package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.validation.AiRouteContentValidator;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** manifest와 evaluation의 전체 입력 계약을 검증하고 정답과 실행 입력이 분리된 계획을 만든다. */
@Component
@Profile("evaluation")
class AiRouteEvaluationReader {

    private final AiRouteEvaluationProperties properties;
    private final OpenAiProperties openAiProperties;
    private final ContentManifestParser parser;

    AiRouteEvaluationReader(
            AiRouteEvaluationProperties properties,
            OpenAiProperties openAiProperties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.openAiProperties = openAiProperties;
        this.parser = new ContentManifestParser(objectMapper);
    }

    AiRouteEvaluationPlan read() {
        requirePath(properties.manifest(), "manifest");
        requirePath(properties.evaluation(), "evaluation");
        requireNonBlank(properties.manifestGitRevision(), "manifest Git revision");
        requireNonBlank(properties.evaluationGitRevision(), "evaluation Git revision");

        byte[] manifestBytes = readBytes(properties.manifest(), "manifest");
        byte[] evaluationBytes = readBytes(properties.evaluation(), "evaluation");
        ContentManifest parsedManifest = parser.parseManifest(manifestBytes);
        if (!(parsedManifest instanceof AiRouteContentManifest manifest)) {
            throw new IllegalArgumentException("평가 manifest는 ai-route-v2여야 합니다.");
        }
        if (!Objects.equals(
                manifest.dataPolicyVersion(), openAiProperties.dataPolicyVersion())) {
            throw new IllegalArgumentException(
                    "manifest dataPolicyVersion이 환경 OPENAI_DATA_POLICY_VERSION과 다릅니다.");
        }
        AiRouteEvaluationDataset evaluation = parser.parseEvaluation(evaluationBytes);
        if (!manifest.contentVersion().equals(evaluation.contentVersion())) {
            throw new IllegalArgumentException("manifest와 evaluation의 contentVersion이 다릅니다.");
        }

        Map<Long, AiRouteContentManifest.Book> candidateBooks = candidateBooks(manifest);
        List<AiRouteEvaluationPlan.Case> cases = validateAndSplit(evaluation, candidateBooks);
        AiRouteContentValidator.validateEvaluation(manifest, evaluation);
        return new AiRouteEvaluationPlan(
                manifest.contentVersion(),
                sha256(manifestBytes),
                properties.manifestGitRevision(),
                properties.evaluationGitRevision(),
                cases);
    }

    private Map<Long, AiRouteContentManifest.Book> candidateBooks(AiRouteContentManifest manifest) {
        Map<Long, AiRouteContentManifest.Book> books = new LinkedHashMap<>();
        for (AiRouteContentManifest.Book book : manifest.books()) {
            if (book.aiRouteCandidate()) {
                books.put(book.bookId(), book);
            }
        }
        return Map.copyOf(books);
    }

    private List<AiRouteEvaluationPlan.Case> validateAndSplit(
            AiRouteEvaluationDataset evaluation,
            Map<Long, AiRouteContentManifest.Book> candidateBooks) {
        Set<Long> evaluatedBookIds = new HashSet<>();
        EnumSet<Scenario> scenarios = EnumSet.noneOf(Scenario.class);
        List<AiRouteEvaluationPlan.Case> cases = evaluation.cases().stream()
                .map(evaluationCase -> {
                    AiRouteContentManifest.Book book = candidateBooks.get(evaluationCase.bookId());
                    if (book == null) {
                        throw new IllegalArgumentException(
                                "평가 case의 bookId가 manifest 지원 도서가 아닙니다: "
                                        + evaluationCase.bookId());
                    }
                    if (!evaluatedBookIds.add(evaluationCase.bookId())) {
                        throw new IllegalArgumentException(
                                "평가 bookId가 중복됩니다: " + evaluationCase.bookId());
                    }
                    scenarios.add(Scenario.of(evaluationCase));
                    return split(evaluationCase, book);
                })
                .sorted((left, right) -> Long.compare(left.input().bookId(), right.input().bookId()))
                .toList();

        if (!evaluatedBookIds.equals(candidateBooks.keySet())) {
            Set<Long> missing = new HashSet<>(candidateBooks.keySet());
            missing.removeAll(evaluatedBookIds);
            throw new IllegalArgumentException("manifest 지원 도서의 평가 case가 누락됐습니다: " + missing);
        }
        if (!scenarios.equals(EnumSet.allOf(Scenario.class))) {
            EnumSet<Scenario> missing = EnumSet.allOf(Scenario.class);
            missing.removeAll(scenarios);
            throw new IllegalArgumentException("필수 평가 시나리오가 누락됐습니다: " + missing);
        }
        return cases;
    }

    private AiRouteEvaluationPlan.Case split(
            AiRouteEvaluationDataset.EvaluationCase evaluationCase,
            AiRouteContentManifest.Book book) {
        Map<Integer, AiRouteContentManifest.Page> candidatePages = new HashMap<>();
        Set<String> candidatePrimaryConcepts = new HashSet<>();
        Set<String> candidateConcepts = new HashSet<>();
        for (AiRouteContentManifest.Page page : book.pages()) {
            if (!page.aiRouteCandidatePage()) {
                continue;
            }
            candidatePages.put(page.pageNumber(), page);
            candidatePrimaryConcepts.addAll(page.primaryConcepts());
            candidateConcepts.addAll(page.primaryConcepts());
            candidateConcepts.addAll(page.secondaryConcepts());
        }

        requireCandidatePages(
                evaluationCase.caseId(),
                evaluationCase.activeRentalPageNumbers(),
                candidatePages,
                "activeRentalPageNumbers");
        requireCandidatePages(
                evaluationCase.caseId(),
                evaluationCase.referencePageNumbers(),
                candidatePages,
                "referencePageNumbers");
        requireCandidatePages(
                evaluationCase.caseId(),
                evaluationCase.allowedAlternativePageNumbers(),
                candidatePages,
                "allowedAlternativePageNumbers");
        requireCandidatePages(
                evaluationCase.caseId(),
                evaluationCase.irrelevantPageNumbers(),
                candidatePages,
                "irrelevantPageNumbers");
        for (List<Integer> group : evaluationCase.duplicatePageGroups()) {
            requireCandidatePages(
                    evaluationCase.caseId(), group, candidatePages, "duplicatePageGroups");
        }
        for (AiRouteEvaluationDataset.RequiredPrerequisite edge :
                evaluationCase.requiredPrerequisites()) {
            requireCandidatePages(
                    evaluationCase.caseId(),
                    List.of(edge.beforePageNumber(), edge.afterPageNumber()),
                    candidatePages,
                    "requiredPrerequisites");
        }
        requireConcepts(
                evaluationCase.caseId(),
                evaluationCase.requiredConcepts(),
                candidatePrimaryConcepts,
                "requiredConcepts");
        requireConcepts(
                evaluationCase.caseId(),
                evaluationCase.helpfulConcepts(),
                candidateConcepts,
                "helpfulConcepts");

        Map<Integer, List<String>> primaryConceptsByPage = new LinkedHashMap<>();
        candidatePages.values().stream()
                .sorted((left, right) -> Integer.compare(left.pageNumber(), right.pageNumber()))
                .forEach(page -> primaryConceptsByPage.put(page.pageNumber(), page.primaryConcepts()));
        AiRouteEvaluationPlan.Input input = new AiRouteEvaluationPlan.Input(
                evaluationCase.caseId(),
                evaluationCase.bookId(),
                evaluationCase.purpose(),
                evaluationCase.owned(),
                evaluationCase.maxAdditionalInk(),
                evaluationCase.depth(),
                evaluationCase.activeRentalPageNumbers());
        AiRouteEvaluationPlan.Reference reference = new AiRouteEvaluationPlan.Reference(
                evaluationCase.requiredConcepts(),
                evaluationCase.helpfulConcepts(),
                evaluationCase.requiredPrerequisites(),
                evaluationCase.irrelevantPageNumbers(),
                evaluationCase.duplicatePageGroups(),
                evaluationCase.referencePageNumbers(),
                evaluationCase.allowedAlternativePageNumbers(),
                primaryConceptsByPage);
        return new AiRouteEvaluationPlan.Case(input, reference);
    }

    private void requireCandidatePages(
            String caseId,
            List<Integer> pageNumbers,
            Map<Integer, AiRouteContentManifest.Page> candidatePages,
            String fieldName) {
        Set<Integer> unique = new HashSet<>();
        for (Integer pageNumber : pageNumbers) {
            if (!unique.add(pageNumber)) {
                throw new IllegalArgumentException(
                        "%s %s에 중복 페이지가 있습니다: %d"
                                .formatted(caseId, fieldName, pageNumber));
            }
            if (!candidatePages.containsKey(pageNumber)) {
                throw new IllegalArgumentException(
                        "%s %s의 페이지가 manifest 후보 페이지가 아닙니다: %d"
                                .formatted(caseId, fieldName, pageNumber));
            }
        }
    }

    private void requireConcepts(
            String caseId, List<String> expected, Set<String> actual, String fieldName) {
        for (String concept : expected) {
            if (!actual.contains(concept)) {
                throw new IllegalArgumentException(
                        "%s %s의 개념이 manifest 후보에 없습니다: %s"
                                .formatted(caseId, fieldName, concept));
            }
        }
    }

    private void requirePath(Path path, String name) {
        if (path == null) {
            throw new IllegalArgumentException("평가 " + name + " 경로가 필요합니다.");
        }
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("평가 " + name + "이 필요합니다.");
        }
    }

    private byte[] readBytes(Path path, String name) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("평가 " + name + " 파일을 읽을 수 없습니다: " + path, exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private enum Scenario {
        BUDGET_0,
        BUDGET_5,
        BUDGET_10,
        BUDGET_15,
        OWNED_QUICK,
        OWNED_BALANCED,
        OWNED_DEEP;

        static Scenario of(AiRouteEvaluationDataset.EvaluationCase evaluationCase) {
            if (evaluationCase.owned()) {
                return valueOf("OWNED_" + evaluationCase.depth().name());
            }
            return valueOf("BUDGET_" + evaluationCase.maxAdditionalInk());
        }
    }
}
