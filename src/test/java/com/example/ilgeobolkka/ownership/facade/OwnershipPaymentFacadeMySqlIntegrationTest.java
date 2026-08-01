package com.example.ilgeobolkka.ownership.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.ownership.dto.CompleteOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.exception.BookAlreadyOwnedException;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    "portone.payment.enabled=true",
    "portone.payment.store-id=store-test",
    "portone.payment.channel-key=channel-test",
    "portone.payment.api-secret=test-api-secret",
    "portone.payment.webhook-secret=whsec_dGVzdC13ZWJob29rLXNlY3JldA=="
})
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(OwnershipPaymentFacadeMySqlIntegrationTest.FixedClockConfiguration.class)
class OwnershipPaymentFacadeMySqlIntegrationTest {

    private static final long READER_ID = 414_001L;
    private static final long BOOK_ID = 414_101L;
    private static final int BOOK_PRICE_WON = 15_000;
    private static final Instant CREATED_AT = Instant.parse("2026-07-31T01:00:00.123456Z");

    private final OwnershipPaymentFacade ownershipPaymentFacade;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OwnershipPaymentFacadeMySqlIntegrationTest(
            OwnershipPaymentFacade ownershipPaymentFacade,
            JdbcTemplate jdbcTemplate) {
        this.ownershipPaymentFacade = ownershipPaymentFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        도서를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_OWN_009_PENDING은_재사용하고_FAILED_뒤에는_새_결제를_준비한다() {
        OwnershipPaymentFacade.Preparation first =
                ownershipPaymentFacade.prepare(READER_ID, BOOK_ID);
        OwnershipPaymentFacade.Preparation reused =
                ownershipPaymentFacade.prepare(READER_ID, BOOK_ID);

        UUID firstPaymentId = UUID.fromString(first.response().paymentId());
        assertAll(
                () -> assertTrue(first.created()),
                () -> assertEquals("store-test", first.response().storeId()),
                () -> assertEquals("channel-test", first.response().channelKey()),
                () -> assertEquals("읽어볼까 도서 소장", first.response().orderName()),
                () -> assertEquals(BOOK_PRICE_WON, first.response().totalAmount()),
                () -> assertEquals("CURRENCY_KRW", first.response().currency()),
                () -> assertTrue(!reused.created()),
                () -> assertEquals(first.response(), reused.response()),
                () -> assertEquals("PENDING", 결제_상태를_조회한다(firstPaymentId)),
                () -> assertEquals(
                        "2026-07-31 01:00:00.123456",
                        결제_생성_시각을_조회한다(firstPaymentId)),
                () -> assertEquals(1, 결제_수를_조회한다()));

        jdbcTemplate.update(
                "UPDATE ownership_payment SET status = 'FAILED' WHERE payment_id = ?",
                firstPaymentId.toString());

        OwnershipPaymentFacade.Preparation retried =
                ownershipPaymentFacade.prepare(READER_ID, BOOK_ID);
        UUID retriedPaymentId = UUID.fromString(retried.response().paymentId());

        assertAll(
                () -> assertTrue(retried.created()),
                () -> assertNotEquals(firstPaymentId, retriedPaymentId),
                () -> assertEquals("FAILED", 결제_상태를_조회한다(firstPaymentId)),
                () -> assertEquals("PENDING", 결제_상태를_조회한다(retriedPaymentId)),
                () -> assertEquals(2, 결제_수를_조회한다()),
                () -> assertEquals(70, 잉크_잔액을_조회한다()));
    }

    @Test
    void 이미_소장한_도서는_새_결제를_만들지_않는다() {
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', ?, '2026-07-31 00:00:00.000000',
                        '2026-07-31 00:01:00.000000')
                """,
                READER_ID,
                BOOK_ID,
                paymentId.toString(),
                BOOK_PRICE_WON);
        long ownershipPaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM ownership_payment WHERE payment_id = ?",
                Long.class,
                paymentId.toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-07-31 00:01:00.000000')
                """,
                READER_ID,
                BOOK_ID,
                ownershipPaymentId);

