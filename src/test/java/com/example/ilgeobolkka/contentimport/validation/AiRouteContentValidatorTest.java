package com.example.ilgeobolkka.contentimport.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * C02 검증기 테스트.
 *
 * <p>이 validator는 Gateway·Repository를 주입받지 않는다. 생성자가 그래프 검증기 하나만 받으므로
 * 검증이 실패했을 때 외부 호출이나 DB 변경이 일어날 경로가 타입 수준에서 없다.
 */
class AiRouteContentValidatorTest {

    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final long BOOK_ID = 41L;

    private final AiRouteContentValidator validator =
            new AiRouteContentValidator(new PrerequisiteGraphValidator());

    @TempDir private Path fixtureRoot;

    private String pdfSha256;

    @BeforeEach
    void PDF를_둔다() throws IOException {
        Path pdfs = fixtureRoot.resolve("pdfs");
        Files.createDirectories(pdfs);
        byte[] content = "%PDF-1.4 테스트".getBytes(StandardCharsets.UTF_8);
        Files.write(pdfs.resolve("book-041.pdf"), content);
        pdfSha256 = AiRouteContentPages.sha256(content);
    }

    @Test
    void 정본_fixture가_그대로_통과한다() throws IOException {
        Path canonical = Path.of("fixtures/content/ai-route-v2");
        ContentManifestParser parser = new ContentManifestParser(new ObjectMapper());
        AiRouteContentManifest manifest =
                assertInstanceOf(
                        AiRouteContentManifest.class,
                        parser.parseManifest(
                                Files.readString(canonical.resolve("manifest.json"))));
        AiRouteEvaluationDataset evaluation =
                parser.parseEvaluation(Files.readString(canonical.resolve("evaluation.json")));

        ValidatedAiRouteContent validated =
                validator.validate(manifest, evaluation, canonical, POLICY);

        assertEquals(manifest.books().size(), validated.books().size());
        assertTrue(
                validated.books().stream()
                        .filter(ValidatedAiRouteContent.ValidatedBook::aiRouteCandidate)
                        .allMatch(book -> !book.pages().isEmpty()));
    }

    @Test
    void 도서_한_권짜리_부분_집합도_권수_때문에_실패하지_않는다() {
        ValidatedAiRouteContent validated =
                validator.validate(manifest(candidateBook()), emptyEvaluation(), fixtureRoot, POLICY);

        assertEquals(1, validated.books().size());
        assertEquals(48, validated.books().getFirst().pages().size());
    }

    @Test
    void 검증_결과의_페이지는_선수가_앞에_오는_순서다() {
        ValidatedAiRouteContent validated =
                validator.validate(manifest(candidateBook()), emptyEvaluation(), fixtureRoot, POLICY);
        ValidatedAiRouteContent.ValidatedBook book = validated.books().getFirst();

        List<Integer> order =
                book.pages().stream().map(ValidatedAiRouteContent.ValidatedPage::pageNumber).toList();
        for (ValidatedAiRouteContent.PrerequisiteEdge edge : book.prerequisiteEdges()) {
            assertTrue(
                    order.indexOf(edge.beforePageNumber()) < order.indexOf(edge.afterPageNumber()),
                    "선수 %d가 의존 %d보다 뒤에 있습니다"
                            .formatted(edge.beforePageNumber(), edge.afterPageNumber()));
        }
    }

    @Test
    void 후보가_아닌_도서는_빈_pages만_허용한다() {
        AiRouteContentManifest.Book novel =
                new AiRouteContentManifest.Book(
                        BOOK_ID, "소설", "pdfs/book-041.pdf", pdfSha256, 4, false, false, List.of());

        ValidatedAiRouteContent validated =
                validator.validate(manifest(novel), emptyEvaluation(), fixtureRoot, POLICY);
        assertTrue(validated.books().getFirst().pages().isEmpty());

        AiRouteContentManifest withPages =
                manifest(
                        new AiRouteContentManifest.Book(
                                BOOK_ID,
                                "소설",
                                "pdfs/book-041.pdf",
                                pdfSha256,
                                4,
                                false,
                                false,
                                List.of(AiRouteContentPages.frontMatter(1))));
        assertTrue(
                fail(withPages, emptyEvaluation()).getMessage().contains("pages[]가 비어 있어야"));
    }

