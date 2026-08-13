package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.demo.DemoBookCatalog;
import com.example.ilgeobolkka.demo.DemoBookWriter;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class AiRouteContentImportMySqlIntegrationTest {

    private static final String VERSION = "ai-route-v2";
    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 3;
    private static final long NOVEL_ID = 1L;
    private static final long BOOK_ID = 41L;
    private static final long EXISTING_PAGE_ID = 62_000L;

    private final ContentPageWriter writer;
    private final DemoBookCatalog demoBookCatalog;
    private final DemoBookWriter demoBookWriter;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AiRouteContentImportMySqlIntegrationTest(
            ContentPageWriter writer,
            DemoBookCatalog demoBookCatalog,
            DemoBookWriter demoBookWriter,
            JdbcTemplate jdbcTemplate) {
        this.writer = writer;
        this.demoBookCatalog = demoBookCatalog;
        this.demoBookWriter = demoBookWriter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void 도서와_기존_페이지를_준비한다() {
        테스트_콘텐츠를_지운다();
        demoBookWriter.ensureBooks(demoBookCatalog.books());
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 1, 'TEXT', '기존 본문', NULL)
                """,
                EXISTING_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path,
                     ai_analysis_text, ai_public_guide_topic, estimated_reading_seconds,
                     embedding_model, embedding_dimensions, embedding_json,
                     duplicate_group_keys, ai_route_candidate)
                VALUES (?, ?, 1, 'TEXT', '기존 소설 본문', NULL,
                        '지울 분석', '지울 주제', 60,
                        'old-model', 3, JSON_ARRAY(0.1, 0.2, 0.3),
                        JSON_ARRAY(), TRUE)
                """,
                EXISTING_PAGE_ID + 10,
                NOVEL_ID);
    }

    @AfterEach
    void 테스트_콘텐츠를_정리한다() {
        테스트_콘텐츠를_지운다();
    }

    @Test
    void Book_BookPage_선수_관계를_한번에_적재하고_기존_페이지_ID와_지원_false를_보존한다() {
        jdbcTemplate.update(
                """
                UPDATE book
                SET ai_external_transfer_allowed = TRUE,
                    ai_data_policy_version = 'OLD_POLICY',
                    ai_route_supported = TRUE
                WHERE id = ?
                """,
                BOOK_ID);

        writer.write(command());

        Map<String, Object> book =
                jdbcTemplate.queryForMap("SELECT * FROM book WHERE id = ?", BOOK_ID);
        Map<String, Object> novelBook =
                jdbcTemplate.queryForMap("SELECT * FROM book WHERE id = ?", NOVEL_ID);
        Map<Integer, Map<String, Object>> pages = 저장된_페이지들(BOOK_ID);
        Map<String, Object> novelPage = 저장된_페이지들(NOVEL_ID).get(1);
        assertAll(
                () -> assertEquals("새 AI 도서 제목", book.get("title")),
                () -> assertEquals(3, book.get("total_page_count")),
                () -> assertEquals(VERSION, book.get("content_version")),
                () -> assertEquals(true, book.get("ai_external_transfer_allowed")),
                () -> assertEquals(POLICY, book.get("ai_data_policy_version")),
                () -> assertEquals(false, book.get("ai_route_supported")),
                () -> assertEquals(EXISTING_PAGE_ID, pages.get(1).get("id")),
                () -> assertEquals("새 본문 1", pages.get(1).get("text_content")),
                () -> assertEquals(false, pages.get(1).get("ai_route_candidate")),
                () -> assertEquals("분석 1", pages.get(1).get("ai_analysis_text")),
                () -> assertEquals("공개 주제 1", pages.get(1).get("ai_public_guide_topic")),
                () -> assertEquals(40, pages.get(1).get("estimated_reading_seconds")),
                () -> assertNull(pages.get(1).get("embedding_model")),
                () -> assertNull(pages.get(1).get("embedding_dimensions")),
                () -> assertNull(pages.get(1).get("embedding_json")),
                () -> assertEquals("[]", pages.get(1).get("duplicate_group_keys").toString()),
                () -> assertEquals("IMAGE", pages.get(2).get("content_type")),
                () -> assertNull(pages.get(2).get("text_content")),
                () -> assertEquals("ai/book-41/page-2.jpg", pages.get(2).get("image_path")),
                () -> assertEquals(true, pages.get(2).get("ai_route_candidate")),
                () -> assertEquals(MODEL, pages.get(2).get("embedding_model")),
                () -> assertEquals(DIMENSIONS, pages.get(2).get("embedding_dimensions")),
                () ->
                        assertEquals(
                                "[0.1,0.2,0.3]",
                                pages.get(2)
                                        .get("embedding_json")
                                        .toString()
                                        .replace(" ", "")),
                () ->
                        assertEquals(
                                "[\"same-concept\"]",
                                pages.get(2)
                                        .get("duplicate_group_keys")
                                        .toString()
                                        .replace(" ", "")),
                () ->
                        assertEquals(
                                List.of("2-3"),
                                jdbcTemplate.queryForList(
                                        """
                                        SELECT CONCAT(prerequisite_page_number, '-',
                                                      dependent_page_number)
                                        FROM ai_route_prerequisite
                                        WHERE book_id = ?
                                        """,
                                        String.class,
                                        BOOK_ID)),
                () -> assertFalse(도서_지원여부(NOVEL_ID)),
                () -> assertEquals(VERSION, 도서_콘텐츠버전(NOVEL_ID)),
                () -> assertEquals(false, novelBook.get("ai_external_transfer_allowed")),
                () -> assertEquals(POLICY, novelBook.get("ai_data_policy_version")),
                () -> assertEquals(4, 저장된_페이지들(NOVEL_ID).size()),
                () -> assertEquals(false, novelPage.get("ai_route_candidate")),
                () -> assertNull(novelPage.get("ai_analysis_text")),
                () -> assertNull(novelPage.get("ai_public_guide_topic")),
                () -> assertNull(novelPage.get("estimated_reading_seconds")),
                () -> assertNull(novelPage.get("embedding_model")),
                () -> assertNull(novelPage.get("embedding_dimensions")),
                () -> assertNull(novelPage.get("embedding_json")),
                () -> assertNull(novelPage.get("duplicate_group_keys")));
    }

    @Test
    void 같은_version을_재적재하면_페이지_ID를_보존하고_선수_관계를_교체한다() {
        writer.write(command());
        Long secondPageId = 페이지_ID(BOOK_ID, 2);
        jdbcTemplate.update(
                """
                DELETE FROM ai_route_prerequisite WHERE book_id = ?
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_prerequisite
                    (book_id, prerequisite_page_number, dependent_page_number)
                VALUES (?, 3, 2)
                """,
                BOOK_ID);

        writer.write(command());

        assertAll(
                () -> assertEquals(secondPageId, 페이지_ID(BOOK_ID, 2)),
                () ->
                        assertEquals(
                                List.of("2-3"),
                                jdbcTemplate.queryForList(
                                        """
                                        SELECT CONCAT(prerequisite_page_number, '-',
                                                      dependent_page_number)
                                        FROM ai_route_prerequisite
                                        WHERE book_id = ?
                                        """,
                                        String.class,
                                        BOOK_ID)));
    }

    @Test
    void manifest_도서가_없으면_동결된_demo_메타데이터로_만든_뒤_v2_값을_적재한다() {
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id IN (?, ?)", NOVEL_ID, BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", NOVEL_ID, BOOK_ID);

        writer.write(command());

        assertAll(
                () -> assertEquals(VERSION, 도서_콘텐츠버전(NOVEL_ID)),
                () -> assertEquals(VERSION, 도서_콘텐츠버전(BOOK_ID)),
                () -> assertEquals(4, 저장된_페이지들(NOVEL_ID).size()),
                () -> assertEquals(3, 저장된_페이지들(BOOK_ID).size()));
    }

    @Test
    void 기존_도서_ID의_불변_메타데이터가_충돌하면_적재하지_않는다() {
        jdbcTemplate.update("UPDATE book SET author = '다른 저자' WHERE id = ?", BOOK_ID);

        assertThrows(AiRouteContentImportException.class, () -> writer.write(command()));

        assertAll(
                () -> assertEquals("initial-v1", 도서_콘텐츠버전(BOOK_ID)),
                () -> assertEquals("기존 본문", 페이지_본문(BOOK_ID, 1)),
                () -> assertEquals(0, 선수관계_수()));
    }

    @Test
    void 예상하지_않은_기존_페이지가_있으면_DB를_변경하지_않는다() {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES (?, 4, 'TEXT', '보존할 본문', NULL)
                """,
                BOOK_ID);

        assertThrows(IllegalStateException.class, () -> writer.write(command()));

        assertAll(
                () -> assertEquals("initial-v1", 도서_콘텐츠버전(BOOK_ID)),
                () -> assertEquals("기존 본문", 페이지_본문(BOOK_ID, 1)),
                () -> assertEquals("보존할 본문", 페이지_본문(BOOK_ID, 4)),
                () -> assertEquals(0, 선수관계_수()));
    }

    @Test
    void 중간_페이지_writer가_실패하면_도서와_앞선_페이지_변경도_rollback한다() {
        AiRouteContentImportCommand invalid =
                AiRouteContentImportCommand.create(
                        batchWithTooLongImagePath(),
                        content(List.of(new ValidatedAiRouteContent.PrerequisiteEdge(2, 3))),
                        embedded());

        assertThrows(DataIntegrityViolationException.class, () -> writer.write(invalid));

        assertAll(
                () -> assertEquals("initial-v1", 도서_콘텐츠버전(BOOK_ID)),
                () -> assertEquals("기존 본문", 페이지_본문(BOOK_ID, 1)),
                () -> assertEquals(1, 저장된_페이지들(BOOK_ID).size()),
                () -> assertEquals(0, 선수관계_수()));
    }

    @Test
    void 잘못된_선수_관계는_transaction_전에_거부해_DB를_변경하지_않는다() {
        assertThrows(
                AiRouteContentImportException.class,
                () ->
                        AiRouteContentImportCommand.create(
                                batch(),
                                content(
                                        List.of(
                                                new ValidatedAiRouteContent.PrerequisiteEdge(
                                                        99, 3))),
                                embedded()));

        assertAll(
                () -> assertEquals("initial-v1", 도서_콘텐츠버전(BOOK_ID)),
                () -> assertEquals("기존 본문", 페이지_본문(BOOK_ID, 1)),
                () -> assertEquals(1, 저장된_페이지들(BOOK_ID).size()),
                () -> assertEquals(0, 선수관계_수()));
    }

    private AiRouteContentImportCommand command() {
        return AiRouteContentImportCommand.create(
                batch(),
                content(List.of(new ValidatedAiRouteContent.PrerequisiteEdge(2, 3))),
                embedded());
    }

    private ContentBatch batch() {
        return new ContentBatch(
                VERSION,
                "b".repeat(64),
                List.of(
                        convertedBook(NOVEL_ID, 4),
                        convertedBook(BOOK_ID, 3)));
    }

    private ContentBatch batchWithTooLongImagePath() {
        ContentBatch valid = batch();
        List<ConvertedBook> books = new ArrayList<>(valid.books());
        ConvertedBook candidate = books.getLast();
        List<ConvertedPage> pages = new ArrayList<>(candidate.pages());
        pages.set(
                2,
                new ConvertedPage(
                        BOOK_ID,
                        3,
                        BookPageContentType.IMAGE,
                        null,
                        "x".repeat(501),
                        10L));
        books.set(
                books.size() - 1,
                new ConvertedBook(
                        BOOK_ID,
                        candidate.sourceSha256(),
                        candidate.totalPageCount(),
                        List.copyOf(pages)));
        return new ContentBatch(VERSION, valid.manifestSha256(), List.copyOf(books));
    }

    private ConvertedBook convertedBook(long bookId, int pageCount) {
        List<ConvertedPage> pages = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            if (bookId == BOOK_ID && pageNumber == 2) {
                pages.add(
                        new ConvertedPage(
                                bookId,
                                pageNumber,
                                BookPageContentType.IMAGE,
                                null,
                                "ai/book-41/page-2.jpg",
                                10L));
                continue;
            }
            pages.add(
                    new ConvertedPage(
                            bookId,
                            pageNumber,
                            BookPageContentType.TEXT,
                            "새 본문 " + pageNumber,
                            null,
                            null));
        }
        return new ConvertedBook(
                bookId, "a".repeat(64), pageCount, List.copyOf(pages));
    }

    private ValidatedAiRouteContent content(
            List<ValidatedAiRouteContent.PrerequisiteEdge> edges) {
        DemoBookCatalog.BookSeed novel = 도서_시드(NOVEL_ID);
        return new ValidatedAiRouteContent(
                VERSION,
                POLICY,
                MODEL,
                DIMENSIONS,
                List.of(
                        new ValidatedAiRouteContent.ValidatedBook(
                                NOVEL_ID,
                                novel.title(),
                                4,
                                false,
                                false,
                                List.of(),
                                List.of()),
                        new ValidatedAiRouteContent.ValidatedBook(
                                BOOK_ID,
                                "새 AI 도서 제목",
                                3,
                                true,
                                true,
                                List.of(
                                        pageMetadata(1, false, 40),
                                        pageMetadata(2, true, 60),
                                        pageMetadata(3, true, 65)),
                                edges)));
    }

    private ValidatedAiRouteContent.ValidatedPage pageMetadata(
            int pageNumber, boolean candidate, int readingSeconds) {
        return new ValidatedAiRouteContent.ValidatedPage(
                pageNumber,
                candidate,
                "분석 " + pageNumber,
                "공개 주제 " + pageNumber,
                readingSeconds,
                candidate ? List.of("same-concept") : List.of());
    }

    private EmbeddedAiRouteContent embedded() {
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors = new LinkedHashMap<>();
        vectors.put(new EmbeddedAiRouteContent.PageKey(BOOK_ID, 2, VERSION), vector());
        vectors.put(new EmbeddedAiRouteContent.PageKey(BOOK_ID, 3, VERSION), vector());
        return new EmbeddedAiRouteContent(VERSION, MODEL, DIMENSIONS, vectors);
    }

    private List<Double> vector() {
        return List.of(0.1, 0.2, 0.3);
    }

    private DemoBookCatalog.BookSeed 도서_시드(long bookId) {
        return demoBookCatalog.books().stream()
                .filter(book -> book.id() == bookId)
                .findFirst()
                .orElseThrow();
    }

    private Map<Integer, Map<String, Object>> 저장된_페이지들(long bookId) {
        Map<Integer, Map<String, Object>> pages = new LinkedHashMap<>();
        jdbcTemplate
                .queryForList(
                        "SELECT * FROM book_page WHERE book_id = ? ORDER BY page_number",
                        bookId)
                .forEach(row -> pages.put((Integer) row.get("page_number"), row));
        return pages;
    }

    private Long 페이지_ID(long bookId, int pageNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM book_page WHERE book_id = ? AND page_number = ?",
                Long.class,
                bookId,
                pageNumber);
    }

    private String 페이지_본문(long bookId, int pageNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT text_content FROM book_page WHERE book_id = ? AND page_number = ?",
                String.class,
                bookId,
                pageNumber);
    }

    private String 도서_콘텐츠버전(long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT content_version FROM book WHERE id = ?", String.class, bookId);
    }

    private boolean 도서_지원여부(long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT ai_route_supported FROM book WHERE id = ?", Boolean.class, bookId);
    }

    private int 선수관계_수() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_prerequisite WHERE book_id = ?",
                Integer.class,
                BOOK_ID);
    }

    private void 테스트_콘텐츠를_지운다() {
        jdbcTemplate.update("DELETE FROM ai_route_prerequisite WHERE book_id BETWEEN 1 AND 100");
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id BETWEEN 1 AND 100");
        jdbcTemplate.update("DELETE FROM book WHERE id BETWEEN 1 AND 100");
    }
}
