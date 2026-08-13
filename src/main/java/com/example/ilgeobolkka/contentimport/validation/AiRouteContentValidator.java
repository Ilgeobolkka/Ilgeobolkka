package com.example.ilgeobolkka.contentimport.validation;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 적재 전 콘텐츠 전체를 검증한다. 통과한 경우에만 Embeddings 호출과 DB 적재로 넘어간다.
 *
 * <p>권수는 세지 않는다. manifest에 실제로 든 도서만 계약대로 검사하므로 확장 중의 부분 집합도 완성본과
 * 같은 코드로 통과한다.
 */
public final class AiRouteContentValidator {

    // 코퍼스 도서 제작 기준
    private static final int MIN_PAGES = 48;
    private static final int MAX_PAGES = 72;
    private static final int MIN_CHAPTERS = 6;
    private static final String EXPECTED_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int EXPECTED_EMBEDDING_DIMENSIONS = 1536;

    private final PrerequisiteGraphValidator graphValidator;

    public AiRouteContentValidator(PrerequisiteGraphValidator graphValidator) {
        this.graphValidator = graphValidator;
    }

    public ValidatedAiRouteContent validate(
            AiRouteContentManifest manifest,
            AiRouteEvaluationDataset evaluation,
            Path fixtureRoot,
            String expectedDataPolicyVersion) {
        require(manifest != null, "AI 경로 manifest가 필요합니다.");
        require(evaluation != null, "AI 경로 evaluation이 필요합니다.");
        require(fixtureRoot != null, "fixture root 경로가 필요합니다.");
        require(
                expectedDataPolicyVersion != null && !expectedDataPolicyVersion.isBlank(),
                "환경 dataPolicyVersion이 필요합니다.");
        require(
                expectedDataPolicyVersion.equals(manifest.dataPolicyVersion()),
                "manifest dataPolicyVersion(%s)이 환경 설정(%s)과 다릅니다."
                        .formatted(manifest.dataPolicyVersion(), expectedDataPolicyVersion));
        require(
                EXPECTED_EMBEDDING_MODEL.equals(manifest.embeddingModel()),
                "manifest embeddingModel(%s)이 지원 모델(%s)과 다릅니다."
                        .formatted(manifest.embeddingModel(), EXPECTED_EMBEDDING_MODEL));
        require(
                manifest.embeddingDimensions() == EXPECTED_EMBEDDING_DIMENSIONS,
                "manifest embeddingDimensions(%d)가 지원 차원(%d)과 다릅니다."
                        .formatted(
                                manifest.embeddingDimensions(), EXPECTED_EMBEDDING_DIMENSIONS));

        List<ValidatedAiRouteContent.ValidatedBook> books = new ArrayList<>();
        Map<Long, AiRouteContentManifest.Book> byBookId = new HashMap<>();
        for (AiRouteContentManifest.Book book : manifest.books()) {
            byBookId.put(book.bookId(), book);
            books.add(validateBook(book, fixtureRoot));
        }
        validateEvaluation(evaluation, byBookId);

        return new ValidatedAiRouteContent(
                manifest.contentVersion(),
                manifest.dataPolicyVersion(),
                manifest.embeddingModel(),
                manifest.embeddingDimensions(),
                books);
    }

