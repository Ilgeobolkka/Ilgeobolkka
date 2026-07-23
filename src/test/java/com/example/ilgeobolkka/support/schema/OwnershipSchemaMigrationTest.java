package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OwnershipSchemaMigrationTest {

    private static final long READER_ID = 11_000L;
    private static final long BOOK_ID = 12_000L;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OwnershipSchemaMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void V2_마이그레이션은_확정_페이지_수를_0으로_초기화하는_필수_컬럼을_추가한다() {
        Map<String, Object> column =
                jdbcTemplate.queryForMap(
                        """
                        SELECT is_nullable, column_default
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'library_entry'
                          AND column_name = 'confirmed_page_count'
                        """);

        assertEquals("NO", column.get("is_nullable"));
        assertEquals("0", String.valueOf(column.get("column_default")));
    }

    @Test
    void 전체_페이지_수가_0인_도서는_저장할_수_없다() {
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO book (id, title, author, total_page_count)
                                VALUES (?, '제목', '저자', 0)
                                """,
                                BOOK_ID));
    }

    @Test
    void 페이지_번호가_0인_열람_확정은_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO confirmed_page
                                    (reader_id, book_id, page_number, confirmed_at)
                                VALUES (?, ?, 0, NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 현재_페이지_번호가_0인_열람_세션은_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO reading_session
                                    (reader_id, book_id, current_page_number, page_opened_at,
                                     session_token, updated_at)
                                VALUES (?, ?, 0, NOW(6), 'token', NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 마지막_확정_페이지_번호가_0인_서재_항목은_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO library_entry
                                    (reader_id, book_id, last_confirmed_page_number,
                                     confirmed_page_count, updated_at)
                                VALUES (?, ?, 0, 0, NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 확정_페이지_수가_음수인_서재_항목은_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO library_entry
                                    (reader_id, book_id, last_confirmed_page_number,
                                     confirmed_page_count, updated_at)
                                VALUES (?, ?, 1, -1, NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 페이지_차감액이_50P가_아니면_원장에_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO point_ledger
                                    (reader_id, type, amount, balance_after,
                                     book_id, page_number, occurred_at)
                                VALUES (?, 'DEDUCTION', 49, 0, ?, 1, NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 지급액이_0P이면_원장에_저장할_수_없다() {
        독자를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO point_ledger
                                    (reader_id, type, amount, balance_after, occurred_at)
                                VALUES (?, 'GRANT', 0, 0, NOW(6))
                                """,
                                READER_ID));
    }

    @Test
    void 지급_내역에_도서와_페이지를_넣으면_원장에_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO point_ledger
                                    (reader_id, type, amount, balance_after,
                                     book_id, page_number, occurred_at)
                                VALUES (?, 'GRANT', 10000, 10000, ?, 1, NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 차감_내역에서_도서나_페이지를_빼면_원장에_저장할_수_없다() {
        독자를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO point_ledger
                                    (reader_id, type, amount, balance_after, occurred_at)
                                VALUES (?, 'DEDUCTION', 50, 0, NOW(6))
                                """,
                                READER_ID));
    }

    @Test
    void 차감_후_잔액이_음수인_내역은_저장할_수_없다() {
        독자를_생성한다();
        도서를_생성한다();

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO point_ledger
                                    (reader_id, type, amount, balance_after,
                                     book_id, page_number, occurred_at)
                                VALUES (?, 'DEDUCTION', 50, -1, ?, 1, NOW(6))
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    private void 독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'hash', NOW(6))
                """,
                READER_ID,
                "reader" + READER_ID + "@example.com");
    }

    private void 도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, title, author, total_page_count)
                VALUES (?, '제목', '저자', 100)
                """,
                BOOK_ID);
    }
}
