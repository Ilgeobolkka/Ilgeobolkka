package com.example.ilgeobolkka.ink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.ink.repository.InkPurchaseRepository;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.List;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class InkConcurrencyMySqlIntegrationTest {

    private static final long READER_ID = 407_300L;
    private static final long BOOK_ID = 407_300L;
    private static final long PAGE_ID = 407_300L;
    private static final long PAID_PURCHASE_ID = 407_300L;
    private static final long FIRST_RENTAL_ID = 407_300L;
    private static final long SECOND_RENTAL_ID = 407_301L;

    private final InkService inkService;
    private final InkPurchaseRepository inkPurchaseRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    InkConcurrencyMySqlIntegrationTest(
            InkService inkService,
            InkPurchaseRepository inkPurchaseRepository,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.inkService = inkService;
        this.inkPurchaseRepository = inkPurchaseRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'concurrent-ink-reader@example.com', 'encoded-password',
                        '2026-07-28 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 1)",
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, '기술', '동시 차감 도서', '테스트 저자', NULL, NULL, 1, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 1, 'TEXT', '테스트 페이지', NULL)
                """,
                PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', 1000, 100,
                        '2026-07-28 09:00:00.000000',
                        '2026-07-28 10:00:00.000000')
                """,
                PAID_PURCHASE_ID,
                READER_ID,
                UUID.nameUUIDFromBytes("concurrent-grant-purchase".getBytes()).toString());
        페이지_대여를_생성한다(FIRST_RENTAL_ID);
        페이지_대여를_생성한다(SECOND_RENTAL_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }

    @Test
    void INV_004_잔액_1에서_서로_다른_대여를_동시에_차감해도_한_건만_성공한다()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> 차감을_시도한다(FIRST_RENTAL_ID, ready, start)),
                    executor.submit(() -> 차감을_시도한다(SECOND_RENTAL_ID, ready, start)));

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "두 차감 작업이 제한 시간 안에 준비되어야 합니다.");
            start.countDown();

            long successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertEquals(1, successCount);
        }

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
    }

    @Test
    void 같은_구매를_동시에_지급해도_두_요청은_성공하고_한_번만_반영된다()
            throws Exception {
        CountDownLatch snapshotReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> 지급을_시도한다(snapshotReady, start)),
                    executor.submit(() -> 지급을_시도한다(snapshotReady, start)));

            assertTrue(
                    snapshotReady.await(5, TimeUnit.SECONDS),
                    "두 지급 작업이 제한 시간 안에 구매 정보를 읽어야 합니다.");
            start.countDown();

            for (Future<Boolean> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS));
            }
        }

        assertEquals(
                101,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_ledger
                        WHERE reader_id = ? AND ink_purchase_id = ?
                        """,
                        Integer.class,
                        READER_ID,
                        PAID_PURCHASE_ID));
    }

    private boolean 차감을_시도한다(
            long rentalId,
            CountDownLatch ready,
            CountDownLatch start) {
        try {
            return transactionTemplate.execute(status -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                try {
                    inkService.deductInk(READER_ID, rentalId);
                    return true;
                } catch (InsufficientInkException exception) {
                    return false;
                }
            });
        } catch (InsufficientInkException exception) {
            return false;
        }
    }

    private boolean 지급을_시도한다(
            CountDownLatch snapshotReady,
            CountDownLatch start) {
        return transactionTemplate.execute(status -> {
            inkPurchaseRepository.findByIdAndReaderId(PAID_PURCHASE_ID, READER_ID)
                    .orElseThrow();
            snapshotReady.countDown();
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }

            inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
            return true;
        });
    }

    private void 페이지_대여를_생성한다(long rentalId) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2026-07-28 10:00:00.000000',
                        '2026-08-27 10:00:00.000000')
                """,
                rentalId,
                READER_ID,
                PAGE_ID);
    }
}
