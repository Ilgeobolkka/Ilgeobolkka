package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// QuerydslTestEntity(테스트 전용 @Entity)가 com.example.ilgeobolkka 하위 패키지에 있어
// 컨텍스트 로딩 시점에 ddl-auto=validate가 그대로 적용되면 그 엔티티의 테이블이 없어 실패한다
// (ADR-0010 결과 절). 이 테스트도 같은 이유로 검증을 끈다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class CoreDomainSchemaMigrationTest {

    private static final long READER_ID = 1_000L;
    private static final long OTHER_READER_ID = 1_001L;
    private static final long BOOK_ID = 2_000L;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CoreDomainSchemaMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void 테스트_데이터를_정리한다() {
        jdbcTemplate.execute("DELETE FROM point_ledger");
        jdbcTemplate.execute("DELETE FROM point_account");
        jdbcTemplate.execute("DELETE FROM library_entry");
        jdbcTemplate.execute("DELETE FROM confirmed_page");
        jdbcTemplate.execute("DELETE FROM reading_session");
        jdbcTemplate.execute("DELETE FROM reading_consent");
        jdbcTemplate.execute("DELETE FROM book");
        jdbcTemplate.execute("DELETE FROM reader");
    }

    @Test
    void V1_마이그레이션이_8개_핵심_도메인_테이블을_모두_생성한다() {
        List<String> tableNames =
                jdbcTemplate.queryForList(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'reader', 'book', 'reading_consent', 'reading_session',
                              'confirmed_page', 'library_entry', 'point_account', 'point_ledger'
                          )
                        """,
                        String.class);

        assertEquals(8, tableNames.size());
    }

    @Test
    void 같은_사용자와_도서로_중복_열람_동의를_저장하면_유니크_제약_위반이_발생한다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID);
        jdbcTemplate.update(
                "INSERT INTO reading_consent (reader_id, book_id, consented_at) VALUES (?, ?, NOW())",
                READER_ID,
                BOOK_ID);

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO reading_consent (reader_id, book_id, consented_at) VALUES (?, ?, NOW())",
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 같은_사용자와_도서_페이지로_중복_확정하면_유니크_제약_위반이_발생한다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID);
        jdbcTemplate.update(
                "INSERT INTO confirmed_page (reader_id, book_id, page_number, confirmed_at) VALUES (?, ?, ?, NOW())",
                READER_ID,
                BOOK_ID,
                1);

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO confirmed_page (reader_id, book_id, page_number, confirmed_at) VALUES (?, ?, ?, NOW())",
                                READER_ID,
                                BOOK_ID,
                                1));
    }

    @Test
    void 같은_사용자와_도서라도_다른_페이지_번호로_확정하면_모두_저장된다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID);
        jdbcTemplate.update(
                "INSERT INTO confirmed_page (reader_id, book_id, page_number, confirmed_at) VALUES (?, ?, ?, NOW())",
                READER_ID,
                BOOK_ID,
                1);

        assertDoesNotThrow(
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO confirmed_page (reader_id, book_id, page_number, confirmed_at) VALUES (?, ?, ?, NOW())",
                                READER_ID,
                                BOOK_ID,
                                2));
    }

    @Test
    void 같은_사용자와_도서로_서재_항목을_중복_등록하면_유니크_제약_위반이_발생한다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID);
        jdbcTemplate.update(
                "INSERT INTO library_entry (reader_id, book_id, last_confirmed_page_number, updated_at) VALUES (?, ?, ?, NOW())",
                READER_ID,
                BOOK_ID,
                1);

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO library_entry (reader_id, book_id, last_confirmed_page_number, updated_at) VALUES (?, ?, ?, NOW())",
                                READER_ID,
                                BOOK_ID,
                                2));
    }

    @Test
    void 같은_사용자로_열람_세션을_두_번_생성하면_유니크_제약_위반이_발생한다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (reader_id, book_id, current_page_number, page_opened_at, session_token, updated_at)
                VALUES (?, ?, 1, NOW(), 'token-1', NOW())
                """,
                READER_ID,
                BOOK_ID);

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                """
                                INSERT INTO reading_session
                                    (reader_id, book_id, current_page_number, page_opened_at, session_token, updated_at)
                                VALUES (?, ?, 1, NOW(), 'token-2', NOW())
                                """,
                                READER_ID,
                                BOOK_ID));
    }

    @Test
    void 같은_사용자로_포인트_계좌를_두_번_생성하면_유니크_제약_위반이_발생한다() {
        독자를_생성한다(READER_ID);
        jdbcTemplate.update(
                "INSERT INTO point_account (reader_id, balance) VALUES (?, ?)", READER_ID, 0);

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO point_account (reader_id, balance) VALUES (?, ?)",
                                READER_ID,
                                100));
    }

    @Test
    void 포인트_계좌_잔액에_음수_값을_저장하면_체크_제약_위반이_발생한다() {
        독자를_생성한다(READER_ID);

        assertThrows(
                DataAccessException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO point_account (reader_id, balance) VALUES (?, ?)",
                                READER_ID,
                                -1));
    }

    @Test
    void 존재하지_않는_독자로_포인트_계좌를_생성하면_외래키_제약_위반이_발생한다() {
        long 존재하지_않는_독자_ID = 999_999L;

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO point_account (reader_id, balance) VALUES (?, ?)",
                                존재하지_않는_독자_ID,
                                0));
    }

    @Test
    void 정상적인_값으로는_핵심_도메인_행이_모두_저장된다() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(OTHER_READER_ID);
        도서를_생성한다(BOOK_ID);

        assertDoesNotThrow(
                () ->
                        jdbcTemplate.update(
                                "INSERT INTO point_account (reader_id, balance) VALUES (?, ?)",
                                READER_ID,
                                50));
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                "INSERT INTO reader (id, email, password_hash, created_at) VALUES (?, ?, 'hash', NOW())",
                readerId,
                "reader" + readerId + "@example.com");
    }

    private void 도서를_생성한다(long bookId) {
        jdbcTemplate.update(
                "INSERT INTO book (id, title, author, total_page_count) VALUES (?, '제목', '저자', 100)",
                bookId);
    }
}
