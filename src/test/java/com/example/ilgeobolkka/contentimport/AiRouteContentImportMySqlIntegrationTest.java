package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private static final long BOOK_ID = 61_000L;
    private static final long NOVEL_ID = 61_001L;
    private static final long FIRST_PAGE_ID = 62_000L;

    private final AiRouteContentWriter writer;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AiRouteContentImportMySqlIntegrationTest(
            AiRouteContentWriter writer, JdbcTemplate jdbcTemplate) {
        this.writer = writer;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void 도서와_본문_페이지를_준비한다() {
        테스트_도서를_지운다();
        도서를_만든다(BOOK_ID, 3);
        도서를_만든다(NOVEL_ID, 1);
        for (int pageNumber = 1; pageNumber <= 3; pageNumber++) {
            페이지를_만든다(FIRST_PAGE_ID + pageNumber, BOOK_ID, pageNumber);
        }
        페이지를_만든다(FIRST_PAGE_ID + 10, NOVEL_ID, 1);
    }

    /** 남겨 두면 도서 목록·핵심 여정 테스트가 예상보다 많은 도서를 보게 된다. */
    @AfterEach
    void 테스트_도서를_정리한다() {
        테스트_도서를_지운다();
    }

    private void 테스트_도서를_지운다() {
        jdbcTemplate.update(
                "DELETE FROM ai_route_prerequisite WHERE book_id IN (?, ?)", BOOK_ID, NOVEL_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id IN (?, ?)", BOOK_ID, NOVEL_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", BOOK_ID, NOVEL_ID);
    }

    @Test
    void 목차는_후보_제외로_본문은_vector와_함께_한_번에_저장한다() {
        writer.write(content(), embedded(Map.of(2, vector(), 3, vector())));

        Map<Integer, Map<String, Object>> pages = 저장된_페이지들();
        assertAll(
                () -> assertEquals(false, pages.get(1).get("ai_route_candidate")),
                () -> assertNull(pages.get(1).get("embedding_model")),
                () -> assertNull(pages.get(1).get("embedding_json")),
                () -> assertEquals("p1 분석", pages.get(1).get("ai_analysis_text")),
                () -> assertEquals(40, pages.get(1).get("estimated_reading_seconds")),
                () -> assertEquals(true, pages.get(2).get("ai_route_candidate")),
                () -> assertEquals(MODEL, pages.get(2).get("embedding_model")),
                () -> assertEquals(DIMENSIONS, pages.get(2).get("embedding_dimensions")),
                () ->
                        // MySQL이 JSON을 공백을 넣어 돌려주므로 공백을 지우고 비교한다.
                        assertEquals(
                                "[0.1,0.2,0.3]",
                                pages.get(2).get("embedding_json").toString().replace(" ", "")));
    }

    @Test
    void 같은_contentVersion이_이미_적재됐는지_판정한다() {
        assertFalse(writer.alreadyImported(VERSION));

        writer.write(content(), embedded(Map.of(2, vector(), 3, vector())));

        assertTrue(writer.alreadyImported(VERSION));
    }

    @Test
    void 도서_메타데이터와_선수_관계를_저장하고_지원은_false로_둔다() {
        writer.write(content(), embedded(Map.of(2, vector(), 3, vector())));

        assertAll(
                () ->
                        assertEquals(
                                VERSION,
                                jdbcTemplate.queryForObject(
                                        "SELECT content_version FROM book WHERE id = ?",
                                        String.class,
                                        BOOK_ID)),
                () ->
                        assertFalse(
                                jdbcTemplate.queryForObject(
                                        "SELECT ai_route_supported FROM book WHERE id = ?",
                                        Boolean.class,
                                        BOOK_ID)),
                () ->
                        assertTrue(
                                jdbcTemplate.queryForObject(
                                        "SELECT ai_external_transfer_allowed FROM book WHERE id = ?",
                                        Boolean.class,
                                        BOOK_ID)),
                // 소설은 후보가 아니므로 외부 전송을 허용하지 않고 페이지도 건드리지 않는다.
                () ->
                        assertFalse(
                                jdbcTemplate.queryForObject(
                                        "SELECT ai_external_transfer_allowed FROM book WHERE id = ?",
                                        Boolean.class,
                                        NOVEL_ID)),
                () ->
                        assertEquals(
                                List.of("2-3"),
                                jdbcTemplate.queryForList(
                                        """
                                        SELECT CONCAT(prerequisite_page_number, '-',
                                                      dependent_page_number)
                                        FROM ai_route_prerequisite WHERE book_id = ?
                                        """,
                                        String.class,
                                        BOOK_ID)));
    }

    @Test
    void 기존_페이지_ID는_보존된다() {
        writer.write(content(), embedded(Map.of(2, vector(), 3, vector())));

        assertEquals(
                List.of(FIRST_PAGE_ID + 1, FIRST_PAGE_ID + 2, FIRST_PAGE_ID + 3),
                jdbcTemplate.queryForList(
                        "SELECT id FROM book_page WHERE book_id = ? ORDER BY page_number",
                        Long.class,
                        BOOK_ID));
    }

    @Test
    void 후보와_vector가_어긋나면_트랜잭션을_열기_전에_실패한다() {
        assertAll(
                () ->
                        assertTrue(
                                실패(embedded(Map.of(2, vector())))
                                        .getMessage()
                                        .contains("후보인데 vector가 없습니다")),
                () ->
                        assertTrue(
                                실패(embedded(Map.of(1, vector(), 2, vector(), 3, vector())))
                                        .getMessage()
                                        .contains("후보가 아닌데 vector가 있습니다")),
                () -> assertTrue(DB가_그대로다()));
    }

    @Test
    void 본문_행이_없으면_전체가_rollback된다() {
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ? AND page_number = 3", BOOK_ID);

        AiRouteContentImportException exception =
                assertThrows(
                        AiRouteContentImportException.class,
                        () -> writer.write(content(), embedded(Map.of(2, vector(), 3, vector()))));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("본문 적재가 먼저")),
                // 앞선 book UPDATE까지 되돌아가야 한다.
                () ->
                        assertNull(
                                jdbcTemplate.queryForObject(
                                        "SELECT ai_data_policy_version FROM book WHERE id = ?",
                                        String.class,
                                        BOOK_ID)),
                () ->
                        assertEquals(
                                0,
                                jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM ai_route_prerequisite WHERE book_id = ?",
                                        Integer.class,
                                        BOOK_ID)));
    }

    private AiRouteContentImportException 실패(EmbeddedAiRouteContent embedded) {
        return assertThrows(
                AiRouteContentImportException.class, () -> writer.write(content(), embedded));
    }

    private boolean DB가_그대로다() {
        return jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book_page WHERE book_id = ? AND ai_route_candidate = 1",
                        Integer.class,
                        BOOK_ID)
                == 0;  // 후보로 올라간 행이 하나도 없어야 한다
    }

    private Map<Integer, Map<String, Object>> 저장된_페이지들() {
        Map<Integer, Map<String, Object>> pages = new HashMap<>();
        jdbcTemplate
                .queryForList("SELECT * FROM book_page WHERE book_id = ?", BOOK_ID)
                .forEach(row -> pages.put((Integer) row.get("page_number"), row));
        return pages;
    }

    private ValidatedAiRouteContent content() {
        return new ValidatedAiRouteContent(
                VERSION,
                POLICY,
                MODEL,
                DIMENSIONS,
                List.of(
                        new ValidatedAiRouteContent.ValidatedBook(
                                BOOK_ID,
                                true,
                                List.of(
                                        page(1, false, 40),
                                        page(2, true, 60),
                                        page(3, true, 60)),
                                List.of(new ValidatedAiRouteContent.PrerequisiteEdge(2, 3))),
                        new ValidatedAiRouteContent.ValidatedBook(
                                NOVEL_ID, false, List.of(), List.of())));
    }

    private ValidatedAiRouteContent.ValidatedPage page(
            int pageNumber, boolean candidate, int readingSeconds) {
        return new ValidatedAiRouteContent.ValidatedPage(
                pageNumber,
                candidate,
                "p%d 분석".formatted(pageNumber),
                "p%d 주제".formatted(pageNumber),
                readingSeconds,
                List.of());
    }

    private EmbeddedAiRouteContent embedded(Map<Integer, List<Double>> vectorsByPage) {
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors = new HashMap<>();
        vectorsByPage.forEach(
                (pageNumber, vector) ->
                        vectors.put(
                                new EmbeddedAiRouteContent.PageKey(BOOK_ID, pageNumber, VERSION),
                                vector));
        return new EmbeddedAiRouteContent(VERSION, MODEL, DIMENSIONS, vectors);
    }

    private List<Double> vector() {
        return List.of(0.1, 0.2, 0.3);
    }

    private void 도서를_만든다(long bookId, int totalPageCount) {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '개발', ?, '저자', ?, 10000)
                """,
                bookId,
                "도서-" + bookId,
                totalPageCount);
    }

    private void 페이지를_만든다(long pageId, long bookId, int pageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, ?, 'TEXT', '본문', NULL)
                """,
                pageId,
                bookId,
                pageNumber);
    }
}
