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
                validator.validate(
                        manifest(candidateBook()), defaultEvaluation(), fixtureRoot, POLICY);

        assertEquals(1, validated.books().size());
        assertEquals(48, validated.books().getFirst().pages().size());
    }

    @Test
    void 검증_결과의_페이지는_선수가_앞에_오는_순서다() {
        ValidatedAiRouteContent validated =
                validator.validate(
                        manifest(candidateBook()), defaultEvaluation(), fixtureRoot, POLICY);
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
    void embedding_모델과_차원이_지원_프로필과_다르면_실패한다() {
        AiRouteContentManifest.Book book = candidateBook();
        AiRouteContentManifest wrongModel = manifest(book, "다른-model", 1536);
        AiRouteContentManifest wrongDimensions =
                manifest(book, "text-embedding-3-small", 3072);

        assertTrue(
                fail(wrongModel, defaultEvaluation()).getMessage().contains("embeddingModel"));
        assertTrue(
                fail(wrongDimensions, defaultEvaluation())
                        .getMessage()
                        .contains("embeddingDimensions"));
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
        assertTrue(
                fail(manifest, evaluationWithActiveRentals(List.of(1)))
                        .getMessage()
                        .contains("activeRentalPageNumbers에 후보가 아닌 페이지"));
    }

    @Test
    void 평가의_허용_페이지와_무관_페이지가_겹치면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());

        assertTrue(
                fail(manifest, evaluationWith(List.of(2), List.of(), List.of(2)))
                        .getMessage()
                        .contains("정답과 무관 페이지가 겹칩니다"));
        assertTrue(
                fail(manifest, evaluationWith(List.of(2), List.of(3), List.of(3)))
                        .getMessage()
                        .contains("대체와 무관 페이지가 겹칩니다"));
    }

    @Test
    void 지원_도서마다_평가_케이스가_정확히_하나여야_한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());
        AiRouteEvaluationDataset evaluation = defaultEvaluation();
        AiRouteEvaluationDataset duplicate =
                new AiRouteEvaluationDataset(
                        "ai-route-v2",
                        List.of(evaluation.cases().getFirst(), evaluation.cases().getFirst()));

        assertTrue(
                fail(manifest, emptyEvaluation())
                        .getMessage()
                        .contains("평가 케이스가 없습니다"));
        assertTrue(
                fail(manifest, duplicate)
                        .getMessage()
                        .contains("평가 케이스가 둘 이상"));
    }

    @Test
    void 평가_개념이_도서_메타데이터에_없으면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("없는 필수 개념"),
                                        List.of(),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("requiredConcepts"));
        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of("없는 도움 개념"),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("helpfulConcepts"));
    }

    @Test
    void 평가_개념이_후보가_아닌_페이지에만_있으면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 1"),
                                        List.of(),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("requiredConcepts"));
        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of("개념 1"),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("helpfulConcepts"));
    }

    @Test
    void 필수_개념은_후보_primary에_있어야_하고_도움_개념은_secondary도_허용한다() {
        AiRouteContentManifest.Page page = candidateBook().pages().get(1);
        AiRouteContentManifest secondaryOnly =
                manifestWithReplacedPage(
                        2,
                        copyPage(
                                page,
                                List.of("다른 필수 개념"),
                                List.of("보조 전용 개념"),
                                page.duplicateGroupKeys()));

        assertTrue(
                fail(
                                secondaryOnly,
                                evaluationWithAnswers(
                                        List.of("보조 전용 개념"),
                                        List.of(),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("requiredConcepts"));
        validator.validate(
                secondaryOnly,
                evaluationWithAnswers(
                        List.of("다른 필수 개념"),
                        List.of("보조 전용 개념"),
                        List.of(),
                        List.of()),
                fixtureRoot,
                POLICY);
    }

    @Test
    void 정답_경로가_필수_개념을_덮지_않으면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 4"),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(2),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("referencePageNumbers"));
    }

    @Test
    void 평가의_필수_선수_관계가_실제_DAG_간선이_아니면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());
        AiRouteEvaluationDataset.RequiredPrerequisite notAnEdge =
                new AiRouteEvaluationDataset.RequiredPrerequisite(2, 4);

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(notAnEdge),
                                        List.of()))
                        .getMessage()
                        .contains("실제 선수 간선이 아닙니다"));
    }

    @Test
    void 필수_선수_관계는_정답_경로의_선수_폐쇄_전체여야_한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());
        AiRouteEvaluationDataset.RequiredPrerequisite edge =
                new AiRouteEvaluationDataset.RequiredPrerequisite(2, 3);
        AiRouteEvaluationDataset.RequiredPrerequisite missingPage =
                new AiRouteEvaluationDataset.RequiredPrerequisite(999, 2);

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 3"),
                                        List.of(),
                                        List.of(edge),
                                        List.of(),
                                        List.of(3),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("선수 폐쇄"));
        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 3"),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(2, 3),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("누락="));
        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(edge),
                                        List.of()))
                        .getMessage()
                        .contains("초과="));
        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(missingPage),
                                        List.of()))
                        .getMessage()
                        .contains("페이지가 도서에 없습니다"));
        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 3"),
                                        List.of(),
                                        List.of(edge, edge),
                                        List.of(),
                                        List.of(2, 3),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("중복됩니다"));
        validator.validate(
                manifest,
                evaluationWithAnswers(
                        List.of("개념 3"),
                        List.of(),
                        List.of(edge),
                        List.of(),
                        List.of(2, 3),
                        List.of(),
                        List.of()),
                fixtureRoot,
                POLICY);
    }

    @Test
    void 평가의_중복_페이지_그룹이_manifest와_다르면_실패한다() {
        AiRouteContentManifest manifest = manifest(candidateBook());

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(),
                                        List.of(List.of(2, 3))))
                        .getMessage()
                        .contains("초과="));
    }

    @Test
    void 중복_페이지_그룹은_두_페이지_이상이고_정답_경로에서는_하나만_고른다() {
        AiRouteContentManifest singleton = manifestWithDuplicateGroup(List.of(2));
        AiRouteContentManifest duplicatePair = manifestWithDuplicateGroup(List.of(3, 10));
        List<AiRouteEvaluationDataset.RequiredPrerequisite> edges = List.of(
                new AiRouteEvaluationDataset.RequiredPrerequisite(2, 3),
                new AiRouteEvaluationDataset.RequiredPrerequisite(2, 9),
                new AiRouteEvaluationDataset.RequiredPrerequisite(9, 10));

        assertTrue(
                fail(
                                duplicatePair,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("누락="));
        assertTrue(
                fail(singleton, emptyEvaluation())
                        .getMessage()
                        .contains("2페이지 이상"));
        assertTrue(
                fail(
                                duplicatePair,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(),
                                        List.of(List.of(3))))
                        .getMessage()
                        .contains("그룹마다 2페이지 이상"));
        assertTrue(
                fail(
                                duplicatePair,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(),
                                        List.of(List.of(3, 3))))
                        .getMessage()
                        .contains("같은 페이지가 중복됩니다"));
        assertTrue(
                fail(
                                duplicatePair,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(),
                                        List.of(List.of(3, 10), List.of(10, 3))))
                        .getMessage()
                        .contains("같은 그룹이 중복됩니다"));
        assertTrue(
                fail(
                                duplicatePair,
                                evaluationWithAnswers(
                                        List.of("개념 10"),
                                        List.of(),
                                        edges,
                                        List.of(List.of(3, 10)),
                                        List.of(2, 3, 9, 10),
                                        List.of(),
                                        List.of()))
                        .getMessage()
                        .contains("같은 중복 그룹 페이지"));
        validator.validate(
                duplicatePair,
                evaluationWithAnswers(
                        List.of("개념 2"),
                        List.of(),
                        List.of(),
                        List.of(List.of(3, 10))),
                fixtureRoot,
                POLICY);
    }

    @Test
    void 후보와_전이적_선수_폐쇄에_같은_중복_그룹이_둘_이상이면_실패한다() {
        AiRouteContentManifest directConflict = manifestWithDuplicateGroup(List.of(2, 3));
        AiRouteContentManifest convergingConflict = manifestWithConvergingDuplicatePrerequisites();

        assertTrue(
                fail(directConflict, defaultEvaluation())
                        .getMessage()
                        .contains("선수 폐쇄에 중복 그룹"));
        assertTrue(
                fail(convergingConflict, defaultEvaluation())
                        .getMessage()
                        .contains("선수 폐쇄에 중복 그룹"));
    }

    @Test
    void 후보가_아닌_페이지는_중복_그룹을_가질_수_없다() {
        AiRouteContentManifest manifest = manifestWithDuplicateGroup(List.of(1, 2));

        assertTrue(
                fail(
                                manifest,
                                evaluationWithAnswers(
                                        List.of("개념 2"),
                                        List.of(),
                                        List.of(),
                                        List.of(List.of(1, 2))))
                        .getMessage()
                        .contains("후보가 아닌 페이지의 duplicateGroupKeys"));
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

    private AiRouteContentManifest manifestWithDuplicateGroup(List<Integer> pageNumbers) {
        AiRouteContentManifest.Book book = candidateBook();
        List<AiRouteContentManifest.Page> pages = new ArrayList<>(book.pages());
        pages.replaceAll(
                page ->
                        pageNumbers.contains(page.pageNumber())
                                ? copyPage(
                                        page,
                                        page.primaryConcepts(),
                                        page.secondaryConcepts(),
                                        List.of("같은 내용"))
                                : page);
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

    private AiRouteContentManifest manifestWithConvergingDuplicatePrerequisites() {
        AiRouteContentManifest grouped = manifestWithDuplicateGroup(List.of(3, 10));
        AiRouteContentManifest.Book book = grouped.books().getFirst();
        List<AiRouteContentManifest.Page> pages = new ArrayList<>(book.pages());
        pages.replaceAll(page -> page.pageNumber() == 11
                ? copyPage(
                        page,
                        page.primaryConcepts(),
                        page.secondaryConcepts(),
                        page.duplicateGroupKeys(),
                        List.of(3, 10))
                : page);
        return manifest(new AiRouteContentManifest.Book(
                book.bookId(),
                book.title(),
                book.pdfPath(),
                book.pdfSha256(),
                book.totalPageCount(),
                true,
                true,
                pages));
    }

    private AiRouteContentManifest.Page copyPage(
            AiRouteContentManifest.Page page,
            List<String> primaryConcepts,
            List<String> secondaryConcepts,
            List<String> duplicateGroupKeys) {
        return copyPage(
                page,
                primaryConcepts,
                secondaryConcepts,
                duplicateGroupKeys,
                page.prerequisitePageNumbers());
    }

    private AiRouteContentManifest.Page copyPage(
            AiRouteContentManifest.Page page,
            List<String> primaryConcepts,
            List<String> secondaryConcepts,
            List<String> duplicateGroupKeys,
            List<Integer> prerequisitePageNumbers) {
        return new AiRouteContentManifest.Page(
                page.pageNumber(),
                page.chapter(),
                page.section(),
                primaryConcepts,
                secondaryConcepts,
                page.contentRole(),
                page.aiRouteCandidatePage(),
                page.aiAnalysisText(),
                page.aiAnalysisInputSha256(),
                page.aiPublicGuideTopic(),
                page.estimatedReadingSeconds(),
                prerequisitePageNumbers,
                duplicateGroupKeys);
    }

    private AiRouteContentManifest manifest(AiRouteContentManifest.Book book) {
        return manifest(book, "text-embedding-3-small", 1536);
    }

    private AiRouteContentManifest manifest(
            AiRouteContentManifest.Book book, String embeddingModel, int embeddingDimensions) {
        return new AiRouteContentManifest(
                "ai-route-v2", POLICY, embeddingModel, embeddingDimensions, List.of(book));
    }

    private AiRouteEvaluationDataset emptyEvaluation() {
        return new AiRouteEvaluationDataset("ai-route-v2", List.of());
    }

    private AiRouteEvaluationDataset defaultEvaluation() {
        return evaluationWith(List.of(2), List.of(), List.of());
    }

    private AiRouteEvaluationDataset evaluationWith(
            List<Integer> reference, List<Integer> alternative, List<Integer> irrelevant) {
        return evaluationWithAnswers(
                List.of("개념 2"),
                List.of(),
                List.of(),
                List.of(),
                reference,
                alternative,
                irrelevant);
    }

    private AiRouteEvaluationDataset evaluationWithActiveRentals(List<Integer> activeRentals) {
        return evaluationWithAnswers(
                List.of("개념 2"),
                List.of(),
                List.of(),
                List.of(),
                activeRentals,
                List.of(2),
                List.of(),
                List.of());
    }

    private AiRouteEvaluationDataset evaluationWithAnswers(
            List<String> requiredConcepts,
            List<String> helpfulConcepts,
            List<AiRouteEvaluationDataset.RequiredPrerequisite> requiredPrerequisites,
            List<List<Integer>> duplicatePageGroups) {
        return evaluationWithAnswers(
                requiredConcepts,
                helpfulConcepts,
                requiredPrerequisites,
                duplicatePageGroups,
                List.of(2),
                List.of(),
                List.of());
    }

    private AiRouteEvaluationDataset evaluationWithAnswers(
            List<String> requiredConcepts,
            List<String> helpfulConcepts,
            List<AiRouteEvaluationDataset.RequiredPrerequisite> requiredPrerequisites,
            List<List<Integer>> duplicatePageGroups,
            List<Integer> reference,
            List<Integer> alternative,
            List<Integer> irrelevant) {
        return evaluationWithAnswers(
                requiredConcepts,
                helpfulConcepts,
                requiredPrerequisites,
                duplicatePageGroups,
                List.of(),
                reference,
                alternative,
                irrelevant);
    }

    private AiRouteEvaluationDataset evaluationWithAnswers(
            List<String> requiredConcepts,
            List<String> helpfulConcepts,
            List<AiRouteEvaluationDataset.RequiredPrerequisite> requiredPrerequisites,
            List<List<Integer>> duplicatePageGroups,
            List<Integer> activeRentalPageNumbers,
            List<Integer> reference,
            List<Integer> alternative,
            List<Integer> irrelevant) {
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
                                activeRentalPageNumbers,
                                requiredConcepts,
                                helpfulConcepts,
                                requiredPrerequisites,
                                irrelevant,
                                duplicatePageGroups,
                                reference,
                                alternative)));
    }
}
