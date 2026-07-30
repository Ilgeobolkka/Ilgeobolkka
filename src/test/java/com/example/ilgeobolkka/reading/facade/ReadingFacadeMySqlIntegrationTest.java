package com.example.ilgeobolkka.reading.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.book.exception.BookPageNotFoundException;
import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingFacadeMySqlIntegrationTest.FixedClockConfiguration.class)
class ReadingFacadeMySqlIntegrationTest {

    private static final long READER_ID = 411_001L;
    private static final long RENTAL_BOOK_ID = 411_101L;
    private static final long OWNED_BOOK_ID = 411_102L;
    private static final long TEXT_PAGE_ID = 411_201L;
    private static final long IMAGE_PAGE_ID = 411_202L;
    private static final long OWNED_PAGE_ID = 411_203L;
    private static final long OWNERSHIP_PAYMENT_ID = 411_301L;
    private static final int CONCURRENT_REQUEST_COUNT = 2;
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00.123456Z");
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingFacadeMySqlIntegrationTest(ReadingFacade readingFacade, JdbcTemplate jdbcTemplate) {
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_RENT_001_소장도_활성_대여도_없는_페이지를_열면_즉시_차감하고_새_대여를_시작한다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertFalse(response.owned()),
                () -> assertEquals(1, response.deductedInk()),
                () -> assertEquals(4, response.inkBalance()),
                () -> assertEquals(NOW, response.rentedAt()),
                () -> assertEquals(NOW.plusSeconds(30L * 24 * 3600), response.expiresAt()),
                () -> assertEquals(BookPageContentType.TEXT, response.contentType()),
                () -> assertEquals(1, 대여_수를_조회한다()),
                () -> assertEquals(1, 차감_원장_수를_조회한다()),
                () -> assertEquals(1, 서재_마지막_페이지를_조회한다(RENTAL_BOOK_ID)),
                () -> assertEquals(1, 세션_현재_페이지를_조회한다()));
    }

    @Test
    void T_RENT_002_활성_대여_중_같은_페이지를_다시_열면_잉크를_추가_차감하지_않는다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();
        활성_대여를_생성한다(TEXT_PAGE_ID, NOW.minusSeconds(3600), NOW.plusSeconds(3600));

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertFalse(response.owned()),
                () -> assertEquals(0, response.deductedInk()),
                () -> assertEquals(5, response.inkBalance()),
                () -> assertEquals(NOW.minusSeconds(3600), response.rentedAt()),
                () -> assertEquals(NOW.plusSeconds(3600), response.expiresAt()),
                () -> assertEquals(1, 대여_수를_조회한다()),
                () -> assertEquals(0, 차감_원장_수를_조회한다()));
    }

    @Test
    void T_BAL_001_잔액_0으로_열면_잉크_부족_오류를_반환하고_대여_기록이_없다() {
        독자를_생성한다(0);
        대여용_도서를_생성한다();

        assertThrows(
                InsufficientInkException.class,
                () -> readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1));

        assertAll(
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 대여_수를_조회한다()),
                () -> assertEquals(0, 차감_원장_수를_조회한다()),
                () -> assertEquals(0, 세션_수를_조회한다()),
                () -> assertEquals(0, 서재_항목_수를_조회한다()));
    }

    @Test
    void T_BAL_002_잔액_1로_열면_1잉크가_차감되고_잔액은_정확히_0이다() {
        독자를_생성한다(1);
        대여용_도서를_생성한다();

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertEquals(1, response.deductedInk()),
                () -> assertEquals(0, response.inkBalance()),
                () -> assertEquals(0, 잔액을_조회한다()));
    }

    @Test
    void T_BAL_003_잔액_0으로_활성_대여_페이지를_다시_열면_차감_없이_콘텐츠를_제공한다() {
        독자를_생성한다(0);
        대여용_도서를_생성한다();
        활성_대여를_생성한다(TEXT_PAGE_ID, NOW.minusSeconds(3600), NOW.plusSeconds(3600));

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertEquals(0, response.deductedInk()),
                () -> assertEquals(0, response.inkBalance()),
                () -> assertEquals(0, 잔액을_조회한다()));
    }

    @Test
    void 소장_도서는_잉크_차감과_대여_없이_페이지를_제공한다() {
        독자를_생성한다(3);
        소장용_도서를_생성한다();

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, OWNED_BOOK_ID, 1);

        assertAll(
                () -> assertTrue(response.owned()),
                () -> assertEquals(0, response.deductedInk()),
                () -> assertEquals(3, response.inkBalance()),
                () -> assertNull(response.rentedAt()),
                () -> assertNull(response.expiresAt()),
                () -> assertEquals(3, 잔액을_조회한다()),
                () -> assertEquals(0, 대여_수를_조회한다()));
    }

    @Test
    void 존재하지_않는_페이지를_열면_실패한다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();

        assertThrows(
                BookPageNotFoundException.class,
                () -> readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 999));
    }

    @Test
    void 새_뷰어를_열면_기존_세션을_교체하고_이전_뷰어는_더_이상_일치하지_않는다() {
        독자를_생성한다(5);
        소장용_도서를_생성한다();

        OpenPageResponse first = readingFacade.openNewSession(READER_ID, OWNED_BOOK_ID, 1);
        OpenPageResponse second = readingFacade.openNewSession(READER_ID, OWNED_BOOK_ID, 1);

        UUID firstViewerSessionId = UUID.fromString(first.viewerSessionId());
        assertAll(
                () -> assertNotEquals(first.viewerSessionId(), second.viewerSessionId()),
                () -> assertEquals(1, 세션_수를_조회한다()),
                () ->
                        assertThrows(
                                ViewerSessionReplacedException.class,
                                () -> readingFacade.movePage(READER_ID, firstViewerSessionId, 1)));
    }

    @Test
    void PATCH_페이지_이동은_같은_뷰어_세션에서_페이지_번호만_바꾼다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();
        UUID viewerSessionId = UUID.fromString(
                readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1).viewerSessionId());

        OpenPageResponse moved = readingFacade.movePage(READER_ID, viewerSessionId, 2);

        assertAll(
                () -> assertEquals(RENTAL_BOOK_ID, moved.bookId()),
                () -> assertEquals(2, moved.pageNumber()),
                () -> assertEquals(BookPageContentType.IMAGE, moved.contentType()),
                () -> assertEquals(viewerSessionId.toString(), moved.viewerSessionId()),
                () -> assertEquals(2, 세션_현재_페이지를_조회한다()));
    }

    @Test
    void 현재_열람_세션이_없으면_페이지_이동에_실패한다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();

        assertThrows(
                ReadingSessionNotFoundException.class,
                () -> readingFacade.movePage(READER_ID, UUID.randomUUID(), 1));
    }

    @Test
    void T_RENT_005_같은_페이지를_동시에_열어도_1잉크만_차감하고_대여도_하나만_만든다() throws Exception {
        독자를_생성한다(5);
        대여용_도서를_생성한다();

        List<OpenPageResponse> responses = 동시에_같은_페이지를_연다(CONCURRENT_REQUEST_COUNT);

        int 차감_합계 = responses.stream().mapToInt(OpenPageResponse::deductedInk).sum();
        assertAll(
                () -> assertEquals(CONCURRENT_REQUEST_COUNT, responses.size()),
                () -> assertEquals(1, 차감_합계),
                () -> assertEquals(4, 잔액을_조회한다()),
                () -> assertEquals(1, 대여_수를_조회한다()),
                () -> assertEquals(1, 차감_원장_수를_조회한다()),
                () -> assertEquals(1, 세션_수를_조회한다()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()));
    }

    /** 잠금 뒤 재확인이 실제로 동작하는지 보려면 모든 요청이 잠금 전 첫 확인을 함께 통과해야 한다. */
    private List<OpenPageResponse> 동시에_같은_페이지를_연다(int 요청_수) throws Exception {
        CyclicBarrier 출발선 = new CyclicBarrier(요청_수);
        ExecutorService executor = Executors.newFixedThreadPool(요청_수);
        try {
            List<Future<OpenPageResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 요청_수; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    출발선.await(5, TimeUnit.SECONDS);
                                    return readingFacade.openNewSession(
                                            READER_ID, RENTAL_BOOK_ID, 1);
                                }));
            }

            List<OpenPageResponse> responses = new ArrayList<>();
            for (Future<OpenPageResponse> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private void 독자를_생성한다(int balance) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum411@example.com', '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)", READER_ID, balance);
    }

    private void 대여용_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-411 대여 도서', '읽어볼까', 2, 10000)
                """,
                RENTAL_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지')
                """,
                TEXT_PAGE_ID,
                RENTAL_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, image_path)
                VALUES (?, ?, 2, 'IMAGE', '/covers/scrum-411/2.jpg')
                """,
                IMAGE_PAGE_ID,
                RENTAL_BOOK_ID);
    }

    private void 소장용_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '에세이', 'SCRUM-411 소장 도서', '읽어볼까', 1, 12000)
                """,
                OWNED_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '소장 도서 첫 페이지')
                """,
                OWNED_PAGE_ID,
                OWNED_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 12000,
                        '2026-07-30 00:00:00.000000', '2026-07-30 00:01:00.000000')
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                OWNED_BOOK_ID,
                UUID.randomUUID().toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-07-30 00:01:00.000000')
                """,
                READER_ID,
                OWNED_BOOK_ID,
                OWNERSHIP_PAYMENT_ID);
    }

    private void 활성_대여를_생성한다(long bookPageId, Instant rentedAt, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                bookPageId,
                DATETIME_FORMATTER.format(rentedAt),
                DATETIME_FORMATTER.format(expiresAt));
    }

    private int 잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 대여_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 차감_원장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ? AND type = 'DEDUCTION'",
                Integer.class,
                READER_ID);
    }

    private int 세션_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reading_session WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 세션_현재_페이지를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT current_page_number FROM reading_session WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 서재_항목_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 서재_마지막_페이지를_조회한다(long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_page_number FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                bookId);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id IN (?, ?)", RENTAL_BOOK_ID, OWNED_BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", RENTAL_BOOK_ID, OWNED_BOOK_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
