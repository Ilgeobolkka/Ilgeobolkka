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
    void 후보에서_탈락한_선수도_전이_폐쇄로_허용하고_입력_순서를_위치로_고정한다() {
        AiRouteCandidatePage belowThresholdPrerequisite =
                page(5, List.of(), embedding(0.0, 1.0));
        AiRouteCandidatePage candidatePage =
                page(10, List.of(5), embedding(1.0, 0.0));

        ValidatedRouteProposal result =
                validate(
                        List.of(belowThresholdPrerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(5, false), item(10, false)));

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
    void 후보_30개를_고른_뒤에도_상한_밖의_다단계_선수를_모두_허용한다() {
        List<AiRouteCandidatePage> pages = new ArrayList<>();
        pages.add(page(1, List.of()));
        pages.add(page(2, List.of(1)));
        List<AiRouteCandidate> candidates = new ArrayList<>();
        for (int pageNumber = 100; pageNumber < 130; pageNumber++) {
            AiRouteCandidatePage candidatePage =
                    page(pageNumber, pageNumber == 100 ? List.of(2) : List.of());
            pages.add(candidatePage);
            candidates.add(candidate(candidatePage));
        }

        ValidatedRouteProposal result =
                validate(pages, candidates, proposal(item(1, false), item(2, false), item(100, false)));

        assertAll(
                () -> assertEquals(30, candidates.size()),
                () -> assertEquals(Set.of(1, 2), result.prerequisiteClosureByCandidate().get(100)),
                () -> assertEquals(32, result.allowedPageNumbers().size()),
                () -> assertTrue(result.allowedPageNumbers().containsAll(Set.of(1, 2))));
    }

    @Test
    void 후보가_30개를_초과하면_검증_문맥을_거부한다() {
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
                () -> validate(pages, candidates, proposal(item(1, false))));
    }

    @Test
    void 검증_문맥_오류는_재시도하지_않고_모델_출력_오류만_재시도한다() {
        assertAll(
                () -> assertFalse(Failure.CONTEXT_MISMATCH.retryable()),
                () -> assertTrue(Failure.PAGE_NOT_FOUND.retryable()),
                () -> assertTrue(Failure.PAGE_OUTSIDE_ALLOWED_SET.retryable()),
                () -> assertTrue(Failure.DUPLICATE_PAGE.retryable()),
                () -> assertTrue(Failure.MISSING_PREREQUISITE.retryable()),
                () -> assertTrue(Failure.INVALID_PREREQUISITE_ORDER.retryable()),
                () -> assertTrue(Failure.INVALID_ENUM.retryable()));
    }

    @Test
    void 서버_그래프의_선수_판정이_모델의_false보다_우선한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        ValidatedRouteProposal result =
                validate(
                        List.of(prerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(5, false), item(10, true)));

        assertAll(
                () -> assertTrue(result.items().get(0).prerequisite()),
                () -> assertFalse(result.items().get(1).prerequisite()),
                () -> assertEquals(AiRouteItemRelevance.HIGH, result.items().get(0).relevance()),
                () -> assertEquals(AiRouteItemRole.CORE, result.items().get(0).role()));
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
                        () -> validate(List.of(otherBook), List.of(candidate(otherBook)), proposal(item(10, false)))),
                () -> assertFailure(
                        Failure.CONTEXT_MISMATCH,
                        () -> validate(
                                List.of(otherVersion),
                                List.of(candidate(otherVersion)),
                                proposal(item(10, false)))));
    }

    @Test
    void 존재하지_않는_페이지면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());

        assertFailure(
                Failure.PAGE_NOT_FOUND,
                () -> validate(
                        List.of(candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(99, false))));
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
                        proposal(item(20, false))));
    }

    @Test
    void 같은_페이지가_두_번_나오면_전체_거부한다() {
        AiRouteCandidatePage candidatePage = page(10, List.of());

        assertFailure(
                Failure.DUPLICATE_PAGE,
                () -> validate(
                        List.of(candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(10, false), item(10, false))));
    }

    @Test
    void 선수_페이지가_누락되면_전체_거부한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        assertFailure(
                Failure.MISSING_PREREQUISITE,
                () -> validate(
                        List.of(prerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(10, false))));
    }

    @Test
    void 선수가_의존_페이지보다_뒤에_있으면_전체_거부한다() {
        AiRouteCandidatePage prerequisite = page(5, List.of());
        AiRouteCandidatePage candidatePage = page(10, List.of(5));

        assertFailure(
                Failure.INVALID_PREREQUISITE_ORDER,
                () -> validate(
                        List.of(prerequisite, candidatePage),
                        List.of(candidate(candidatePage)),
                        proposal(item(10, false), item(5, false))));
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
                                proposal(new ModelRouteItem(10, null, false, Role.CORE)))),
                () -> assertFailure(
                        Failure.INVALID_ENUM,
                        () -> validate(
                                List.of(candidatePage),
                                List.of(candidate(candidatePage)),
                                proposal(new ModelRouteItem(10, Relevance.HIGH, false, null)))));
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
                                proposal(item(10, false), item(20, false))));

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

    private ModelRouteItem item(int pageNumber, boolean prerequisite) {
        return new ModelRouteItem(
                pageNumber, Relevance.HIGH, prerequisite, Role.CORE);
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