    @Test
    void PDF가_없거나_SHA가_다르면_실패한다() {
        AiRouteContentManifest missing =
                manifest(
                        AiRouteContentPages.candidateBook(
                                BOOK_ID, "pdfs/book-999.pdf", pdfSha256));
        AiRouteContentManifest wrongSha =
                manifest(
                        AiRouteContentPages.candidateBook(
                                BOOK_ID, "pdfs/book-041.pdf", "0".repeat(64)));

        assertTrue(fail(missing, emptyEvaluation()).getMessage().contains("PDF가 없습니다"));
        assertTrue(fail(wrongSha, emptyEvaluation()).getMessage().contains("PDF SHA-256"));
    }

    @Test
    void 외부_전송_권리와_데이터_정책_불일치는_실패한다() {
        AiRouteContentManifest.Book book = candidateBook();
        AiRouteContentManifest noTransfer =
                manifest(
                        new AiRouteContentManifest.Book(
                                book.bookId(),
                                book.title(),
                                book.pdfPath(),
                                book.pdfSha256(),
                                book.totalPageCount(),
                                true,
                                false,
                                book.pages()));

        assertTrue(fail(noTransfer, emptyEvaluation()).getMessage().contains("외부 전송 권리"));
        assertTrue(
                assertThrows(
                                AiRouteContentValidationException.class,
                                () ->
                                        validator.validate(
                                                manifest(book),
                                                emptyEvaluation(),
                                                fixtureRoot,
                                                "OTHER_POLICY"))
                        .getMessage()
                        .contains("dataPolicyVersion"));
    }

    @Test
    void 역할과_후보_표시가_어긋난_페이지는_실패한다() {
        AiRouteContentManifest frontMatterCandidate =
                manifestWithReplacedPage(
                        1,
                        AiRouteContentPages.page(
                                1,
                                "앞부분",
                                AiRouteContentManifest.ContentRole.FRONT_MATTER,
                                true,
                                List.of()));
        AiRouteContentManifest coreNonCandidate =
                manifestWithReplacedPage(
                        3,
                        AiRouteContentPages.page(
                                3,
                                "1장",
                                AiRouteContentManifest.ContentRole.CORE,
                                false,
                                List.of(2)));

        assertTrue(fail(frontMatterCandidate, emptyEvaluation()).getMessage().contains("어긋납니다"));
        assertTrue(fail(coreNonCandidate, emptyEvaluation()).getMessage().contains("어긋납니다"));
    }

    @Test
    void 페이지_수_장_수_분석해시_개념_계약_위반은_실패한다() {
        List<AiRouteContentManifest.Page> pages =
                new ArrayList<>(candidateBook().pages().subList(0, 10));
        AiRouteContentManifest tooFewPages =
                manifest(
                        new AiRouteContentManifest.Book(
                                BOOK_ID, "도서", "pdfs/book-041.pdf", pdfSha256, 10, true, true, pages));
        AiRouteContentManifest wrongTotal =
                manifest(
                        new AiRouteContentManifest.Book(
                                BOOK_ID,
                                "도서",
                                "pdfs/book-041.pdf",
                                pdfSha256,
                                49,
                                true,
                                true,
                                candidateBook().pages()));
        AiRouteContentManifest brokenSha =
                manifestWithReplacedPage(
                        2,
                        new AiRouteContentManifest.Page(
                                2,
                                "1장",
                                "2절",
                                List.of("개념"),
                                List.of(),
                                AiRouteContentManifest.ContentRole.CORE,
                                true,
                                "분석 텍스트",
                                "0".repeat(64),
                                "공개 주제",
                                60,
                                List.of(),
                                List.of()));
        AiRouteContentManifest emptyConcepts =
                manifestWithReplacedPage(
                        2,
                        new AiRouteContentManifest.Page(
                                2,
                                "1장",
                                "2절",
                                List.of(),
                                List.of(),
                                AiRouteContentManifest.ContentRole.CORE,
                                true,
                                "분석",
                                AiRouteContentPages.sha256("분석".getBytes(StandardCharsets.UTF_8)),
                                "공개 주제",
                                60,
                                List.of(),
                                List.of()));

        assertTrue(fail(tooFewPages, emptyEvaluation()).getMessage().contains("범위 밖"));
        assertTrue(fail(wrongTotal, emptyEvaluation()).getMessage().contains("totalPageCount"));
        assertTrue(fail(brokenSha, emptyEvaluation()).getMessage().contains("aiAnalysisInputSha256"));
        assertTrue(fail(emptyConcepts, emptyEvaluation()).getMessage().contains("primaryConcepts"));
    }