    private ValidatedAiRouteContent.ValidatedBook validateBook(
            AiRouteContentManifest.Book book, Path fixtureRoot) {
        long bookId = book.bookId();
        validatePdf(book, fixtureRoot);
        require(
                book.title() != null && !book.title().isBlank(),
                "book %d의 제목이 필요합니다.".formatted(bookId));

        if (!book.aiRouteCandidate()) {
            require(
                    book.pages().isEmpty(),
                    "book %d는 AI 경로 후보가 아니므로 pages[]가 비어 있어야 합니다.".formatted(bookId));
            return new ValidatedAiRouteContent.ValidatedBook(
                    bookId,
                    book.title(),
                    book.totalPageCount(),
                    false,
                    book.aiExternalTransferAllowed(),
                    List.of(),
                    List.of());
        }

        require(
                book.aiExternalTransferAllowed(),
                "book %d는 외부 전송 권리를 확인하지 못해 지원할 수 없습니다.".formatted(bookId));

        List<AiRouteContentManifest.Page> pages = book.pages();
        int pageCount = pages.size();
        require(
                MIN_PAGES <= pageCount && pageCount <= MAX_PAGES,
                "book %d 페이지 수 %d는 %d~%d 범위 밖입니다."
                        .formatted(bookId, pageCount, MIN_PAGES, MAX_PAGES));
        require(
                book.totalPageCount() == pageCount,
                "book %d totalPageCount(%d)가 pages 길이(%d)와 다릅니다."
                        .formatted(bookId, book.totalPageCount(), pageCount));

        Set<Integer> pageNumbers = new LinkedHashSet<>();
        for (AiRouteContentManifest.Page page : pages) {
            require(
                    pageNumbers.add(page.pageNumber()),
                    "book %d 페이지 번호 %d가 중복입니다.".formatted(bookId, page.pageNumber()));
        }
        for (int expected = 1; expected <= pageCount; expected++) {
            require(
                    pageNumbers.contains(expected),
                    "book %d 페이지 번호가 1부터 %d까지 연속이 아닙니다: %d 없음"
                            .formatted(bookId, pageCount, expected));
        }

        Set<String> chapters = new HashSet<>();
        Map<String, Set<Integer>> pagesByDuplicateGroup = new HashMap<>();
        for (AiRouteContentManifest.Page page : pages) {
            validatePage(bookId, page);
            for (String groupKey : page.duplicateGroupKeys()) {
                pagesByDuplicateGroup
                        .computeIfAbsent(groupKey, ignored -> new HashSet<>())
                        .add(page.pageNumber());
            }
            if (page.contentRole() != AiRouteContentManifest.ContentRole.FRONT_MATTER) {
                chapters.add(page.chapter());
            }
        }
        require(
                chapters.size() >= MIN_CHAPTERS,
                "book %d 장 수 %d가 최소 %d에 미달합니다."
                        .formatted(bookId, chapters.size(), MIN_CHAPTERS));
        requireDuplicateGroupsHaveMultiplePages(bookId, pagesByDuplicateGroup);

        List<Integer> order = graphValidator.topologicalOrder(bookId, pages);
        Map<Integer, AiRouteContentManifest.Page> byNumber = new HashMap<>();
        for (AiRouteContentManifest.Page page : pages) {
            byNumber.put(page.pageNumber(), page);
        }
        List<ValidatedAiRouteContent.ValidatedPage> validatedPages = new ArrayList<>();
        for (Integer pageNumber : order) {
            AiRouteContentManifest.Page page = byNumber.get(pageNumber);
            validatedPages.add(
                    new ValidatedAiRouteContent.ValidatedPage(
                            page.pageNumber(),
                            page.aiRouteCandidatePage(),
                            page.aiAnalysisText(),
                            page.aiPublicGuideTopic(),
                            page.estimatedReadingSeconds(),
                            page.duplicateGroupKeys()));
        }
        List<ValidatedAiRouteContent.PrerequisiteEdge> edges = new ArrayList<>();
        for (Integer pageNumber : order) {
            for (Integer before : byNumber.get(pageNumber).prerequisitePageNumbers()) {
                edges.add(new ValidatedAiRouteContent.PrerequisiteEdge(before, pageNumber));
            }
        }
        return new ValidatedAiRouteContent.ValidatedBook(
                bookId,
                book.title(),
                book.totalPageCount(),
                true,
                book.aiExternalTransferAllowed(),
                validatedPages,
                edges);
    }

    private void requireDuplicateGroupsHaveMultiplePages(
            long bookId, Map<String, Set<Integer>> pagesByDuplicateGroup) {
        for (Map.Entry<String, Set<Integer>> entry : pagesByDuplicateGroup.entrySet()) {
            require(
                    entry.getValue().size() >= 2,
                    "book %d duplicateGroupKeys '%s'은 2페이지 이상이어야 합니다."
                            .formatted(bookId, entry.getKey()));
        }
    }