        assertThrows(
                BookAlreadyOwnedException.class,
                () -> ownershipPaymentFacade.prepare(READER_ID, BOOK_ID));
        assertEquals(1, 결제_수를_조회한다());
    }

    @Test
    void 기존_PAID_결제의_누락된_서재_항목을_완료_재조회로_복구한다() {
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', ?, '2026-07-31 00:00:00.000000',
                        '2026-07-31 00:01:00.123456')
                """,
                READER_ID,
                BOOK_ID,
                paymentId.toString(),
                BOOK_PRICE_WON);
        long ownershipPaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM ownership_payment WHERE payment_id = ?",
                Long.class,
                paymentId.toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-07-31 00:01:00.123456')
                """,
                READER_ID,
                BOOK_ID,
                ownershipPaymentId);

        CompleteOwnershipPaymentResponse response =
                ownershipPaymentFacade.complete(READER_ID, paymentId);

        assertAll(
                () -> assertEquals("PAID", response.status().name()),
                () -> assertTrue(response.owned()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()),
                () -> assertEquals(1, 서재_마지막_페이지를_조회한다()),
                () -> assertEquals(
                        "2026-07-31 00:01:00.123456",
                        서재_갱신_시각을_조회한다()));
    }

    @Test
    void 동시에_준비해도_같은_PENDING_결제를_재사용한다() throws Exception {
        List<OwnershipPaymentFacade.Preparation> preparations = 동시에_준비한다();

        assertAll(
                () -> assertEquals(
                        1,
                        preparations.stream()
                                .filter(OwnershipPaymentFacade.Preparation::created)
                                .count()),
                () -> assertEquals(
                        1,
                        preparations.stream()
                                .map(preparation -> preparation.response().paymentId())
                                .distinct()
                                .count()),
                () -> assertEquals(1, PENDING_결제_수를_조회한다()));
    }

    private List<OwnershipPaymentFacade.Preparation> 동시에_준비한다() throws Exception {
        int concurrentRequestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequestCount);
        CountDownLatch ready = new CountDownLatch(concurrentRequestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<OwnershipPaymentFacade.Preparation> action = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return ownershipPaymentFacade.prepare(READER_ID, BOOK_ID);
            };
            List<Future<OwnershipPaymentFacade.Preparation>> futures = new ArrayList<>();
            for (int index = 0; index < concurrentRequestCount; index++) {
                futures.add(executor.submit(action));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<OwnershipPaymentFacade.Preparation> preparations = new ArrayList<>();
            for (Future<OwnershipPaymentFacade.Preparation> future : futures) {
                preparations.add(future.get(10, TimeUnit.SECONDS));
            }
            return preparations;
        } finally {
            executor.shutdownNow();
        }
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum414@example.com', '{noop}password',
                        '2026-07-31 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 70)",
                READER_ID);
    }

    private void 도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', '동시성의 숲', '읽어볼까', 4, ?)
                """,
                BOOK_ID,
                BOOK_PRICE_WON);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content)
                VALUES (?, 1, 'TEXT', '첫 페이지')
                """,
                BOOK_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update(
                "DELETE FROM library_entry WHERE reader_id = ? AND book_id = ?",
                READER_ID,
                BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM book_ownership WHERE reader_id = ? AND book_id = ?",
                READER_ID,
                BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM ownership_payment WHERE reader_id = ? AND book_id = ?",
                READER_ID,
                BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }

    private String 결제_상태를_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ownership_payment WHERE payment_id = ?",
                String.class,
                paymentId.toString());
    }

    private String 결제_생성_시각을_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM ownership_payment
                WHERE payment_id = ?
                """,
                String.class,
                paymentId.toString());
    }

    private int 결제_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ownership_payment WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    private int PENDING_결제_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ownership_payment
                WHERE reader_id = ? AND book_id = ? AND status = 'PENDING'
                """,
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    private int 잉크_잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 서재_항목_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    private int 서재_마지막_페이지를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT last_page_number FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    private String 서재_갱신_시각을_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM library_entry
                WHERE reader_id = ? AND book_id = ?
                """,
                String.class,
                READER_ID,
                BOOK_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        }
    }
}
