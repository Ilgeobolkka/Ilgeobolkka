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

        if (!book.aiRouteCandidate()) {
            require(
                    book.pages().isEmpty(),
                    "book %d는 AI 경로 후보가 아니므로 pages[]가 비어 있어야 합니다.".formatted(bookId));
            return new ValidatedAiRouteContent.ValidatedBook(bookId, false, List.of(), List.of());
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
        for (AiRouteContentManifest.Page page : pages) {
            validatePage(bookId, page);
            if (page.contentRole() != AiRouteContentManifest.ContentRole.FRONT_MATTER) {
                chapters.add(page.chapter());
            }
        }
        require(
                chapters.size() >= MIN_CHAPTERS,
                "book %d 장 수 %d가 최소 %d에 미달합니다."
                        .formatted(bookId, chapters.size(), MIN_CHAPTERS));

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
                            page.pageNumber(), page.aiRouteCandidatePage(), page.aiAnalysisText()));
        }
        List<ValidatedAiRouteContent.PrerequisiteEdge> edges = new ArrayList<>();
        for (Integer pageNumber : order) {
            for (Integer before : byNumber.get(pageNumber).prerequisitePageNumbers()) {
                edges.add(new ValidatedAiRouteContent.PrerequisiteEdge(before, pageNumber));
            }
        }
        return new ValidatedAiRouteContent.ValidatedBook(bookId, true, validatedPages, edges);
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
        for (AiRouteEvaluationDataset.EvaluationCase evaluationCase : evaluation.cases()) {
            String caseId = evaluationCase.caseId();
            AiRouteContentManifest.Book book = byBookId.get(evaluationCase.bookId());
            require(
                    book != null,
                    "%s의 bookId %d가 manifest에 없습니다.".formatted(caseId, evaluationCase.bookId()));
            Set<Integer> pageNumbers = new HashSet<>();
            Set<Integer> nonCandidates = new HashSet<>();
            for (AiRouteContentManifest.Page page : book.pages()) {
                pageNumbers.add(page.pageNumber());
                if (!page.aiRouteCandidatePage()) {
                    nonCandidates.add(page.pageNumber());
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
            for (List<Integer> group : evaluationCase.duplicatePageGroups()) {
                requirePagesExist(caseId, "duplicatePageGroups", group, pageNumbers);
            }
            // 후보가 아닌 페이지는 추천될 수 없으므로 정답·대체·무관 어디에도 나올 수 없다.
            requireNoNonCandidate(caseId, "referencePageNumbers",
                    evaluationCase.referencePageNumbers(), nonCandidates);
            requireNoNonCandidate(caseId, "allowedAlternativePageNumbers",
                    evaluationCase.allowedAlternativePageNumbers(), nonCandidates);
            requireNoNonCandidate(caseId, "irrelevantPageNumbers",
                    evaluationCase.irrelevantPageNumbers(), nonCandidates);
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