    private void validatePage(long bookId, AiRouteContentManifest.Page page) {
        int pageNumber = page.pageNumber();
        // 후보 여부는 역할 이름이나 내용이 아니라 두 필드의 일치로만 판정한다.
        boolean frontMatter =
                page.contentRole() == AiRouteContentManifest.ContentRole.FRONT_MATTER;
        require(
                frontMatter != page.aiRouteCandidatePage(),
                "book %d p%d contentRole(%s)과 aiRouteCandidatePage(%s)가 어긋납니다. "
                                .formatted(bookId, pageNumber, page.contentRole(),
                                        page.aiRouteCandidatePage())
                        + "FRONT_MATTER만 후보에서 빠집니다.");
        require(
                !page.primaryConcepts().isEmpty(),
                "book %d p%d primaryConcepts가 비어 있습니다.".formatted(bookId, pageNumber));
        // 적재가 book_page에 그대로 저장하는 값이라 Embeddings 호출 전에 여기서 막는다.
        require(
                page.aiAnalysisText() != null && !page.aiAnalysisText().isBlank(),
                "book %d p%d의 분석 텍스트가 필요합니다.".formatted(bookId, pageNumber));
        require(
                page.aiPublicGuideTopic() != null && !page.aiPublicGuideTopic().isBlank(),
                "book %d p%d의 공개 가이드 주제가 필요합니다.".formatted(bookId, pageNumber));
        require(
                page.estimatedReadingSeconds() > 0,
                "book %d p%d의 예상 독서 시간은 양수여야 합니다.".formatted(bookId, pageNumber));
        require(
                page.duplicateGroupKeys() != null,
                "book %d p%d의 중복 그룹 목록이 필요합니다.".formatted(bookId, pageNumber));
        require(
                page.aiRouteCandidatePage() || page.duplicateGroupKeys().isEmpty(),
                "book %d p%d 후보가 아닌 페이지의 duplicateGroupKeys는 비어 있어야 합니다."
                        .formatted(bookId, pageNumber));
        require(
                sha256(page.aiAnalysisText().getBytes(StandardCharsets.UTF_8))
                        .equals(page.aiAnalysisInputSha256()),
                "book %d p%d aiAnalysisInputSha256이 분석 텍스트와 다릅니다.".formatted(bookId, pageNumber));
    }

    private void validateEvaluation(
            AiRouteEvaluationDataset evaluation, Map<Long, AiRouteContentManifest.Book> byBookId) {
        require(
                evaluation.contentVersion() != null,
                "evaluation contentVersion이 필요합니다.");
        Set<Long> evaluatedBookIds = new HashSet<>();
        for (AiRouteEvaluationDataset.EvaluationCase evaluationCase : evaluation.cases()) {
            String caseId = evaluationCase.caseId();
            AiRouteContentManifest.Book book = byBookId.get(evaluationCase.bookId());
            require(
                    book != null,
                    "%s의 bookId %d가 manifest에 없습니다.".formatted(caseId, evaluationCase.bookId()));
            require(
                    book.aiRouteCandidate(),
                    "%s의 bookId %d는 AI 경로 지원 도서가 아닙니다."
                            .formatted(caseId, evaluationCase.bookId()));
            require(
                    evaluatedBookIds.add(book.bookId()),
                    "book %d의 평가 케이스가 둘 이상입니다.".formatted(book.bookId()));
            Set<Integer> pageNumbers = new HashSet<>();
            Set<Integer> nonCandidates = new HashSet<>();
            Set<Integer> referencePageNumbers =
                    new HashSet<>(evaluationCase.referencePageNumbers());
            Set<String> candidatePrimaryConcepts = new HashSet<>();
            Set<String> candidateConcepts = new HashSet<>();
            Set<String> referencePrimaryConcepts = new HashSet<>();
            Map<Integer, Set<Integer>> prerequisitesByPage = new HashMap<>();
            Map<String, Set<Integer>> pagesByDuplicateGroup = new HashMap<>();
            for (AiRouteContentManifest.Page page : book.pages()) {
                pageNumbers.add(page.pageNumber());
                if (page.aiRouteCandidatePage()) {
                    candidatePrimaryConcepts.addAll(page.primaryConcepts());
                    candidateConcepts.addAll(page.primaryConcepts());
                    candidateConcepts.addAll(page.secondaryConcepts());
                    if (referencePageNumbers.contains(page.pageNumber())) {
                        referencePrimaryConcepts.addAll(page.primaryConcepts());
                    }
                } else {
                    nonCandidates.add(page.pageNumber());
                }
                prerequisitesByPage.put(
                        page.pageNumber(), Set.copyOf(page.prerequisitePageNumbers()));
                for (String groupKey : page.duplicateGroupKeys()) {
                    pagesByDuplicateGroup
                            .computeIfAbsent(groupKey, ignored -> new HashSet<>())
                            .add(page.pageNumber());
                }
            }
            requirePagesExist(caseId, "activeRentalPageNumbers",
                    evaluationCase.activeRentalPageNumbers(), pageNumbers);
            requirePagesExist(caseId, "irrelevantPageNumbers",
                    evaluationCase.irrelevantPageNumbers(), pageNumbers);
            requirePagesExist(caseId, "referencePageNumbers",
                    evaluationCase.referencePageNumbers(), pageNumbers);
            requirePagesExist(caseId, "allowedAlternativePageNumbers",
                    evaluationCase.allowedAlternativePageNumbers(), pageNumbers);
            Set<Integer> conflictingPages =
                    new HashSet<>(evaluationCase.referencePageNumbers());
            conflictingPages.retainAll(evaluationCase.irrelevantPageNumbers());
            require(
                    conflictingPages.isEmpty(),
                    "%s 정답과 무관 페이지가 겹칩니다: %s"
                            .formatted(caseId, conflictingPages));
            Set<Integer> conflictingAlternativePages =
                    new HashSet<>(evaluationCase.allowedAlternativePageNumbers());
            conflictingAlternativePages.retainAll(evaluationCase.irrelevantPageNumbers());
            require(
                    conflictingAlternativePages.isEmpty(),
                    "%s 대체와 무관 페이지가 겹칩니다: %s"
                            .formatted(caseId, conflictingAlternativePages));
            for (List<Integer> group : evaluationCase.duplicatePageGroups()) {
                requirePagesExist(caseId, "duplicatePageGroups", group, pageNumbers);
            }
            // 후보가 아닌 페이지는 경로 비용·추천·채점 대상이 될 수 없다.
            requireNoNonCandidate(caseId, "activeRentalPageNumbers",
                    evaluationCase.activeRentalPageNumbers(), nonCandidates);
            requireNoNonCandidate(caseId, "referencePageNumbers",
                    evaluationCase.referencePageNumbers(), nonCandidates);
            requireNoNonCandidate(caseId, "allowedAlternativePageNumbers",
                    evaluationCase.allowedAlternativePageNumbers(), nonCandidates);
            requireNoNonCandidate(caseId, "irrelevantPageNumbers",
                    evaluationCase.irrelevantPageNumbers(), nonCandidates);
            requireConceptsExist(
                    caseId,
                    "requiredConcepts",
                    evaluationCase.requiredConcepts(),
                    candidatePrimaryConcepts,
                    "후보 페이지 primaryConcepts");
            requireConceptsExist(
                    caseId,
                    "requiredConcepts",
                    evaluationCase.requiredConcepts(),
                    referencePrimaryConcepts,
                    "referencePageNumbers의 후보 페이지 primaryConcepts");
            requireConceptsExist(
                    caseId,
                    "helpfulConcepts",
                    evaluationCase.helpfulConcepts(),
                    candidateConcepts,
                    "후보 페이지 primaryConcepts 또는 secondaryConcepts");
            requirePrerequisitesMatch(
                    caseId,
                    evaluationCase.requiredPrerequisites(),
                    pageNumbers,
                    prerequisitesByPage,
                    evaluationCase.referencePageNumbers());
            requireDuplicateGroupsMatch(
                    caseId,
                    evaluationCase.duplicatePageGroups(),
                    pagesByDuplicateGroup,
                    evaluationCase.referencePageNumbers());
        }
        Set<Long> missingBookIds = new HashSet<>();
        for (AiRouteContentManifest.Book book : byBookId.values()) {
            if (book.aiRouteCandidate() && !evaluatedBookIds.contains(book.bookId())) {
                missingBookIds.add(book.bookId());
            }
        }
        require(
                missingBookIds.isEmpty(),
                "AI 경로 지원 도서의 평가 케이스가 없습니다: %s".formatted(missingBookIds));
    }

