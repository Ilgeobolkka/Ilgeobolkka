package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiRouteContentImportCommandTest {

    private static final String VERSION = "ai-route-v2";
    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 3;
    private static final long BOOK_ID = 41L;

    @Test
    void 검증을_우회하는_생성자를_노출하지_않는다() {
        assertFalse(
                java.util.Arrays.stream(AiRouteContentImportCommand.class.getDeclaredConstructors())
                        .anyMatch(constructor -> !Modifier.isPrivate(constructor.getModifiers())));
    }

    @Test
    void 변환_검증_embedding의_도서와_페이지가_정확히_맞으면_command를_만든다() {
        assertDoesNotThrow(
                () ->
                        AiRouteContentImportCommand.create(
                                batch(), content(), embedded(Map.of(2, vector(), 3, vector()))));
    }

    @Test
    void 버전_모델_차원이나_페이지_key가_어긋나면_DB_command를_만들지_않는다() {
        assertTrue(
                failure(
                                batch("other-version"),
                                content(),
                                embedded(Map.of(2, vector(), 3, vector())))
                        .getMessage()
                        .contains("콘텐츠 버전"));
        assertTrue(
                failure(
                                batch(),
                                content(),
                                new EmbeddedAiRouteContent(
                                        VERSION,
                                        "other-model",
                                        DIMENSIONS,
                                        vectors(Map.of(2, vector(), 3, vector()))))
                        .getMessage()
                        .contains("모델·차원"));
        assertTrue(
                failure(batch(), content(), embedded(Map.of(2, vector())))
                        .getMessage()
                        .contains("vector가 없습니다"));
        assertTrue(
                failure(
                                batch(),
                                content(),
                                embedded(Map.of(1, vector(), 2, vector(), 3, vector())))
                        .getMessage()
                        .contains("후보가 아닌데 vector"));
    }

    @Test
    void vector의_길이와_유한성을_DB_트랜잭션_전에_검증한다() {
        assertTrue(
                failure(
                                batch(),
                                content(),
                                embedded(Map.of(2, List.of(0.1, 0.2), 3, vector())))
                        .getMessage()
                        .contains("길이"));
        assertTrue(
                failure(
                                batch(),
                                content(),
                                embedded(
                                        Map.of(
                                                2,
                                                List.of(0.1, Double.NaN, 0.3),
                                                3,
                                                vector())))
                        .getMessage()
                        .contains("유한"));
    }

    @Test
    void 후보_도서의_외부_전송_권리가_없으면_DB_command를_만들지_않는다() {
        assertTrue(
                failure(
                                batch(),
                                content(false),
                                embedded(Map.of(2, vector(), 3, vector())))
                        .getMessage()
                        .contains("외부 전송 권리"));
    }

    @Test
    void 변환된_도서나_페이지가_검증_결과와_다르면_DB_command를_만들지_않는다() {
        ContentBatch missingPage =
                new ContentBatch(
                        VERSION,
                        "b".repeat(64),
                        List.of(
                                book(1, 1),
                                new ConvertedBook(
                                        BOOK_ID,
                                        "a".repeat(64),
                                        2,
                                        List.of(page(BOOK_ID, 1), page(BOOK_ID, 2)))));

        assertTrue(
                failure(
                                missingPage,
                                content(),
                                embedded(Map.of(2, vector(), 3, vector())))
                        .getMessage()
                        .contains("페이지"));
    }

    @Test
    void 선수_관계의_페이지_key_자기참조_중복_순환을_DB_트랜잭션_전에_거부한다() {
        Map<Integer, List<Double>> vectors = Map.of(2, vector(), 3, vector());
        var validEdge = new ValidatedAiRouteContent.PrerequisiteEdge(2, 3);

        assertTrue(
                failure(
                                batch(),
                                content(
                                        true,
                                        List.of(
                                                new ValidatedAiRouteContent.PrerequisiteEdge(
                                                        99, 3)),
                                        List.of()),
                                embedded(vectors))
                        .getMessage()
                        .contains("없는 페이지"));
        assertTrue(
                failure(
                                batch(),
                                content(
                                        true,
                                        List.of(
                                                new ValidatedAiRouteContent.PrerequisiteEdge(
                                                        2, 2)),
                                        List.of()),
                                embedded(vectors))
                        .getMessage()
                        .contains("자기 자신"));
        assertTrue(
                failure(
                                batch(),
                                content(true, List.of(validEdge, validEdge), List.of()),
                                embedded(vectors))
                        .getMessage()
                        .contains("중복"));
        assertTrue(
                failure(
                                batch(),
                                content(
                                        true,
                                        List.of(
                                                validEdge,
                                                new ValidatedAiRouteContent.PrerequisiteEdge(
                                                        3, 2)),
                                        List.of()),
                                embedded(vectors))
                        .getMessage()
                        .contains("순환"));
    }

    @Test
    void 비후보_도서의_선수_관계를_DB_트랜잭션_전에_거부한다() {
        ValidatedAiRouteContent invalid =
                content(
                        true,
                        List.of(new ValidatedAiRouteContent.PrerequisiteEdge(2, 3)),
                        List.of(new ValidatedAiRouteContent.PrerequisiteEdge(1, 2)));

        assertTrue(
                failure(
                                batch(),
                                invalid,
                                embedded(Map.of(2, vector(), 3, vector())))
                        .getMessage()
                        .contains("비후보 book"));
    }

    private AiRouteContentImportException failure(
            ContentBatch batch,
            ValidatedAiRouteContent content,
            EmbeddedAiRouteContent embedded) {
        return assertThrows(
                AiRouteContentImportException.class,
                () -> AiRouteContentImportCommand.create(batch, content, embedded));
    }

    private ContentBatch batch() {
        return batch(VERSION);
    }

    private ContentBatch batch(String version) {
        return new ContentBatch(
                version,
                "b".repeat(64),
                List.of(book(1, 1), book(BOOK_ID, 3)));
    }

    private ConvertedBook book(long bookId, int pageCount) {
        java.util.ArrayList<ConvertedPage> pages = new java.util.ArrayList<>();
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            pages.add(page(bookId, pageNumber));
        }
        return new ConvertedBook(
                bookId, "a".repeat(64), pageCount, List.copyOf(pages));
    }

    private ConvertedPage page(long bookId, int pageNumber) {
        return new ConvertedPage(
                bookId,
                pageNumber,
                BookPageContentType.TEXT,
                "본문 " + pageNumber,
                null,
                null);
    }

    private ValidatedAiRouteContent content() {
        return content(true);
    }

    private ValidatedAiRouteContent content(boolean externalTransferAllowed) {
        return content(
                externalTransferAllowed,
                List.of(new ValidatedAiRouteContent.PrerequisiteEdge(2, 3)),
                List.of());
    }

    private ValidatedAiRouteContent content(
            boolean externalTransferAllowed,
            List<ValidatedAiRouteContent.PrerequisiteEdge> candidateEdges,
            List<ValidatedAiRouteContent.PrerequisiteEdge> novelEdges) {
        return new ValidatedAiRouteContent(
                VERSION,
                POLICY,
                MODEL,
                DIMENSIONS,
                List.of(
                        new ValidatedAiRouteContent.ValidatedBook(
                                1, "소설", 1, false, false, List.of(), novelEdges),
                        new ValidatedAiRouteContent.ValidatedBook(
                                BOOK_ID,
                                "새 제목",
                                3,
                                true,
                                externalTransferAllowed,
                                List.of(pageMetadata(1, false), pageMetadata(2, true), pageMetadata(3, true)),
                                candidateEdges)));
    }

    private ValidatedAiRouteContent.ValidatedPage pageMetadata(
            int pageNumber, boolean candidate) {
        return new ValidatedAiRouteContent.ValidatedPage(
                pageNumber,
                candidate,
                "분석 " + pageNumber,
                "공개 주제 " + pageNumber,
                60,
                List.of());
    }

    private EmbeddedAiRouteContent embedded(Map<Integer, List<Double>> vectorsByPage) {
        return new EmbeddedAiRouteContent(
                VERSION, MODEL, DIMENSIONS, vectors(vectorsByPage));
    }

    private Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors(
            Map<Integer, List<Double>> vectorsByPage) {
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors = new LinkedHashMap<>();
        vectorsByPage.forEach(
                (pageNumber, vector) ->
                        vectors.put(
                                new EmbeddedAiRouteContent.PageKey(
                                        BOOK_ID, pageNumber, VERSION),
                                vector));
        return vectors;
    }

    private List<Double> vector() {
        return List.of(0.1, 0.2, 0.3);
    }
}