    @Test
    void 평가가_없는_페이지나_후보가_아닌_페이지를_가리키면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());

        assertTrue(
                fail(manifest, evaluationWith(List.of(999), List.of(), List.of()))
                        .getMessage()
                        .contains("도서에 없습니다"));
        assertTrue(
                fail(manifest, evaluationWith(List.of(1), List.of(), List.of()))
                        .getMessage()
                        .contains("referencePageNumbers에 후보가 아닌 페이지"));
        assertTrue(
                fail(manifest, evaluationWith(List.of(2), List.of(1), List.of()))
                        .getMessage()
                        .contains("allowedAlternativePageNumbers에 후보가 아닌 페이지"));
        assertTrue(
                fail(manifest, evaluationWith(List.of(2), List.of(), List.of(1)))
                        .getMessage()
                        .contains("irrelevantPageNumbers에 후보가 아닌 페이지"));
    }

    private AiRouteContentValidationException fail(
            AiRouteContentManifest manifest, AiRouteEvaluationDataset evaluation) {
        return assertThrows(
                AiRouteContentValidationException.class,
                () -> validator.validate(manifest, evaluation, fixtureRoot, POLICY));
    }

    private AiRouteContentManifest.Book candidateBook() {
        return AiRouteContentPages.candidateBook(BOOK_ID, "pdfs/book-041.pdf", pdfSha256);
    }

    private AiRouteContentManifest manifestWithReplacedPage(
            int pageNumber, AiRouteContentManifest.Page replacement) {
        AiRouteContentManifest.Book book = candidateBook();
        List<AiRouteContentManifest.Page> pages = new ArrayList<>(book.pages());
        pages.replaceAll(page -> page.pageNumber() == pageNumber ? replacement : page);
        return manifest(
                new AiRouteContentManifest.Book(
                        book.bookId(),
                        book.title(),
                        book.pdfPath(),
                        book.pdfSha256(),
                        book.totalPageCount(),
                        true,
                        true,
                        pages));
    }

    private AiRouteContentManifest manifest(AiRouteContentManifest.Book book) {
        return new AiRouteContentManifest(
                "ai-route-v2", POLICY, "text-embedding-3-small", 1536, List.of(book));
    }

    private AiRouteEvaluationDataset emptyEvaluation() {
        return new AiRouteEvaluationDataset("ai-route-v2", List.of());
    }

    private AiRouteEvaluationDataset evaluationWith(
            List<Integer> reference, List<Integer> alternative, List<Integer> irrelevant) {
        return new AiRouteEvaluationDataset(
                "ai-route-v2",
                List.of(
                        new AiRouteEvaluationDataset.EvaluationCase(
                                "case-book-041",
                                BOOK_ID,
                                "테스트 목적",
                                false,
                                5,
                                null,
                                List.of(),
                                List.of("개념 2"),
                                List.of(),
                                List.of(),
                                irrelevant,
                                List.of(),
                                reference,
                                alternative)));
    }
}
