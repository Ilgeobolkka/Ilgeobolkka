package com.example.ilgeobolkka.perf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.dto.FindBooksResponse;
import com.example.ilgeobolkka.book.facade.BookFacade;
import com.example.ilgeobolkka.library.dto.FindLibraryResponse;
import com.example.ilgeobolkka.library.facade.LibraryFacade;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * SCRUM-422: MVP 핵심 경로(도서 목록·페이지 열기·서재)의 쿼리 수·응답 시간을 실제 MySQL 8.4에서
 * 측정해 명백한 N+1 유무를 근거로 확인한다. 조회 쿼리는 모두 native SQL 단일 문장이라 목록 크기와
 * 무관하게 쿼리 수가 고정될 것으로 예상하며, 아래 상한 초과는 그 가정이 깨졌다는 회귀 신호다.
 *
 * <p>2026-08-03 로컬 MySQL 8.4(Docker), 도서 100권·서재 30건(소장 10·활성 대여 10·만료 대여 10)
 * 조건으로 측정한 베이스라인은 아래와 같다. 모든 경로가 데이터 규모와 무관한 고정 쿼리 수를 보여
 * 명백한 N+1이 확인되지 않았고, 그에 따라 별도 보정은 하지 않았다.
 *
 * <pre>
 * | 경로                        | 쿼리 수 | 응답 시간 |
 * |-----------------------------|--------|----------|
 * | 도서 목록 1페이지(100권 중) | 2건    | 2ms      |
 * | 도서 목록 2페이지(100권 중) | 2건    | 10ms     |
 * | 서재 조회(30건)             | 1건    | 13ms     |
 * | 페이지 열기(신규 대여)      | 14건   | 97ms     |
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class CoreQueryMeasurementMySqlIntegrationTest {

    private static final long READER_ID = 422_001L;
    private static final long BOOK_ID_BASE = 422_100L;
    private static final int BOOK_COUNT = 100;
    private static final int LIBRARY_ENTRY_COUNT = 30;
    private static final long RENTAL_BOOK_ID = 422_500L;
    private static final long RENTAL_PAGE_ID = 422_501L;
    private static final String[] CATEGORIES = {"소설", "에세이", "과학", "역사", "경제"};

    private final BookFacade bookFacade;
    private final LibraryFacade libraryFacade;
    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;
    private final Statistics statistics;

    @Autowired
    CoreQueryMeasurementMySqlIntegrationTest(
            BookFacade bookFacade,
            LibraryFacade libraryFacade,
            ReadingFacade readingFacade,
            JdbcTemplate jdbcTemplate,
            EntityManagerFactory entityManagerFactory) {
        this.bookFacade = bookFacade;
        this.libraryFacade = libraryFacade;
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    @BeforeEach
    void setUp() {
        데이터를_정리한다();
        독자를_생성한다();
        도서_100권을_생성한다();
        서재_30건을_생성한다();
        대여용_도서를_생성한다();
    }

    @AfterEach
    void tearDown() {
        데이터를_정리한다();
    }

    @Test
    void 도서_첫_목록_쿼리_수와_응답시간을_측정한다() {
        statistics.clear();
        long start = System.nanoTime();

        FindBooksResponse response = bookFacade.findBooks(1, null);

        long elapsedMillis = 경과_밀리초(start);
        long queryCount = statistics.getPrepareStatementCount();
        측정_결과를_출력한다("도서 목록 1페이지(100권 중)", queryCount, elapsedMillis);

        assertTrue(response.books().size() > 0);
        assertTrue(
                queryCount <= 2,
                "목록 조회는 콘텐츠·카운트 쿼리 2건을 넘지 않아야 한다: " + queryCount);
    }

    @Test
    void 도서_다음_목록_쿼리_수와_응답시간을_측정한다() {
        statistics.clear();
        long start = System.nanoTime();

        FindBooksResponse response = bookFacade.findBooks(2, null);

        long elapsedMillis = 경과_밀리초(start);
        long queryCount = statistics.getPrepareStatementCount();
        측정_결과를_출력한다("도서 목록 2페이지(100권 중)", queryCount, elapsedMillis);

        assertTrue(response.books().size() > 0);
        assertTrue(
                queryCount <= 2,
                "목록 조회는 콘텐츠·카운트 쿼리 2건을 넘지 않아야 한다: " + queryCount);
    }

    @Test
    void 서재_조회_쿼리_수와_응답시간을_측정한다() {
        statistics.clear();
        long start = System.nanoTime();

        FindLibraryResponse response = libraryFacade.findLibrary(READER_ID);

        long elapsedMillis = 경과_밀리초(start);
        long queryCount = statistics.getPrepareStatementCount();
        측정_결과를_출력한다("서재 조회(" + LIBRARY_ENTRY_COUNT + "건)", queryCount, elapsedMillis);

        assertTrue(response.entries().size() >= LIBRARY_ENTRY_COUNT);
        assertTrue(queryCount <= 1, "서재 조회는 단일 쿼리를 넘지 않아야 한다: " + queryCount);
    }

    @Test
    void 신규_대여_페이지_열기_쿼리_수와_응답시간을_측정한다() {
        statistics.clear();
        long start = System.nanoTime();

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        long elapsedMillis = 경과_밀리초(start);
        long queryCount = statistics.getPrepareStatementCount();
        측정_결과를_출력한다("페이지 열기(신규 대여)", queryCount, elapsedMillis);

        assertNotNull(response);
        assertTrue(
                queryCount <= 14,
                "잠금→재확인→신규 대여 흐름의 고정 쿼리 수(측정값 14건)를 벗어났다: " + queryCount);
    }

    private long 경과_밀리초(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void 측정_결과를_출력한다(String label, long queryCount, long elapsedMillis) {
        System.out.printf(
                "[SCRUM-422 측정] %s -> 쿼리 %d건, %dms%n", label, queryCount, elapsedMillis);
    }

    private void 독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum422@example.com', '{noop}password', '2026-08-03 00:00:00.000000')
                AS incoming
                ON DUPLICATE KEY UPDATE password_hash = incoming.password_hash
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_account (reader_id, balance) VALUES (?, 100)
                AS incoming
                ON DUPLICATE KEY UPDATE balance = incoming.balance
                """,
                READER_ID);
    }

    private void 도서_100권을_생성한다() {
        List<Object[]> bookRows = new ArrayList<>();
        List<Object[]> pageRows = new ArrayList<>();
        for (int i = 0; i < BOOK_COUNT; i++) {
            long bookId = BOOK_ID_BASE + i;
            String category = CATEGORIES[i % CATEGORIES.length];
            String title = "SCRUM-422 측정도서 %03d".formatted(i);
            bookRows.add(new Object[] {bookId, category, title, "읽어볼까", 1, 10_000});
            pageRows.add(new Object[] {페이지_아이디(bookId), bookId, 1, "TEXT", title + " 첫 페이지"});
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                bookRows);
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, ?, ?, ?)
                """,
                pageRows);
    }

    /**
     * 서재 항목 30건을 소장 10건·활성 대여 10건·만료 대여 10건으로 나눠 만들어, 서재 쿼리의
     * LEFT JOIN 대상(book_ownership, page_rental)이 모두 실제로 채워지는 조건에서 측정한다.
     * library_entry는 언제나 소장 또는 대여를 동반해 생성되므로(둘 다 없는 상태는 실제로 발생하지
     * 않는다), 만료된 상태도 대여 기록 자체는 남아 있는 형태로 만든다.
     */
    private void 서재_30건을_생성한다() {
        for (int i = 0; i < LIBRARY_ENTRY_COUNT; i++) {
            long bookId = BOOK_ID_BASE + i;
            long pageId = 페이지_아이디(bookId);
            jdbcTemplate.update(
                    """
                    INSERT INTO library_entry (reader_id, book_id, last_page_number, updated_at)
                    VALUES (?, ?, 1, '2026-08-03 00:00:00.000000')
                    """,
                    READER_ID,
                    bookId);

            if (i < 10) {
                long paymentId = 422_600_000L + i;
                jdbcTemplate.update(
                        """
                        INSERT INTO ownership_payment
                            (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                        VALUES (?, ?, ?, ?, 'PAID', 10000,
                                '2026-08-03 00:00:00.000000', '2026-08-03 00:01:00.000000')
                        """,
                        paymentId,
                        READER_ID,
                        bookId,
                        UUID.randomUUID().toString());
                jdbcTemplate.update(
                        """
                        INSERT INTO book_ownership (reader_id, book_id, ownership_payment_id, created_at)
                        VALUES (?, ?, ?, '2026-08-03 00:01:00.000000')
                        """,
                        READER_ID,
                        bookId,
                        paymentId);
            } else if (i < 20) {
                jdbcTemplate.update(
                        """
                        INSERT INTO page_rental (reader_id, book_page_id, rented_at, expires_at)
                        VALUES (?, ?, '2026-08-03 00:00:00.000000', '2026-09-02 00:00:00.000000')
                        """,
                        READER_ID,
                        pageId);
            } else {
                jdbcTemplate.update(
                        """
                        INSERT INTO page_rental (reader_id, book_page_id, rented_at, expires_at)
                        VALUES (?, ?, '2026-06-01 00:00:00.000000', '2026-07-01 00:00:00.000000')
                        """,
                        READER_ID,
                        pageId);
            }
        }
    }

    private void 대여용_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-422 페이지 열기 측정 도서', '읽어볼까', 1, 10000)
                """,
                RENTAL_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '측정용 첫 페이지')
                """,
                RENTAL_PAGE_ID,
                RENTAL_BOOK_ID);
    }

    private long 페이지_아이디(long bookId) {
        return bookId * 10;
    }

    private void 데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id BETWEEN ? AND ?",
                BOOK_ID_BASE,
                BOOK_ID_BASE + BOOK_COUNT - 1);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", RENTAL_BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM book WHERE id BETWEEN ? AND ?",
                BOOK_ID_BASE,
                BOOK_ID_BASE + BOOK_COUNT - 1);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", RENTAL_BOOK_ID);
    }
}
