package com.example.ilgeobolkka.airoute.service.validation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidate;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePage;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePolicy;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteEmbedding;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException.Failure;
import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.ModelRouteItem;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.ModelRouteProposal;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.Relevance;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiRouteOutputValidatorTest {

    private static final long BOOK_ID = 42L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String MODEL = "text-embedding-3-small";
    private static final long PAGE_ID_BASE = 1000L;

    private final AiRouteOutputValidator validator = new AiRouteOutputValidator();

    @Test
    void 모델이_고른_후보에_후보에서_탈락한_선수를_자동으로_앞에_추가한다() {
        AiRouteCandidatePage belowThresholdPrerequisite =
                page(5, List.of(), embedding(0.0, 1.0));
        AiRouteCandidatePage candidatePage =
                page(10, List.of(5), embedding(1.0, 0.0));

        ValidatedRouteProposal result =
                validate(
                        List.of(belowThresholdPrerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(10)));

        assertAll(
                () -> assertEquals(Set.of(5), result.prerequisiteClosureByCandidate().get(10)),
                () -> assertEquals(Set.of(5, 10), result.allowedPageNumbers()),
                () -> assertEquals(List.of(1, 2), result.items().stream()
                        .map(ValidatedRouteProposal.ValidatedRouteItem::position)
                        .toList()),
                () -> assertEquals(List.of(PAGE_ID_BASE + 5, PAGE_ID_BASE + 10), result.items().stream()
                        .map(ValidatedRouteProposal.ValidatedRouteItem::pageId)
                        .toList()));
    }

    @Test
    void 후보_40개를_고른_뒤에도_상한_밖의_다단계_선수를_모두_허용한다() {
        List<AiRouteCandidatePage> pages = new ArrayList<>();
        pages.add(page(1, List.of()));
        pages.add(page(2, List.of(1)));
        List<AiRouteCandidate> candidates = new ArrayList<>();
        for (int pageNumber = 100; pageNumber < 140; pageNumber++) {
            AiRouteCandidatePage candidatePage =
                    page(pageNumber, pageNumber == 100 ? List.of(2) : List.of());
            pages.add(candidatePage);
            candidates.add(candidate(candidatePage));
        }

        ValidatedRouteProposal result =
                validate(pages, candidates, proposal(item(100)));

        assertAll(
                () -> assertEquals(40, candidates.size()),
                () -> assertEquals(Set.of(100), result.prerequisiteClosureByCandidate().keySet()),
                () -> assertEquals(Set.of(1, 2), result.prerequisiteClosureByCandidate().get(100)),
                () -> assertEquals(42, result.allowedPageNumbers().size()),
                () -> assertTrue(result.allowedPageNumbers().containsAll(Set.of(1, 2))),
                () -> assertEquals(
                        List.of(1, 2, 100),
                        result.items().stream()
                                .map(ValidatedRouteProposal.ValidatedRouteItem::pageNumber)
                                .toList()));
    }

    @Test
    void 후보가_40개를_초과하면_검증_문맥을_거부한다() {
        List<AiRouteCandidatePage> pages = new ArrayList<>();
        List<AiRouteCandidate> candidates = new ArrayList<>();
        for (int pageNumber = 1;
                pageNumber <= AiRouteCandidatePolicy.MAXIMUM_CANDIDATES + 1;
                pageNumber++) {
            AiRouteCandidatePage candidatePage = page(pageNumber, List.of());
            pages.add(candidatePage);
            candidates.add(candidate(candidatePage));
        }

        assertFailure(
                Failure.CONTEXT_MISMATCH,
                () -> validate(pages, candidates, proposal(item(1))));
    }

    @Test
    void 검증_문맥_오류는_재시도하지_않고_모델_출력_오류만_재시도한다() {
        assertAll(
                () -> assertFalse(Failure.CONTEXT_MISMATCH.retryable()),
                () -> assertTrue(Failure.PAGE_NOT_FOUND.retryable()),
                () -> assertTrue(Failure.PAGE_OUTSIDE_ALLOWED_SET.retryable()),
                () -> assertTrue(Failure.MISSING_CANDIDATE.retryable()),
                () -> assertTrue(Failure.DUPLICATE_PAGE.retryable()),
                () -> assertTrue(Failure.EMPTY_PROPOSAL.retryable()),
                () -> assertTrue(Failure.INVALID_ENUM.retryable()));
    }

    @Test
    void 서버가_추가한_선수는_MEDIUM과_PREREQUISITE로_고정한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        ValidatedRouteProposal result =
                validate(
                        List.of(prerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(10)));

        assertAll(
                () -> assertTrue(result.items().get(0).prerequisite()),
                () -> assertFalse(result.items().get(1).prerequisite()),
                () -> assertEquals(AiRouteItemRelevance.MEDIUM, result.items().get(0).relevance()),
                () -> assertEquals(AiRouteItemRole.PREREQUISITE, result.items().get(0).role()));
    }

    @Test
    void 다른_도서나_콘텐츠_버전이_섞인_snapshot은_전체_거부한다() {
        AiRouteCandidatePage otherBook =
                page(BOOK_ID + 1, CONTENT_VERSION, 10, List.of());
        AiRouteCandidatePage otherVersion =
                page(BOOK_ID, "old-version", 10, List.of());

        assertAll(
                () -> assertFailure(
                        Failure.CONTEXT_MISMATCH,
                        () -> validate(List.of(otherBook), List.of(candidate(otherBook)), proposal(item(10)))),
                () -> assertFailure(
                        Failure.CONTEXT_MISMATCH,
                        () -> validate(
                                List.of(otherVersion),
                                List.of(candidate(otherVersion)),
                                proposal(item(10)))));
    }

    @Test
    void 존재하지_않는_페이지면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());

        assertFailure(
                Failure.PAGE_NOT_FOUND,
                () -> validate(
                        List.of(candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(99))));
    }

    @Test
    void 같은_버전에_존재해도_허용_집합_밖이면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());
        AiRouteCandidatePage unrelatedPage = page(20, List.of());

        assertFailure(
                Failure.PAGE_OUTSIDE_ALLOWED_SET,
                () -> validate(
                        List.of(candidatePage, unrelatedPage),
                        List.of(candidate(candidatePage)),
                        proposal(item(20))));
    }

    @Test
    void 모델이_검색_후보가_아닌_선수_페이지만_고르면_전체_거부한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        assertFailure(
                Failure.PAGE_OUTSIDE_ALLOWED_SET,
                () -> validate(
                        List.of(prerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(5))));
    }

    @Test
    void 같은_페이지가_두_번_나오면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());

        assertFailure(
                Failure.DUPLICATE_PAGE,
                () -> validate(
                        List.of(candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(10), item(10))));
    }

    @Test
    void 모델에서_선수_페이지가_누락되어도_서버가_전이_폐쇄를_완성한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        ValidatedRouteProposal result = validate(
                List.of(prerequisite, candidatePage),
                List.of(candidate(candidatePage)),
                proposal(item(10)));

        assertEquals(
                List.of(5, 10),
                result.items().stream()
                        .map(ValidatedRouteProposal.ValidatedRouteItem::pageNumber)
                        .toList());
    }

    @Test
    void 모델이_선택한_선수가_뒤에_있어도_서버가_앞으로_정렬하고_모델_메타데이터를_보존한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        ValidatedRouteProposal result = validate(
                List.of(prerequisite, candidatePage),
                List.of(candidate(prerequisite), candidate(candidatePage)),
                proposal(
                        item(10),
                        new ModelRouteItem(5, Relevance.MEDIUM, Role.CONCLUSION)));

        assertAll(
                () -> assertEquals(
                        List.of(5, 10),
                        result.items().stream()
                                .map(ValidatedRouteProposal.ValidatedRouteItem::pageNumber)
                                .toList()),
                () -> assertEquals(AiRouteItemRelevance.MEDIUM, result.items().get(0).relevance()),
                () -> assertEquals(AiRouteItemRole.CONCLUSION, result.items().get(0).role()),
                () -> assertTrue(result.items().get(0).prerequisite()));
    }

    @Test
    void proposal이_없거나_items가_비어_있으면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());

        assertAll(
                () -> assertFailure(
                        Failure.EMPTY_PROPOSAL,
                        () -> validate(
                                List.of(candidatePage),
                                List.of(candidate(candidatePage)),
                                null)),
                () -> assertFailure(
                        Failure.EMPTY_PROPOSAL,
                        () -> validate(
                                List.of(candidatePage),
                                List.of(candidate(candidatePage)),
                                proposal())));
    }

    @Test
    void F05_허용_enum이_아니면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());

        assertAll(
                () -> assertFailure(
                        Failure.INVALID_ENUM,
                        () -> validate(
                                List.of(candidatePage),
                                List.of(candidate(candidatePage)),
                                proposal(new ModelRouteItem(10, null, Role.CORE)))),
                () -> assertFailure(
                        Failure.INVALID_ENUM,
                        () -> validate(
                                List.of(candidatePage),
                                List.of(candidate(candidatePage)),
                                proposal(new ModelRouteItem(10, Relevance.HIGH, null)))));
    }

    @Test
    void 실패에는_부분_proposal이나_비공개_분석값을_담지_않는다() {
        AiRouteCandidatePage candidatePage =
                new AiRouteCandidatePage(
                        BOOK_ID,
                        CONTENT_VERSION,
                        PAGE_ID_BASE + 10,
                        10,
                        BookPageContentType.TEXT,
                        embedding(1.0, 0.0),
                        "secret-analysis-reference",
                        List.of());
        AiRouteCandidatePage unrelatedPage = page(20, List.of());

        AiRouteInvalidOutputException exception =
                assertThrows(
                        AiRouteInvalidOutputException.class,
                        () -> validate(
                                List.of(candidatePage, unrelatedPage),
                                List.of(candidate(candidatePage)),
                                proposal(item(10), item(20))));

        assertAll(
                () -> assertEquals(Failure.PAGE_OUTSIDE_ALLOWED_SET, exception.failure()),
                () -> assertFalse(exception.getMessage().contains("10")),
                () -> assertFalse(exception.getMessage().contains("20")),
                () -> assertFalse(exception.getMessage().contains("secret")),
                () -> assertEquals(
                        List.of("failure"),
                        Arrays.stream(AiRouteInvalidOutputException.class.getDeclaredFields())
                                .map(field -> field.getName())
                                .toList()));
    }

    private ValidatedRouteProposal validate(
            List<AiRouteCandidatePage> pages,
            List<AiRouteCandidate> candidates,
            ModelRouteProposal proposal) {
        return validator.validate(BOOK_ID, CONTENT_VERSION, pages, candidates, proposal);
    }

    private void assertFailure(Failure expected, Runnable invocation) {
        AiRouteInvalidOutputException exception =
                assertThrows(AiRouteInvalidOutputException.class, invocation::run);
        assertEquals(expected, exception.failure());
    }

    private ModelRouteProposal proposal(ModelRouteItem... items) {
        return new ModelRouteProposal(List.of(items));
    }

    private ModelRouteItem item(int pageNumber) {
        return new ModelRouteItem(pageNumber, Relevance.HIGH, Role.CORE);
    }

    private AiRouteCandidate candidate(AiRouteCandidatePage page) {
        return new AiRouteCandidate(
                page.pageId(),
                page.pageNumber(),
                1.0,
                page.analysisTextRef(),
                page.prerequisitePageNumbers());
    }

    private AiRouteCandidatePage page(int pageNumber, List<Integer> prerequisites) {
        return page(pageNumber, prerequisites, embedding(1.0, 0.0));
    }

    private AiRouteCandidatePage page(
            int pageNumber,
            List<Integer> prerequisites,
            AiRouteEmbedding embedding) {
        return page(BOOK_ID, CONTENT_VERSION, pageNumber, prerequisites, embedding);
    }

    private AiRouteCandidatePage page(
            long bookId,
            String contentVersion,
            int pageNumber,
            List<Integer> prerequisites) {
        return page(bookId, contentVersion, pageNumber, prerequisites, embedding(1.0, 0.0));
    }

    private AiRouteCandidatePage page(
            long bookId,
            String contentVersion,
            int pageNumber,
            List<Integer> prerequisites,
            AiRouteEmbedding embedding) {
        return new AiRouteCandidatePage(
                bookId,
                contentVersion,
                PAGE_ID_BASE + pageNumber,
                pageNumber,
                BookPageContentType.TEXT,
                embedding,
                "analysis-" + pageNumber,
                prerequisites);
    }

    private AiRouteEmbedding embedding(double first, double second) {
        return AiRouteEmbedding.of(MODEL, 2, new double[] {first, second});
    }
}
