package com.example.ilgeobolkka.airoute.service.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiRouteCandidateSelectorTest {

    private static final String MODEL = "text-embedding-3-small";
    private static final long BOOK_ID = 42L;
    private static final String CONTENT_VERSION = "ai-route-v2";

    /**
     * 이 값을 두 번째 성분으로 쓰면 {@code {x, PARTNER}}의 norm이 정확히 {@code 1.0}이 된다. 목적 벡터도
     * norm이 1.0이라 cosine이 {@code x}와 <b>완전히</b> 같아진다. 임계값 경계를 부동소수점 오차 없이
     * 고정하려고 쓴다.
     */
    private static final double UNIT_PARTNER_FOR_030 = 0.9539392014169457;

    private static final double UNIT_PARTNER_FOR_050 = 0.8660254037844387;

    private final AiRouteCandidateSelector selector = new AiRouteCandidateSelector();

    // --- cosine 계산 -------------------------------------------------------

    @Test
    void 같은_방향은_1_직교는_0_반대는_음수가_되어_직교와_반대는_후보에서_빠진다() {
        AiRouteEmbedding purpose = embedding(1.0, 0.0);

        List<AiRouteCandidate> candidates =
                selector.select(
                        BOOK_ID,
                        CONTENT_VERSION,
                        purpose,
                        List.of(
                                page(1, embedding(1.0, 0.0)), // 같은 방향 → 1.0
                                page(2, embedding(0.0, 1.0)), // 직교 → 0.0
                                page(3, embedding(-1.0, 0.0)))); // 반대 → -1.0

        assertEquals(List.of(1L), pageIdsOf(candidates));
        assertEquals(1.0, candidates.get(0).similarity());
    }

    // --- 임베딩 유효성 ------------------------------------------------------

    @Test
    void 목적과_모델이_다른_페이지가_있으면_계산_전에_실패한다() {
        AiRouteEmbedding otherModel = AiRouteEmbedding.of("text-embedding-3-large", 2, new double[] {1.0, 0.0});

        assertThrows(
                InvalidAiRouteEmbeddingException.class,
                () ->
                        selector.select(
                                BOOK_ID,
                                CONTENT_VERSION,
                                embedding(1.0, 0.0),
                                List.of(page(1, otherModel))));
    }

    @Test
    void 목적과_차원이_다른_페이지가_있으면_계산_전에_실패한다() {
        AiRouteEmbedding threeDimensions = AiRouteEmbedding.of(MODEL, 3, new double[] {1.0, 0.0, 0.0});

        assertThrows(
                InvalidAiRouteEmbeddingException.class,
                () ->
                        selector.select(
                                BOOK_ID,
                                CONTENT_VERSION,
                                embedding(1.0, 0.0),
                                List.of(page(1, threeDimensions))));
    }

    @Test
    void zero_norm_벡터는_similarity_0으로_대체하지_않고_실패한다() {
        // 0 으로 조용히 처리하면 잘못된 콘텐츠가 "관련 없음"으로 위장된다.
        assertThrows(
                InvalidAiRouteEmbeddingException.class,
                () -> AiRouteEmbedding.of(MODEL, 2, new double[] {0.0, 0.0}));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 유한하지_않은_값이_있으면_실패한다(double value) {
        assertThrows(
                InvalidAiRouteEmbeddingException.class,
                () -> AiRouteEmbedding.of(MODEL, 2, new double[] {value, 1.0}));
    }

    @Test
    void 선언한_차원과_값_개수가_다르면_실패한다() {
        assertThrows(
                InvalidAiRouteEmbeddingException.class,
                () -> AiRouteEmbedding.of(MODEL, 3, new double[] {1.0, 0.0}));
    }

    // --- 0.30 임계값 경계 ---------------------------------------------------

    @Test
    void 임계값_경계_기법이_실제로_오차가_없다() {
        // 아래 세 경계 테스트는 "cosine == 첫 성분"에 기대고 있다. norm 이 1.0 이 아니게 되면
        // 세 테스트가 조용히 무의미해지므로 전제를 먼저 못박는다.
        assertEquals(1.0, embedding(1.0, 0.0).norm());
        assertEquals(1.0, embedding(0.30, UNIT_PARTNER_FOR_030).norm());
        assertEquals(1.0, embedding(0.50, UNIT_PARTNER_FOR_050).norm());
    }

    @Test
    void 임계값_직전_1_ULP는_제외한다() {
        double justBelow = Math.nextDown(AiRouteCandidatePolicy.MINIMUM_SIMILARITY);

        assertTrue(selectSingle(justBelow).isEmpty(), () -> "제외 기대: " + justBelow);
    }

    @Test
    void 정확히_임계값이면_포함한다() {
        List<AiRouteCandidate> candidates = selectSingle(AiRouteCandidatePolicy.MINIMUM_SIMILARITY);

        assertEquals(1, candidates.size());
        assertEquals(AiRouteCandidatePolicy.MINIMUM_SIMILARITY, candidates.get(0).similarity());
    }

    @Test
    void 임계값_직후_1_ULP는_포함한다() {
        double justAbove = Math.nextUp(AiRouteCandidatePolicy.MINIMUM_SIMILARITY);

        List<AiRouteCandidate> candidates = selectSingle(justAbove);

        assertEquals(1, candidates.size());
        assertEquals(justAbove, candidates.get(0).similarity());
    }

    // --- 정렬과 상한 --------------------------------------------------------

    @Test
    void similarity_내림차순으로_정렬한다() {
        AiRouteEmbedding purpose = embedding(1.0, 0.0);

        List<AiRouteCandidate> candidates =
                selector.select(
                        BOOK_ID,
                        CONTENT_VERSION,
                        purpose,
                        List.of(
                                page(1, embedding(5.0, 12.0)), // 5/13  ≈ 0.3846
                                page(2, embedding(24.0, 7.0)), // 24/25 = 0.96
                                page(3, embedding(7.0, 24.0)), // 7/25  = 0.28 → 제외
                                page(4, embedding(3.0, 4.0)))); // 3/5   = 0.6

        assertEquals(List.of(2L, 4L, 1L), pageIdsOf(candidates));
    }

    @ParameterizedTest
    @ValueSource(ints = {29, 30, 31})
    void 상한은_30개이고_동점이면_pageNumber_오름차순으로_자른다(int pageCount) {
        // 모든 페이지의 similarity 가 정확히 같아 정렬이 pageNumber 로만 결정된다.
        List<AiRouteCandidatePage> pages = new ArrayList<>();
        for (int pageNumber = pageCount; pageNumber >= 1; pageNumber--) { // 일부러 역순 입력
            pages.add(page(pageNumber, embedding(0.50, UNIT_PARTNER_FOR_050)));
        }

        List<AiRouteCandidate> candidates =
                selector.select(BOOK_ID, CONTENT_VERSION, embedding(1.0, 0.0), pages);

        int expectedSize = Math.min(pageCount, AiRouteCandidatePolicy.MAXIMUM_CANDIDATES);
        assertEquals(expectedSize, candidates.size());
        for (int index = 0; index < expectedSize; index++) {
            assertEquals(index + 1, candidates.get(index).pageNumber());
        }
    }

    @Test
    void 임계값을_넘는_페이지가_없으면_빈_목록이다() {
        // NO_RELEVANT_PAGES 판정은 상위 단계가 한다. selector 는 빈 결과까지만 책임진다.
        List<AiRouteCandidate> candidates =
                selector.select(
                        BOOK_ID,
                        CONTENT_VERSION,
                        embedding(1.0, 0.0),
                        List.of(page(1, embedding(7.0, 24.0)))); // 0.28

        assertTrue(candidates.isEmpty());
    }

    // --- 입력 경계 ----------------------------------------------------------

    @Test
    void TEXT와_IMAGE를_같은_기준으로_처리한다() {
        AiRouteEmbedding sameVector = embedding(0.50, UNIT_PARTNER_FOR_050);

        List<AiRouteCandidate> candidates =
                selector.select(
                        BOOK_ID,
                        CONTENT_VERSION,
                        embedding(1.0, 0.0),
                        List.of(
                                page(1, sameVector, BookPageContentType.TEXT),
                                page(2, sameVector, BookPageContentType.IMAGE)));

        assertEquals(List.of(1L, 2L), pageIdsOf(candidates));
        assertEquals(candidates.get(0).similarity(), candidates.get(1).similarity());
    }

    @Test
    void 다른_콘텐츠_버전의_페이지가_섞이면_거부한다() {
        AiRouteCandidatePage otherVersion =
                new AiRouteCandidatePage(
                        BOOK_ID,
                        "initial-v1",
                        7L,
                        7,
                        BookPageContentType.TEXT,
                        embedding(1.0, 0.0),
                        "analysis-7",
                        List.of());

        assertThrows(
                InvalidAiRouteCandidateInputException.class,
                () ->
                        selector.select(
                                BOOK_ID,
                                CONTENT_VERSION,
                                embedding(1.0, 0.0),
                                List.of(page(1, embedding(1.0, 0.0)), otherVersion)));
    }

    @Test
    void 다른_도서의_페이지가_섞이면_거부한다() {
        AiRouteCandidatePage otherBook =
                new AiRouteCandidatePage(
                        BOOK_ID + 1,
                        CONTENT_VERSION,
                        7L,
                        7,
                        BookPageContentType.TEXT,
                        embedding(1.0, 0.0),
                        "analysis-7",
                        List.of());

        assertThrows(
                InvalidAiRouteCandidateInputException.class,
                () ->
                        selector.select(
                                BOOK_ID,
                                CONTENT_VERSION,
                                embedding(1.0, 0.0),
                                List.of(page(1, embedding(1.0, 0.0)), otherBook)));
    }

    @Test
    void 같은_페이지_번호가_두_번_들어오면_거부한다() {
        // 중복을 허용하면 30개 상한과 정렬 결과가 입력 순서에 따라 달라진다.
        assertThrows(
                InvalidAiRouteCandidateInputException.class,
                () ->
                        selector.select(
                                BOOK_ID,
                                CONTENT_VERSION,
                                embedding(1.0, 0.0),
                                List.of(page(1, embedding(1.0, 0.0)), page(1, embedding(3.0, 4.0)))));
    }

    // --- 결정성과 범위 ------------------------------------------------------

    @Test
    void 같은_입력을_반복하면_같은_결과가_나온다() {
        List<AiRouteCandidatePage> pages =
                List.of(
                        page(1, embedding(5.0, 12.0)),
                        page(2, embedding(24.0, 7.0)),
                        page(3, embedding(3.0, 4.0)));

        List<AiRouteCandidate> first =
                selector.select(BOOK_ID, CONTENT_VERSION, embedding(1.0, 0.0), pages);
        List<AiRouteCandidate> second =
                selector.select(BOOK_ID, CONTENT_VERSION, embedding(1.0, 0.0), pages);

        assertEquals(first, second);
    }

    @Test
    void 평가_정답을_받는_필드가_입출력_타입에_없다() {
        // requiredConcepts·allowedAlternativePageNumbers 는 evaluation.json 계약에만 있고
        // runtime 검색 입력이 아니다. 필드가 생기면 정답이 후보 선정에 흘러들어올 수 있다.
        Set<String> forbidden = Set.of("requiredconcept", "allowedalternative", "expected", "answer");

        assertTrue(componentNames(AiRouteCandidatePage.class).stream().noneMatch(
                name -> forbidden.stream().anyMatch(name::contains)),
                () -> "입력 타입 필드: " + componentNames(AiRouteCandidatePage.class));
        assertTrue(componentNames(AiRouteCandidate.class).stream().noneMatch(
                name -> forbidden.stream().anyMatch(name::contains)),
                () -> "출력 타입 필드: " + componentNames(AiRouteCandidate.class));
    }

    @Test
    void similarity를_백분율로_바꾸지_않고_원값_그대로_돌려준다() {
        List<AiRouteCandidate> candidates =
                selector.select(
                        BOOK_ID,
                        CONTENT_VERSION,
                        embedding(1.0, 0.0),
                        List.of(page(1, embedding(3.0, 4.0)))); // 3/5

        // 3/5 를 그대로 돌려준다. 60 이나 0.60 으로 가공하면 이 단언이 깨진다.
        assertEquals(0.6, candidates.get(0).similarity());
    }

    // --- fixture ------------------------------------------------------------

    private List<AiRouteCandidate> selectSingle(double targetSimilarity) {
        return selector.select(
                BOOK_ID,
                CONTENT_VERSION,
                embedding(1.0, 0.0),
                List.of(page(1, embedding(targetSimilarity, UNIT_PARTNER_FOR_030))));
    }

    private AiRouteEmbedding embedding(double first, double second) {
        return AiRouteEmbedding.of(MODEL, 2, new double[] {first, second});
    }

    private AiRouteCandidatePage page(int pageNumber, AiRouteEmbedding embedding) {
        return page(pageNumber, embedding, BookPageContentType.TEXT);
    }

    private AiRouteCandidatePage page(
            int pageNumber, AiRouteEmbedding embedding, BookPageContentType contentType) {
        return new AiRouteCandidatePage(
                BOOK_ID,
                CONTENT_VERSION,
                pageNumber,
                pageNumber,
                contentType,
                embedding,
                "analysis-" + pageNumber,
                List.of());
    }

    private List<Long> pageIdsOf(List<AiRouteCandidate> candidates) {
        return candidates.stream().map(AiRouteCandidate::pageId).toList();
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }
}
