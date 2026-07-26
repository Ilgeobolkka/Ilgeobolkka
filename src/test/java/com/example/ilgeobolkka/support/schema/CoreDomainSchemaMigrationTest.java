package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class CoreDomainSchemaMigrationTest {

    private static final long READER_ID = 1_000L;
    private static final long SECOND_READER_ID = 1_001L;
    private static final long BOOK_ID = 2_000L;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CoreDomainSchemaMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void V1_마이그레이션이_현재_ERD의_11개_테이블을_생성한다() {
        List<String> tableNames =
                jdbcTemplate.queryForList(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'reader', 'book', 'book_page', 'reading_session',
                              'ink_account', 'ink_purchase', 'ink_ledger', 'page_rental',
                              'ownership_payment', 'book_ownership', 'library_entry'
                          )
                        ORDER BY table_name
                        """,
                        String.class);

        assertEquals(
                List.of(
                        "book",
                        "book_ownership",
                        "book_page",
                        "ink_account",
                        "ink_ledger",
                        "ink_purchase",
                        "library_entry",
                        "ownership_payment",
                        "page_rental",
                        "reader",
                        "reading_session"),
                tableNames);
    }

    @Test
    void 시간_경계와_이력에_쓰는_11개_컬럼은_마이크로초_정밀도다() {
        Long preciseColumnCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND datetime_precision = 6
                          AND CONCAT(table_name, '.', column_name) IN (
                              'reader.created_at',
                              'reading_session.updated_at',
                              'ink_purchase.created_at',
                              'ink_purchase.paid_at',
                              'ink_ledger.occurred_at',
                              'page_rental.rented_at',
                              'page_rental.expires_at',
                              'ownership_payment.created_at',
                              'ownership_payment.paid_at',
                              'book_ownership.created_at',
                              'library_entry.updated_at'
                          )
                        """,
                        Long.class);

        assertEquals(11L, preciseColumnCount);
    }

    @Test
    void 도서_제목과_저자는_영문_대소문자를_구분하지_않는_collation을_사용한다() {
        List<String> collations =
                jdbcTemplate.queryForList(
                        """
                        SELECT collation_name
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'book'
                          AND column_name IN ('title', 'author')
                        ORDER BY column_name
                        """,
                        String.class);

        assertEquals(List.of("utf8mb4_0900_ai_ci", "utf8mb4_0900_ai_ci"), collations);
    }

    @Test
    void 도서는_카테고리와_양수인_페이지_수와_원가를_가져야_한다() {
        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 도서를_생성한다(BOOK_ID, "   ", 100, 10_000)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 도서를_생성한다(BOOK_ID + 1, "소설", 0, 10_000)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 도서를_생성한다(BOOK_ID + 2, "소설", 100, 0)));
    }

    @Test
    void 도서_페이지는_TEXT와_IMAGE_중_한_형식만_저장한다() {
        도서를_생성한다(BOOK_ID, "소설", 5, 10_000);

        assertAll(
                () ->
                        assertDoesNotThrow(
                                () -> 텍스트_페이지를_생성한다(3_000L, BOOK_ID, 1)),
                () ->
                        assertDoesNotThrow(
                                () -> 이미지_페이지를_생성한다(3_001L, BOOK_ID, 2)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO book_page
                                                    (id, book_id, page_number, content_type,
                                                     text_content, image_path)
                                                VALUES (?, ?, 3, 'TEXT', '본문', '/pages/3.png')
                                                """,
                                                3_002L,
                                                BOOK_ID)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO book_page
                                                    (id, book_id, page_number, content_type,
                                                     text_content, image_path)
                                                VALUES (?, ?, 4, 'AUDIO', NULL, NULL)
                                                """,
                                                3_003L,
                                                BOOK_ID)));
    }

    @Test
    void 같은_도서에_같은_원본_페이지_번호를_중복_저장할_수_없다() {
        도서를_생성한다(BOOK_ID, "소설", 5, 10_000);
        텍스트_페이지를_생성한다(3_000L, BOOK_ID, 1);

        assertThrows(
                DataAccessException.class,
                () -> 이미지_페이지를_생성한다(3_001L, BOOK_ID, 1));
    }

    @Test
    void 열람_세션은_독자와_뷰어_세션_ID가_각각_고유하고_실제_도서_페이지를_가리킨다() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(SECOND_READER_ID);
        도서를_생성한다(BOOK_ID, "소설", 5, 10_000);
        텍스트_페이지를_생성한다(3_000L, BOOK_ID, 1);
        열람_세션을_생성한다(
                4_000L, READER_ID, BOOK_ID, 1, "00000000-0000-0000-0000-000000000001");

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        열람_세션을_생성한다(
                                                4_001L,
                                                READER_ID,
                                                BOOK_ID,
                                                1,
                                                "00000000-0000-0000-0000-000000000002")),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        열람_세션을_생성한다(
                                                4_002L,
                                                SECOND_READER_ID,
                                                BOOK_ID,
                                                1,
                                                "00000000-0000-0000-0000-000000000001")),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        열람_세션을_생성한다(
                                                4_003L,
                                                SECOND_READER_ID,
                                                BOOK_ID,
                                                2,
                                                "00000000-0000-0000-0000-000000000003")));
    }

    @Test
    void 서재는_독자와_도서별_한_행이며_실제_도서_페이지를_마지막_위치로_가리킨다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID, "소설", 5, 10_000);
        텍스트_페이지를_생성한다(3_000L, BOOK_ID, 1);
        이미지_페이지를_생성한다(3_001L, BOOK_ID, 2);
        서재_항목을_생성한다(5_000L, READER_ID, BOOK_ID, 1);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 서재_항목을_생성한다(5_001L, READER_ID, BOOK_ID, 2)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 서재_항목을_생성한다(5_002L, READER_ID, BOOK_ID, 3)));
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'hash', '2026-07-26 00:00:00.000000')
                """,
                readerId,
                "reader" + readerId + "@example.com");
    }

    private void 도서를_생성한다(
            long bookId, String category, int totalPageCount, int priceWon) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, ?, '제목', '저자', ?, ?)
                """,
                bookId,
                category,
                totalPageCount,
                priceWon);
    }

    private void 텍스트_페이지를_생성한다(long pageId, long bookId, int pageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, ?, 'TEXT', '본문')
                """,
                pageId,
                bookId,
                pageNumber);
    }

    private void 이미지_페이지를_생성한다(long pageId, long bookId, int pageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, image_path)
                VALUES (?, ?, ?, 'IMAGE', ?)
                """,
                pageId,
                bookId,
                pageNumber,
                "/pages/" + pageNumber + ".png");
    }

    private void 열람_세션을_생성한다(
            long sessionId,
            long readerId,
            long bookId,
            int currentPageNumber,
            String viewerSessionId) {
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (id, reader_id, book_id, current_page_number, viewer_session_id, updated_at)
                VALUES (?, ?, ?, ?, ?, '2026-07-26 00:00:00.000000')
                """,
                sessionId,
                readerId,
                bookId,
                currentPageNumber,
                viewerSessionId);
    }

    private void 서재_항목을_생성한다(
            long entryId, long readerId, long bookId, int lastPageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (id, reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, ?, ?, '2026-07-26 00:00:00.000000')
                """,
                entryId,
                readerId,
                bookId,
                lastPageNumber);
    }
}