    private void requireConceptsExist(
            String caseId,
            String field,
            List<String> expected,
            Set<String> concepts,
            String conceptSource) {
        for (String concept : expected) {
            require(
                    concepts.contains(concept),
                    "%s %s의 개념 '%s'이 %s에 없습니다."
                            .formatted(caseId, field, concept, conceptSource));
        }
    }

    private void requirePrerequisitesMatch(
            String caseId,
            List<AiRouteEvaluationDataset.RequiredPrerequisite> requiredPrerequisites,
            Set<Integer> pageNumbers,
            Map<Integer, Set<Integer>> prerequisitesByPage,
            List<Integer> referencePageNumbers) {
        Set<AiRouteEvaluationDataset.RequiredPrerequisite> actual = new HashSet<>();
        for (AiRouteEvaluationDataset.RequiredPrerequisite prerequisite : requiredPrerequisites) {
            int before = prerequisite.beforePageNumber();
            int after = prerequisite.afterPageNumber();
            require(
                    pageNumbers.contains(before) && pageNumbers.contains(after),
                    "%s requiredPrerequisites의 %d -> %d 페이지가 도서에 없습니다."
                            .formatted(caseId, before, after));
            require(
                    actual.add(prerequisite),
                    "%s requiredPrerequisites에 %d -> %d가 중복됩니다."
                            .formatted(caseId, before, after));
            require(
                    prerequisitesByPage.get(after).contains(before),
                    "%s requiredPrerequisites의 %d -> %d는 실제 선수 간선이 아닙니다."
                            .formatted(caseId, before, after));
        }

        Set<Integer> referencePages = new HashSet<>(referencePageNumbers);
        Set<AiRouteEvaluationDataset.RequiredPrerequisite> expected = new HashSet<>();
        for (Integer after : referencePages) {
            for (Integer before : prerequisitesByPage.get(after)) {
                require(
                        referencePages.contains(before),
                        "%s referencePageNumbers가 선수 폐쇄가 아닙니다: %d -> %d의 선수 페이지가 없습니다."
                                .formatted(caseId, before, after));
                expected.add(new AiRouteEvaluationDataset.RequiredPrerequisite(before, after));
            }
        }
        Set<AiRouteEvaluationDataset.RequiredPrerequisite> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        Set<AiRouteEvaluationDataset.RequiredPrerequisite> unexpected = new HashSet<>(actual);
        unexpected.removeAll(expected);
        require(
                actual.equals(expected),
                "%s requiredPrerequisites가 referencePageNumbers 선수 간선 전체와 다릅니다: "
                                .formatted(caseId)
                        + "누락=%s, 초과=%s".formatted(missing, unexpected));
    }

