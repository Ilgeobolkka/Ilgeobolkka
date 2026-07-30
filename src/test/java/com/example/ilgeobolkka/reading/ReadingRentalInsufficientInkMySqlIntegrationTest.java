package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.reading.facade.OpenPageViewer;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * SCRUM-435: 잉크가 부족하면 도메인 예외로 실패하고 어떤 행도 남지 않는지 증명한다(T-BAL-001의
 * 이번 범위분). 공개 {@code 422 INSUFFICIENT_INK} 응답 계약은 {@code
 * ReadingSessionFailureResponseMySqlIntegrationTest}(SCRUM-436, 5/5)가 HTTP 레벨로 증명한다.
 *
 * <p>클래스 레벨 {@code @Transactional}을 두지 않는다. 저장이 없었음을 확인하려면 실제 커밋된 DB
 * 상태를 봐야 한다. 테스트가 트랜잭션 안에 있으면 {@code ReadingFacade.openPage}가 그 트랜잭션에
 * 합류하고, 롤백은 테스트가 끝날 때 일어나므로 **먼저 저장된 {@code page_rental} 행이 조회에
 * 그대로 보인다.** 그 상태로는 "저장되지 않았다"와 "롤백될 예정이다"를 구분할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ReadingRentalInsufficientInkMySqlIntegrationTest {

    private static final long READER_ID = 435_301L;
    private static final long BOOK_ID = 435_301L;
    private static final long BOOK_PAGE_ID = 435_301L;

    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingRentalInsufficientInkMySqlIntegrationTest(
            ReadingFacade readingFacade, JdbcTemplate jdbcTemplate) {
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        잔액_0인_독자를_생성한다();
        소장하지_않은_도서와_페이지를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void 잔액이_0이고_활성_대여도_없으면_잉크_부족_예외가_발생하고_아무_행도_남지_않는다() {
        assertThrows(
                InsufficientInkException.class,
                () -> readingFacade.openPage(READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1));

        assertEquals(0, 잉크_잔액을_조회한다());
        assertEquals(0, 페이지_대여_수를_조회한다());
        assertEquals(0, 잉크_내역_수를_조회한다());
        assertEquals(0, 서재_항목_수를_조회한다());
    }

    private void 잔액_0인_독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-01 00:00:00.000000')
                """,
                READER_ID,
                "scrum435-insufficient-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)", READER_ID);
    }

    private void 소장하지_않은_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 435 Insufficient', 'Author 435', 'Description 435',
                        '/assets/covers/demo/category-01.svg', 1, 9001)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', 'Page 1 content')
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
    }

    private int 잉크_잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 잉크_내역_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 페이지_대여_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 서재_항목_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }
}
