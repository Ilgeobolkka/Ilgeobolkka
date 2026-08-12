package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * `ai-route-v2` 본문 적재 경로.
 *
 * <p>초기 fixture와 달리 권수를 세지 않고 manifest에 든 도서만 적재한다. 본문이 길어지므로
 * `total_page_count`가 올라가고 기존 페이지 행은 ID를 보존한 채 갱신된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class AiRoutePageWriterMySqlIntegrationTest {

    private static final long BOOK_ID = 11L;
    private static final int NEW_PAGE_COUNT = 5;
    private static final long OTHER_BOOK_ID = 11_001L;

    private final ContentPageWriter pageWriter;
    private final JdbcTemplate jdbcTemplate;

    private int originalPageCount;
    private List<Long> originalPageIds;

    @Autowired
    AiRoutePageWriterMySqlIntegrationTest(
            ContentPageWriter pageWriter, JdbcTemplate jdbcTemplate) {
        this.pageWriter = pageWriter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void 초기_상태를_기록한다() {
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        // 도서는 앞선 시연 데이터 단계에서 만들어져 있어야 한다. 적재는 만들지 않는다.
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '에세이', '적재 대상 도서', '저자', 3, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (book_id, page_number, content_type, text_content)
                VALUES (?, 1, 'TEXT', '기존 본문')
                """,
                BOOK_ID);
        // manifest에 들지 않은 도서. 적재가 건드리지 않아야 한다.
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '에세이', '대상 아닌 도서', '저자', 1, 10000)
                """,
                OTHER_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (book_id, page_number, content_type, text_content)
                VALUES (?, 1, 'TEXT', '남아 있어야 하는 본문')
                """,
                OTHER_BOOK_ID);
        originalPageCount =
                jdbcTemplate.queryForObject(
                        "SELECT total_page_count FROM book WHERE id = ?", Integer.class, BOOK_ID);
        originalPageIds = 페이지_ID들();
    }

    @AfterEach
    void 초기_상태로_되돌린다() {
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
    }

    @Test
    void manifest에_든_도서만_적재하고_페이지_수를_올린다() {
        pageWriter.write(batch(NEW_PAGE_COUNT));

        assertAll(
                () ->
                        assertEquals(
                                NEW_PAGE_COUNT,
                                jdbcTemplate.queryForObject(
                                        "SELECT total_page_count FROM book WHERE id = ?",
                                        Integer.class,
                                        BOOK_ID)),
                () ->
                        assertEquals(
                                NEW_PAGE_COUNT,
                                jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM book_page WHERE book_id = ?",
                                        Integer.class,
                                        BOOK_ID)),
                // manifest에 없는 도서의 페이지는 건드리지 않는다.
                () ->
                        assertEquals(
                                1,
                                jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM book_page WHERE book_id = ?",
                                        Integer.class,
                                        OTHER_BOOK_ID)));
    }

    @Test
    void 기존_페이지_행의_ID를_보존한다() {
        pageWriter.write(batch(NEW_PAGE_COUNT));

        assertEquals(originalPageIds, 페이지_ID들().subList(0, originalPageIds.size()));
    }

    @Test
    void 변환_페이지_수가_manifest와_다르면_적재하지_않는다() {
        ContentBatch mismatched =
                new ContentBatch(
                        "ai-route-v2",
                        "a".repeat(64),
                        List.of(new ConvertedBook(BOOK_ID, "b".repeat(64), 9, pages(2))));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> pageWriter.write(mismatched));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("변환 페이지 수가 manifest와")),
                () ->
                        assertEquals(
                                originalPageCount,
                                jdbcTemplate.queryForObject(
                                        "SELECT total_page_count FROM book WHERE id = ?",
                                        Integer.class,
                                        BOOK_ID)));
    }

    private List<Long> 페이지_ID들() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM book_page WHERE book_id = ? ORDER BY page_number",
                Long.class,
                BOOK_ID);
    }

    private ContentBatch batch(int pageCount) {
        return new ContentBatch(
                "ai-route-v2",
                "a".repeat(64),
                List.of(new ConvertedBook(BOOK_ID, "b".repeat(64), pageCount, pages(pageCount))));
    }

    private List<ConvertedPage> pages(int pageCount) {
        List<ConvertedPage> pages = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            pages.add(
                    new ConvertedPage(
                            BOOK_ID,
                            pageNumber,
                            BookPageContentType.TEXT,
                            "p%d 본문".formatted(pageNumber),
                            null,
                            null));
        }
        return pages;
    }
}
