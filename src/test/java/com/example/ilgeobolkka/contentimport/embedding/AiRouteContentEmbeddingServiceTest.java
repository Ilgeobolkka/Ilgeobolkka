package com.example.ilgeobolkka.contentimport.embedding;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * C03 embedding batch 테스트.
 *
 * <p>서비스는 Gateway 하나만 주입받는다. Repository·transaction을 부를 경로가 타입 수준에서 없으므로
 * 실패 시 DB 변경 0건은 구조로 보장된다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AiRouteContentEmbeddingServiceTest {

    private static final String VERSION = "ai-route-v2";
    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 3;

    @Test
    void 후보_페이지만_bookId와_페이지_번호_오름차순으로_보낸다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        // 도서와 페이지를 일부러 뒤섞어 넣어도 요청 순서가 고정되는지 본다.
        EmbeddedAiRouteContent embedded =
                service.embed(
                        content(
                                book(42, page(3, true), page(1, false), page(2, true)),
                                book(41, page(2, true), page(1, false))),
                        POLICY);

        assertAll(
                () -> assertEquals(List.of("p2 분석", "p2 분석", "p3 분석"), gateway.analysisTexts),
                () -> assertEquals(List.of(MODEL, MODEL, MODEL), gateway.models),
                () -> assertEquals(List.of(DIMENSIONS, DIMENSIONS, DIMENSIONS), gateway.dimensions),
                () -> assertEquals(3, embedded.vectors().size()),
                () ->
                        assertEquals(
                                List.of(
                                        new EmbeddedAiRouteContent.PageKey(41, 2, VERSION),
                                        new EmbeddedAiRouteContent.PageKey(42, 2, VERSION),
                                        new EmbeddedAiRouteContent.PageKey(42, 3, VERSION)),
                                new ArrayList<>(embedded.vectors().keySet())),
                () -> assertEquals(MODEL, embedded.embeddingModel()),
                () -> assertEquals(DIMENSIONS, embedded.embeddingDimensions()));
    }

    @Test
    void 후보가_아닌_페이지는_호출하지_않고_vector도_없다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        EmbeddedAiRouteContent embedded =
                service.embed(content(book(41, page(1, false), page(2, true))), POLICY);

        assertAll(
                () -> assertEquals(1, gateway.analysisTexts.size()),
                () -> assertEquals("p2 분석", gateway.analysisTexts.getFirst()),
                () -> assertNull(embedded.vectorOf(41, 1)),
                () -> assertEquals(List.of(0.1, 0.2, 0.3), embedded.vectorOf(41, 2)));
    }

    @Test
    void 후보가_없으면_빈_batch를_만들고_호출하지_않는다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        EmbeddedAiRouteContent embedded =
                service.embed(content(novel(1)), POLICY);

        assertAll(
                () -> assertTrue(gateway.analysisTexts.isEmpty()),
                () -> assertTrue(embedded.vectors().isEmpty()));
    }

    @Test
    void 데이터_정책이_어긋나면_Gateway를_한_번도_부르지_않는다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        AiRouteContentEmbeddingException exception =
                assertThrows(
                        AiRouteContentEmbeddingException.class,
                        () -> service.embed(content(book(41, page(2, true))), "OTHER_POLICY"));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("dataPolicyVersion")),
                () -> assertTrue(gateway.analysisTexts.isEmpty()));
    }

    @Test
    void 환경_데이터_정책이_누락되면_Gateway를_한_번도_부르지_않는다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        assertThrows(
                AiRouteContentEmbeddingException.class,
                () -> service.embed(content(book(41, page(2, true))), null));

        assertTrue(gateway.analysisTexts.isEmpty());
    }

    @Test
    void 검증된_콘텐츠가_누락되면_Gateway를_한_번도_부르지_않는다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        assertThrows(AiRouteContentEmbeddingException.class, () -> service.embed(null, POLICY));

        assertTrue(gateway.analysisTexts.isEmpty());
    }

    @Test
    void 중간에_한_페이지가_실패하면_batch를_만들지_않는다() {
        RecordingGateway gateway =
                new RecordingGateway(
                        input -> {
                            if (input.aiAnalysisText().startsWith("p3")) {
                                throw new IllegalStateException("공급자 오류");
                            }
                            return vector(0.1, 0.2, 0.3);
                        });
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        assertThrows(
                IllegalStateException.class,
                () -> service.embed(content(book(41, page(2, true), page(3, true))), POLICY));
        assertEquals(2, gateway.analysisTexts.size());
    }

    @Test
    void 후보_페이지_키가_중복되면_batch를_만들지_않는다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        AiRouteContentEmbeddingException exception =
                assertThrows(
                        AiRouteContentEmbeddingException.class,
                        () ->
                                service.embed(
                                        content(book(41, page(2, true), page(2, true))), POLICY));

        assertAll(
                () -> assertEquals(2, gateway.analysisTexts.size()),
                () -> assertTrue(exception.getMessage().contains("후보 페이지 2개 중 1개")));
    }

    @Test
    void 차원_모델_길이가_어긋나거나_유한하지_않은_값이_있으면_실패한다() {
        assertAll(
                () ->
                        assertTrue(
                                embedFailure(
                                                input ->
                                                        new OpenAiEmbeddingGateway.Embedding(
                                                                List.of(0.1, 0.2), MODEL, 2))
                                        .getMessage()
                                        .contains("차원")),
                () ->
                        assertTrue(
                                embedFailure(
                                                input ->
                                                        new OpenAiEmbeddingGateway.Embedding(
                                                                List.of(0.1, 0.2), MODEL, DIMENSIONS))
                                        .getMessage()
                                        .contains("길이")),
                () ->
                        assertTrue(
                                embedFailure(
                                                input ->
                                                        new OpenAiEmbeddingGateway.Embedding(
                                                                List.of(0.1, 0.2, 0.3),
                                                                "other-model",
                                                                DIMENSIONS))
                                        .getMessage()
                                        .contains("모델")),
                () ->
                        assertTrue(
                                embedFailure(input -> vector(0.1, Double.NaN, 0.3))
                                        .getMessage()
                                        .contains("유한하지 않은")),
                () ->
                        assertTrue(
                                embedFailure(input -> vector(0.1, Double.POSITIVE_INFINITY, 0.3))
                                        .getMessage()
                                        .contains("유한하지 않은")),
                () ->
                        assertTrue(
                                embedFailure(input -> vectorWithNull())
                                        .getMessage()
                                        .contains("유한하지 않은")),
                () ->
                        assertTrue(
                                embedFailure(
                                                input ->
                                                        new OpenAiEmbeddingGateway.Embedding(
                                                                null, MODEL, DIMENSIONS))
                                        .getMessage()
                                        .contains("vector가 비어 있습니다")),
                () -> assertTrue(embedFailure(input -> null).getMessage().contains("응답이 없습니다")));
    }

    @Test
    void 실패_메시지와_로그에_분석_텍스트나_vector_원문을_남기지_않는다(CapturedOutput output) {
        String secret = "비공개 분석 텍스트 원문";
        RecordingGateway gateway =
                new RecordingGateway(
                        input -> new OpenAiEmbeddingGateway.Embedding(List.of(0.5), MODEL, 1));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);
        ValidatedAiRouteContent content =
                content(
                        new ValidatedAiRouteContent.ValidatedBook(
                                41,
                                "도서 41",
                                1,
                                true,
                                true,
                                List.of(
                                        new ValidatedAiRouteContent.ValidatedPage(
                                                2,
                                                true,
                                                secret,
                                                "공개 주제",
                                                60,
                                                List.of())),
                                List.of()));

        AiRouteContentEmbeddingException exception =
                assertThrows(
                        AiRouteContentEmbeddingException.class, () -> service.embed(content, POLICY));

        assertAll(
                () -> assertFalse(exception.getMessage().contains(secret)),
                () -> assertFalse(exception.getMessage().contains("0.5")),
                () -> assertTrue(exception.getMessage().contains("book 41 p2")),
                () -> assertFalse(output.getAll().contains(secret)),
                () -> assertFalse(output.getAll().contains("0.5")),
                () -> assertFalse(output.getAll().contains("book 41 p2")));
    }

    @Test
    void 결과_batch는_불변이다() {
        RecordingGateway gateway = new RecordingGateway(input -> vector(0.1, 0.2, 0.3));
        AiRouteContentEmbeddingService service = new AiRouteContentEmbeddingService(gateway);

        EmbeddedAiRouteContent embedded =
                service.embed(content(book(41, page(2, true))), POLICY);

        assertAll(
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> embedded.vectors().clear()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> embedded.vectorOf(41, 2).add(0.9)));
    }

    @Test
    void 결과_batch는_생성자에_전달한_vector와_분리된_깊은_불변_복사다() {
        EmbeddedAiRouteContent.PageKey key =
                new EmbeddedAiRouteContent.PageKey(41, 2, VERSION);
        List<Double> sourceVector = new ArrayList<>(List.of(0.1, 0.2, 0.3));
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> sourceVectors = new LinkedHashMap<>();
        sourceVectors.put(key, sourceVector);

        EmbeddedAiRouteContent embedded =
                new EmbeddedAiRouteContent(VERSION, MODEL, DIMENSIONS, sourceVectors);
        sourceVector.set(0, 9.9);
        sourceVectors.clear();

        assertAll(
                () -> assertEquals(List.of(0.1, 0.2, 0.3), embedded.vectorOf(41, 2)),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> embedded.vectorOf(41, 2).add(0.9)));
    }

    @Test
    void 결과_batch는_null_key가_있으면_즉시_실패한다() {
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> sourceVectors = new LinkedHashMap<>();
        sourceVectors.put(null, List.of(0.1, 0.2, 0.3));

        assertThrows(
                NullPointerException.class,
                () -> new EmbeddedAiRouteContent(VERSION, MODEL, DIMENSIONS, sourceVectors));
    }

    private AiRouteContentEmbeddingException embedFailure(
            Function<OpenAiEmbeddingGateway.PageAnalysisInput, OpenAiEmbeddingGateway.Embedding>
                    responder) {
        AiRouteContentEmbeddingService service =
                new AiRouteContentEmbeddingService(new RecordingGateway(responder));
        return assertThrows(
                AiRouteContentEmbeddingException.class,
                () -> service.embed(content(book(41, page(2, true))), POLICY));
    }

    private static OpenAiEmbeddingGateway.Embedding vector(double... values) {
        List<Double> vector = new ArrayList<>();
        for (double value : values) {
            vector.add(value);
        }
        return new OpenAiEmbeddingGateway.Embedding(vector, MODEL, DIMENSIONS);
    }

    private static OpenAiEmbeddingGateway.Embedding vectorWithNull() {
        List<Double> vector = new ArrayList<>(List.of(0.1, 0.2, 0.3));
        vector.set(1, null);
        return new OpenAiEmbeddingGateway.Embedding(vector, MODEL, DIMENSIONS);
    }

    private ValidatedAiRouteContent content(ValidatedAiRouteContent.ValidatedBook... books) {
        return new ValidatedAiRouteContent(VERSION, POLICY, MODEL, DIMENSIONS, List.of(books));
    }

    private ValidatedAiRouteContent.ValidatedBook book(
            long bookId, ValidatedAiRouteContent.ValidatedPage... pages) {
        return new ValidatedAiRouteContent.ValidatedBook(
                bookId,
                "도서 " + bookId,
                pages.length,
                true,
                true,
                List.of(pages),
                List.of());
    }

    private ValidatedAiRouteContent.ValidatedBook novel(long bookId) {
        return new ValidatedAiRouteContent.ValidatedBook(
                bookId, "도서 " + bookId, 4, false, false, List.of(), List.of());
    }

    private ValidatedAiRouteContent.ValidatedPage page(int pageNumber, boolean candidate) {
        return new ValidatedAiRouteContent.ValidatedPage(
                pageNumber,
                candidate,
                "p%d 분석".formatted(pageNumber),
                "p%d 공개 주제".formatted(pageNumber),
                60,
                List.of());
    }

    private static final class RecordingGateway implements OpenAiEmbeddingGateway {

        private final Function<PageAnalysisInput, Embedding> responder;
        private final List<String> analysisTexts = new ArrayList<>();
        private final List<String> models = new ArrayList<>();
        private final List<Integer> dimensions = new ArrayList<>();

        private RecordingGateway(Function<PageAnalysisInput, Embedding> responder) {
            this.responder = responder;
        }

        @Override
        public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
            throw new AssertionError("적재는 목적 embedding을 호출하지 않습니다.");
        }

        @Override
        public Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dims) {
            analysisTexts.add(input.aiAnalysisText());
            models.add(model);
            dimensions.add(dims);
            return responder.apply(input);
        }
    }
}
