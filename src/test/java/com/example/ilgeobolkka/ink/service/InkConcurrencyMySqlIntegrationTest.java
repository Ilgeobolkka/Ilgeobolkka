package com.example.ilgeobolkka.ink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
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
    private static final long OTHER_READER_ID = 407_301L;
    private static final long BOOK_ID = 407_300L;
    private static final long PAGE_ID = 407_300L;
    private static final long PAID_PURCHASE_ID = 407_300L;
    private static final long OTHER_PAID_PURCHASE_ID = 407_301L;
    private static final long FIRST_RENTAL_ID = 407_300L;
    private static final long SECOND_RENTAL_ID = 407_301L;

    private final InkService inkService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    InkConcurrencyMySqlIntegrationTest(
            InkService inkService,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.inkService = inkService;
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
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'other-concurrent-ink-reader@example.com', 'encoded-password',
                        '2026-07-28 00:00:00.000000')
                """,
                OTHER_READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 1)",
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                OTHER_READER_ID);
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
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', 1000, 100,
                        '2026-07-28 09:00:00.000000',
                        '2026-07-28 10:00:00.000000')
                """,
                OTHER_PAID_PURCHASE_ID,
                OTHER_READER_ID,
                UUID.nameUUIDFromBytes("other-concurrent-grant-purchase".getBytes()).toString());
        페이지_대여를_생성한다(FIRST_RENTAL_ID);
        페이지_대여를_생성한다(SECOND_RENTAL_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE reader_id = ?", OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", OTHER_READER_ID);
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
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_operation_claim WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));

        long unprocessedRentalId = jdbcTemplate.queryForObject(
                """
                SELECT rental.id
                FROM page_rental rental
                LEFT JOIN ink_ledger ledger ON ledger.page_rental_id = rental.id
                WHERE rental.reader_id = ?
                  AND ledger.id IS NULL
                """,
                Long.class,
                READER_ID);
        transactionTemplate.executeWithoutResult(
                status -> inkService.grantInk(READER_ID, PAID_PURCHASE_ID));
        transactionTemplate.executeWithoutResult(
                status -> inkService.deductInk(READER_ID, unprocessedRentalId));

        assertEquals(
                99,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_operation_claim WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
    }

    @Test
    void 같은_대여를_동시에_차감해도_두_요청은_성공하고_한_번만_반영된다()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> 차감을_시도한다(FIRST_RENTAL_ID, ready, start)),
                    executor.submit(() -> 차감을_시도한다(FIRST_RENTAL_ID, ready, start)));

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "두 차감 작업이 제한 시간 안에 준비되어야 합니다.");
            start.countDown();

            for (Future<Boolean> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS));
            }
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
                        "SELECT COUNT(*) FROM ink_ledger WHERE page_rental_id = ?",
                        Integer.class,
                        FIRST_RENTAL_ID));
    }

    @Test
    void 같은_구매를_동시에_지급해도_두_요청은_성공하고_한_번만_반영된다()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> 지급을_시도한다(
                            READER_ID,
                            PAID_PURCHASE_ID,
                            ready,
                            start)),
                    executor.submit(() -> 지급을_시도한다(
                            READER_ID,
                            PAID_PURCHASE_ID,
                            ready,
                            start)));

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "두 지급 작업이 제한 시간 안에 준비되어야 합니다.");
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

    @Test
    void 서로_다른_독자에게_동시에_지급해도_두_서비스_호출이_모두_성공한다()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> 지급을_시도한다(
                            READER_ID,
                            PAID_PURCHASE_ID,
                            ready,
                            start)),
                    executor.submit(() -> 지급을_시도한다(
                            OTHER_READER_ID,
                            OTHER_PAID_PURCHASE_ID,
                            ready,
                            start)));

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "서로 다른 독자의 두 지급 작업이 제한 시간 안에 준비되어야 합니다.");
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
                100,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        OTHER_READER_ID));
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE ink_purchase_id IN (?, ?)",
                        Integer.class,
                        PAID_PURCHASE_ID,
                        OTHER_PAID_PURCHASE_ID));
    }

    @Test
    void 과거_스냅샷이_있어도_같은_구매의_재지급은_멱등_성공한다()
            throws Exception {
        과거_스냅샷에서_다른_트랜잭션_처리_후_재처리한다(
                """
                SELECT COUNT(*)
                FROM ink_ledger
                WHERE ink_purchase_id = ?
                """,
                PAID_PURCHASE_ID,
                () -> inkService.grantInk(READER_ID, PAID_PURCHASE_ID));

        assertEquals(
                101,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE ink_purchase_id = ?",
                        Integer.class,
                        PAID_PURCHASE_ID));
    }

    @Test
    void 과거_스냅샷이_있어도_같은_대여의_재차감은_멱등_성공한다()
            throws Exception {
        과거_스냅샷에서_다른_트랜잭션_처리_후_재처리한다(
                """
                SELECT COUNT(*)
                FROM ink_ledger
                WHERE page_rental_id = ?
                """,
                FIRST_RENTAL_ID,
                () -> inkService.deductInk(READER_ID, FIRST_RENTAL_ID));

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE page_rental_id = ?",
                        Integer.class,
                        FIRST_RENTAL_ID));
    }

    private void 과거_스냅샷에서_다른_트랜잭션_처리_후_재처리한다(
            String snapshotQuery,
            long sourceId,
            Runnable operation) throws Exception {
        CountDownLatch snapshotCreated = new CountDownLatch(1);
        CountDownLatch firstOperationCommitted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> retry = executor.submit(() -> transactionTemplate.execute(status -> {
                assertEquals(
                        0,
                        jdbcTemplate.queryForObject(
                                snapshotQuery,
                                Integer.class,
                                sourceId));
                snapshotCreated.countDown();
                await(firstOperationCommitted, "첫 처리가 제한 시간 안에 커밋되어야 합니다.");

                operation.run();
                return true;
            }));
            Future<Boolean> firstOperation = executor.submit(() -> {
                await(snapshotCreated, "재처리 트랜잭션이 제한 시간 안에 스냅샷을 생성해야 합니다.");
                boolean result = transactionTemplate.execute(status -> {
                    operation.run();
                    return true;
                });
                firstOperationCommitted.countDown();
                return result;
            });

            assertTrue(firstOperation.get(10, TimeUnit.SECONDS));
            assertTrue(retry.get(10, TimeUnit.SECONDS));
        }
    }

    private boolean 차감을_시도한다(
            long rentalId,
            CountDownLatch ready,
            CountDownLatch start) {
        try {
            return transactionTemplate.execute(status -> {
                ready.countDown();
                await(start, "두 차감 작업이 제한 시간 안에 시작되어야 합니다.");
                inkService.deductInk(READER_ID, rentalId);
                return true;
            });
        } catch (InsufficientInkException exception) {
            return false;
        }
    }

    private boolean 지급을_시도한다(
            long readerId,
            long purchaseId,
            CountDownLatch ready,
            CountDownLatch start) {
        return transactionTemplate.execute(status -> {
            ready.countDown();
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }

            inkService.grantInk(readerId, purchaseId);
            return true;
        });
    }

    private void await(CountDownLatch latch, String timeoutMessage) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), timeoutMessage);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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
