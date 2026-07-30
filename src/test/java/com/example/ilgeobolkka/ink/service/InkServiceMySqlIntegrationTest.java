package com.example.ilgeobolkka.ink.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.ink.entity.InkAccount;
import com.example.ilgeobolkka.ink.exception.InkAccountNotFoundException;
import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class InkServiceMySqlIntegrationTest {

    private static final long READER_ID = 407_001L;
    private static final long ACCOUNT_ID = 407_101L;
    private static final long BOOK_ID = 407_201L;
    private static final long BOOK_PAGE_ID = 407_301L;
    private static final long PURCHASE_ID = 407_401L;
    private static final long RENTAL_ID = 407_501L;
    private static final long SECOND_RENTAL_ID = 407_502L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T07:00:00.123456Z");

    private final InkService inkService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    InkServiceMySqlIntegrationTest(
            InkService inkService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.inkService = inkService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
    void 지급은_호출자가_시작한_트랜잭션이_필요하다() {
        기본_데이터를_생성한다(0);
        이용권_결제를_생성한다();

        assertThrows(
                IllegalTransactionStateException.class,
                () -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT));
        assertAll(
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 원장_수를_조회한다()));
    }

    @Test
    void 지급하면_계좌와_원장을_같은_트랜잭션에서_변경한다() {
        기본_데이터를_생성한다(0);
        이용권_결제를_생성한다();

        트랜잭션에서(() -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT));

        Map<String, Object> ledger = 원장을_조회한다();
        assertAll(
                () -> assertEquals(100, 잔액을_조회한다()),
                () -> assertEquals("GRANT", ledger.get("type")),
                () -> assertEquals(100, ((Number) ledger.get("amount")).intValue()),
                () -> assertEquals(100, ((Number) ledger.get("balance_after")).intValue()),
                () -> assertEquals(PURCHASE_ID, ((Number) ledger.get("ink_purchase_id")).longValue()),
                () -> assertNull(ledger.get("page_rental_id")),
                () -> assertEquals(
                        "2026-07-29 07:00:00.123456", ledger.get("occurred_at")));
    }

    @Test
    void 차감하면_계좌와_원장을_같은_트랜잭션에서_변경한다() {
        기본_데이터를_생성한다(1);
        대여_기본_데이터를_생성한다();
        페이지_대여를_생성한다(RENTAL_ID);

        트랜잭션에서(() -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT));

        Map<String, Object> ledger = 원장을_조회한다();
        assertAll(
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals("DEDUCTION", ledger.get("type")),
                () -> assertEquals(1, ((Number) ledger.get("amount")).intValue()),
                () -> assertEquals(0, ((Number) ledger.get("balance_after")).intValue()),
                () -> assertNull(ledger.get("ink_purchase_id")),
                () -> assertEquals(RENTAL_ID, ((Number) ledger.get("page_rental_id")).longValue()));
    }

    @Test
    void 잔액이_0이면_차감에_실패하고_계좌와_원장이_변경되지_않는다() {
        기본_데이터를_생성한다(0);
        대여_기본_데이터를_생성한다();
        페이지_대여를_생성한다(RENTAL_ID);

        assertThrows(
                InsufficientInkException.class,
                () -> 트랜잭션에서(
                        () -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT)));
        assertAll(
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 원장_수를_조회한다()));
    }

    @Test
    void 잉크_계좌가_없으면_지급하지_않는다() {
        독자를_생성한다();
        이용권_결제를_생성한다();

        assertThrows(
                InkAccountNotFoundException.class,
                () -> 트랜잭션에서(
                        () -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT)));
        assertEquals(0, 원장_수를_조회한다());
    }

    @Test
    void 원장_저장에_실패하면_지급된_잔액도_롤백한다() {
        기본_데이터를_생성한다(0);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> 트랜잭션에서(
                        () -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT)));
        assertAll(
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 원장_수를_조회한다()));
    }

    @Test
    void 같은_구매를_재처리해도_한_번만_지급한다() {
        기본_데이터를_생성한다(0);
        이용권_결제를_생성한다();

        트랜잭션에서(() -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT));
        트랜잭션에서(() -> inkService.grant(
                READER_ID,
                PURCHASE_ID,
                OCCURRED_AT.plusSeconds(315_360_000L)));

        assertAll(
                () -> assertEquals(100, 잔액을_조회한다()),
                () -> assertEquals(1, 원장_수를_조회한다()),
                () -> assertEquals(
                        "2026-07-29 07:00:00.123456",
                        원장을_조회한다().get("occurred_at")));
    }

    @Test
    void 같은_대여를_재처리해도_한_번만_차감한다() {
        기본_데이터를_생성한다(2);
        대여_기본_데이터를_생성한다();
        페이지_대여를_생성한다(RENTAL_ID);

        트랜잭션에서(() -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT));
        트랜잭션에서(() -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT.plusSeconds(1)));

        assertAll(
                () -> assertEquals(1, 잔액을_조회한다()),
                () -> assertEquals(1, 원장_수를_조회한다()));
    }

    @Test
    void 같은_구매를_동시에_지급해도_한_번만_반영한다() throws Exception {
        기본_데이터를_생성한다(0);
        이용권_결제를_생성한다();

        List<Throwable> results = 동시에_실행한다(
                () -> 트랜잭션에서(
                        () -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT)),
                () -> 트랜잭션에서(
                        () -> inkService.grant(READER_ID, PURCHASE_ID, OCCURRED_AT)));

        assertAll(
                () -> assertTrue(results.stream().allMatch(Objects::isNull)),
                () -> assertEquals(100, 잔액을_조회한다()),
                () -> assertEquals(1, 원장_수를_조회한다()));
    }

    @Test
    void 같은_대여를_동시에_차감해도_한_번만_반영한다() throws Exception {
        기본_데이터를_생성한다(2);
        대여_기본_데이터를_생성한다();
        페이지_대여를_생성한다(RENTAL_ID);

        List<Throwable> results = 동시에_실행한다(
                () -> 트랜잭션에서(
                        () -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT)),
                () -> 트랜잭션에서(
                        () -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT)));

        assertAll(
                () -> assertTrue(results.stream().allMatch(Objects::isNull)),
                () -> assertEquals(1, 잔액을_조회한다()),
                () -> assertEquals(1, 원장_수를_조회한다()));
    }

    @Test
    void 서로_다른_대여를_동시에_차감해도_잔액은_음수가_되지_않는다() throws Exception {
        기본_데이터를_생성한다(1);
        대여_기본_데이터를_생성한다();
        페이지_대여를_생성한다(RENTAL_ID);
        페이지_대여를_생성한다(SECOND_RENTAL_ID);

        List<Throwable> results = 동시에_실행한다(
                () -> 트랜잭션에서(
                        () -> inkService.deduct(READER_ID, RENTAL_ID, OCCURRED_AT)),
                () -> 트랜잭션에서(
                        () -> inkService.deduct(READER_ID, SECOND_RENTAL_ID, OCCURRED_AT)));

        List<Throwable> failures = results.stream().filter(Objects::nonNull).toList();
        assertAll(
                () -> assertEquals(1, failures.size()),
                () -> assertInstanceOf(InsufficientInkException.class, failures.getFirst()),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(1, 원장_수를_조회한다()));
    }

    @Test
    void 계좌가_없는_독자를_잠그면_InkAccountNotFoundException을_던진다() {
        독자를_생성한다();

        assertThrows(
                InkAccountNotFoundException.class,
                () -> 트랜잭션에서(() -> inkService.lockAccount(READER_ID)));
    }

    /**
     * SCRUM-434(2/5): {@code lockAccount}가 실제로 {@code SELECT ... FOR UPDATE}를 실행해 다른
     * 트랜잭션을 대기시키는지 증명한다. 잠금 보유 트랜잭션이 잔액을 바꾸고 커밋할 때까지 두 번째
     * 잠금 시도가 끝나지 않아야 하고, 커밋 뒤에는 바뀐 잔액을 읽어야 한다.
     */
    @Test
    void 계좌_잠금은_다른_트랜잭션이_커밋할_때까지_대기한다() throws Exception {
        기본_데이터를_생성한다(0);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(
                    status -> {
                        InkAccount account = inkService.lockAccount(READER_ID);
                        account.grant();
                        locked.countDown();
                        awaitUninterruptibly(proceed);
                    }));

            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<Integer> waiter = executor.submit(() -> transactionTemplate.execute(
                    status -> inkService.lockAccount(READER_ID).getBalance()));

            Thread.sleep(200);
            assertFalse(waiter.isDone());

            proceed.countDown();
            holder.get(5, TimeUnit.SECONDS);

            assertEquals(100, waiter.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void 트랜잭션에서(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private List<Throwable> 동시에_실행한다(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Throwable> firstResult = executor.submit(() -> 실행_결과(first, ready, start));
            Future<Throwable> secondResult = executor.submit(() -> 실행_결과(second, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return Arrays.asList(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Throwable 실행_결과(Runnable action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("동시 실행 시작을 기다리다 시간 초과했습니다.");
            }
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private void 기본_데이터를_생성한다(int balance) {
        독자를_생성한다();
        jdbcTemplate.update(
                "INSERT INTO ink_account (id, reader_id, balance) VALUES (?, ?, ?)",
                ACCOUNT_ID,
                READER_ID,
                balance);
    }

    private void 독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum-407-reader@example.com', 'hash',
                        '2026-07-29 07:00:00.000000')
                """,
                READER_ID);
    }

    private void 이용권_결제를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, '00000000-0000-0000-0000-000000407001', 'PAID', 1000, 100,
                        '2026-07-29 07:00:00.000000', '2026-07-29 07:00:00.000000')
                """,
                PURCHASE_ID,
                READER_ID);
    }

    private void 대여_기본_데이터를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-407 테스트 도서', '테스트 저자', 1, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '테스트 본문')
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
    }

    private void 페이지_대여를_생성한다(long rentalId) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2026-07-29 07:00:00.000000',
                        '2026-08-28 07:00:00.000000')
                """,
                rentalId,
                READER_ID,
                BOOK_PAGE_ID);
    }

    private int 잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 원장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private Map<String, Object> 원장을_조회한다() {
        return jdbcTemplate.queryForMap(
                """
                SELECT type, amount, balance_after, ink_purchase_id, page_rental_id,
                       DATE_FORMAT(occurred_at, '%Y-%m-%d %H:%i:%s.%f') AS occurred_at
                FROM ink_ledger
                WHERE reader_id = ?
                """,
                READER_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }
}