    private void requireDuplicateGroupsMatch(
            String caseId,
            List<List<Integer>> evaluationGroups,
            Map<String, Set<Integer>> pagesByDuplicateGroup,
            List<Integer> referencePageNumbers) {
        Set<Set<Integer>> manifestGroups = new HashSet<>();
        for (Set<Integer> pages : pagesByDuplicateGroup.values()) {
            manifestGroups.add(Set.copyOf(pages));
        }

        Set<Set<Integer>> expectedGroups = new HashSet<>();
        for (List<Integer> group : evaluationGroups) {
            Set<Integer> pages = new HashSet<>(group);
            require(
                    pages.size() == group.size(),
                    "%s duplicatePageGroups 안에 같은 페이지가 중복됩니다: %s"
                            .formatted(caseId, group));
            require(
                    pages.size() >= 2,
                    "%s duplicatePageGroups는 그룹마다 2페이지 이상이어야 합니다: %s"
                            .formatted(caseId, group));
            require(
                    expectedGroups.add(Set.copyOf(pages)),
                    "%s duplicatePageGroups에 같은 그룹이 중복됩니다: %s"
                            .formatted(caseId, group));
        }
        Set<Set<Integer>> missing = new HashSet<>(manifestGroups);
        missing.removeAll(expectedGroups);
        Set<Set<Integer>> unexpected = new HashSet<>(expectedGroups);
        unexpected.removeAll(manifestGroups);
        require(
                manifestGroups.equals(expectedGroups),
                "%s duplicatePageGroups가 manifest의 duplicateGroupKeys와 다릅니다: "
                                .formatted(caseId)
                        + "누락=%s, 초과=%s".formatted(missing, unexpected));

        Set<Integer> referencePages = new HashSet<>(referencePageNumbers);
        for (Set<Integer> group : manifestGroups) {
            Set<Integer> selected = new HashSet<>(group);
            selected.retainAll(referencePages);
            require(
                    selected.size() <= 1,
                    "%s referencePageNumbers에 같은 중복 그룹 페이지가 둘 이상 있습니다: %s"
                            .formatted(caseId, selected));
        }
    }

    private void requirePagesExist(
            String caseId, String field, List<Integer> pages, Set<Integer> pageNumbers) {
        for (Integer pageNumber : pages) {
            require(
                    pageNumbers.contains(pageNumber),
                    "%s %s의 페이지 %d가 도서에 없습니다.".formatted(caseId, field, pageNumber));
        }
    }

    private void requireNoNonCandidate(
            String caseId, String field, List<Integer> pages, Set<Integer> nonCandidates) {
        for (Integer pageNumber : pages) {
            require(
                    !nonCandidates.contains(pageNumber),
                    "%s %s에 후보가 아닌 페이지 %d가 있습니다.".formatted(caseId, field, pageNumber));
        }
    }

    private void validatePdf(AiRouteContentManifest.Book book, Path fixtureRoot) {
        Path pdf = fixtureRoot.resolve(book.pdfPath());
        require(
                Files.isRegularFile(pdf),
                "book %d PDF가 없습니다: %s".formatted(book.bookId(), book.pdfPath()));
        try {
            require(
                    sha256(Files.readAllBytes(pdf)).equals(book.pdfSha256()),
                    "book %d PDF SHA-256이 manifest와 다릅니다: %s"
                            .formatted(book.bookId(), book.pdfPath()));
        } catch (IOException exception) {
            throw new AiRouteContentValidationException(
                    "book %d PDF를 읽지 못했습니다: %s".formatted(book.bookId(), book.pdfPath()));
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AiRouteContentValidationException(message);
        }
    }
}
