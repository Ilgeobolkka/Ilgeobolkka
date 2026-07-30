package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.facade.OpenPageViewer;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SCRUM-434(3/5)의 잠금 뒤 재확인({@code ReadingFacade.provideRentedPage} 4단계)이 실제 동시성
 * 상황에서 효과가 있는지 증명한다.
 *
 * <p>스레드1이 {@code InkAccount} 행 잠금을 보유한 채 커밋을 지연시키는 동안, 스레드2({@code
 * ReadingFacade.openPage} 실제 호출)는 잠금 획득 전 최초 확인에서는 아직 없는 활성 대여를 관찰한다.
 * 그 틈에 테스트 fixture가 활성 대여를 직접 저장·커밋하고, 스레드1이 잠금을 해제하면 스레드2는 잠금을
 * 획득한 뒤 재확인에서 "방금 생긴" 활성 대여를 인지해 차감 없이 응답해야 한다.
 *
 * <p>재확인 검증 근거: 스레드2는 최초 확인 시점에 활성 대여를 보지 못한다. 활성 대여 행은 그
 * 뒤에 커밋되기 때문이다. 따라서 {@code inkService.lockAccount} 다음의 두 번째
 * {@code pageRentalService.findActive(...)} 재조회 분기가 없으면 스레드2가 갈 곳은 스텁
 * {@link UnsupportedOperationException}뿐이고 이 테스트는 실패한다. 재확인 분기만이 이 테스트를
 * 통과시킬 수 있다.
 *
 * <p>이 테스트가 성립하는 전제는 {@code ReadingFacade.openPage}의 격리 수준이 READ COMMITTED라는
 * 것이다(docs/adr/application/0014-read-page-opening-at-read-committed.md). REPEATABLE READ에서는
 * 재확인이 최초 확인과 같은 스냅샷을 읽어 방금 커밋된 대여를 보지 못한다. 그래서 이 테스트는
 * 격리 수준 결정과 재확인 분기 **둘 다** 있어야 통과한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ReadingRentalLockReconfirmationMySqlIntegrationTest {

    private static final long READER_ID = 434_201L;
    private static final long BOOK_ID = 434_201L;
    private static final long BOOK_PAGE_ID = 434_201L;
    private static final long RENTAL_ID = 434_201L;
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00.000000Z");
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final ReadingFacade readingFacade;
    private final InkService inkService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ReadingRentalLockReconfirmationMySqlIntegrationTest(
            ReadingFacade readingFacade,
            InkService inkService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.readingFacade = readingFacade;
        this.inkService = inkService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        소장하지_않은_도서와_페이지를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void 잠금_보유_중_생긴_활성_대여를_재확인해_차감_없이_제공한다() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(
                    status -> {
                        inkService.lockAccount(READER_ID);
                        locked.countDown();
                        await(release);
                    }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));

            Future<OpenPageResponse> reader = executor.submit(() -> readingFacade.openPage(
                    READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1));

            // 스레드2가 최초 확인(잠금 전)을 지나 잠금 대기 상태에 들어갈 시간을 준다.
            Thread.sleep(300);
            assertFalse(reader.isDone());

            Instant rentedAt = NOW.minusSeconds(60 * 60 * 24);
            Instant expiresAt = NOW.plusSeconds(60L * 60 * 24 * 29);
            테스트_fixture로_활성_대여를_직접_저장한다(rentedAt, expiresAt);

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            OpenPageResponse response = reader.get(5, TimeUnit.SECONDS);

            assertFalse(response.owned());
            assertEquals(0, response.deductedInk());
            assertEquals(rentedAt, response.rentedAt());
            assertEquals(expiresAt, response.expiresAt());
            assertEquals(5, 잉크_잔액을_조회한다());
            assertEquals(0, 잉크_내역_수를_조회한다());
            assertEquals(1, 페이지_대여_수를_조회한다());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-01 00:00:00.000000')
                """,
                READER_ID,
                "scrum434-lock-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)", READER_ID);
    }

    private void 소장하지_않은_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 434 Lock', 'Author 434', 'Description 434',
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

    private void 테스트_fixture로_활성_대여를_직접_저장한다(Instant rentedAt, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                RENTAL_ID,
                READER_ID,
                BOOK_PAGE_ID,
                DATETIME_FORMATTER.format(rentedAt),
                DATETIME_FORMATTER.format(expiresAt));
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
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ? AND book_page_id = ?",
                Integer.class,
                READER_ID,
                BOOK_PAGE_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }
}
