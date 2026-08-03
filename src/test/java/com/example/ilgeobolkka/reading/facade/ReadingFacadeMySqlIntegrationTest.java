package com.example.ilgeobolkka.reading.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.book.exception.BookPageNotFoundException;
import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.ink.repository.InkAccountRepository;
import com.example.ilgeobolkka.ink.repository.InkLedgerRepository;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingFacadeMySqlIntegrationTest.ReadingFacadeTestConfiguration.class)
// T-RENT-005가 동시 요청 10건을 띄우므로 이 컨텍스트에서만 기본 풀 10을 넘긴다.
// 전역으로 올리면 컨텍스트 수만큼 곱해져 MySQL max_connections를 넘는다.
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=15")
class ReadingFacadeMySqlIntegrationTest {

    private static final long READER_ID = 411_001L;
    private static final long RENTAL_BOOK_ID = 411_101L;
    private static final long OWNED_BOOK_ID = 411_102L;
    private static final long TEXT_PAGE_ID = 411_201L;
    private static final long IMAGE_PAGE_ID = 411_202L;
    private static final long OWNED_PAGE_ID = 411_203L;
    private static final long OWNERSHIP_PAYMENT_ID = 411_301L;
    private static final int CONCURRENT_REQUEST_COUNT = 10;
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00.123456Z");
    private static final Duration RENTAL_PERIOD = Duration.ofDays(30);
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;
    private final ScriptedClock clock;
    private final TransactionTemplate transactionTemplate;
    private final ProbedInkService probedInkService;

    @Autowired
    ReadingFacadeMySqlIntegrationTest(
            ReadingFacade readingFacade,
            JdbcTemplate jdbcTemplate,
            ScriptedClock clock,
            PlatformTransactionManager transactionManager,
            ProbedInkService probedInkService) {
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.probedInkService = probedInkService;
    }

    @BeforeEach
    void setUp() {
        clock.reset();
        테스트_데이터를_정리한다();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
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
                () -> assertEquals(1, 세션_현재_페이지를_조회한다()),
                () -> assertEquals(DATETIME_FORMATTER.format(NOW), 세션_갱신_시각을_조회한다()));
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
    void T_RENT_006_응답_유실_후_같은_페이지를_재시도하면_활성_대여를_재사용한다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();
        OpenPageResponse lostResponse =
                readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        OpenPageResponse retried =
                readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertEquals(1, lostResponse.deductedInk()),
                () -> assertEquals(0, retried.deductedInk()),
                () -> assertEquals(lostResponse.rentedAt(), retried.rentedAt()),
                () -> assertEquals(lostResponse.expiresAt(), retried.expiresAt()),
                () -> assertNotEquals(lostResponse.viewerSessionId(), retried.viewerSessionId()),
                () -> assertEquals(4, retried.inkBalance()),
                () -> assertEquals(4, 잔액을_조회한다()),
                () -> assertEquals(1, 대여_수를_조회한다()),
                () -> assertEquals(1, 차감_원장_수를_조회한다()),
                () -> assertEquals(1, 세션_수를_조회한다()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()));
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
    void T_RENT_003_만료_1밀리초_전에는_무료_재열람이고_정확한_만료_시각에는_새_대여를_시작한다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();
        Instant 만료_시각 = NOW.plusSeconds(10);
        활성_대여를_생성한다(TEXT_PAGE_ID, 만료_시각.minus(RENTAL_PERIOD), 만료_시각);
        clock.script(만료_시각.minusMillis(1), 만료_시각, 만료_시각);

        OpenPageResponse beforeExpiry = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);
        OpenPageResponse atExpiry = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertEquals(0, beforeExpiry.deductedInk()),
                () -> assertEquals(만료_시각.minus(RENTAL_PERIOD), beforeExpiry.rentedAt()),
                () -> assertEquals(만료_시각, beforeExpiry.expiresAt()),
                () -> assertEquals(1, atExpiry.deductedInk()),
                () -> assertEquals(만료_시각, atExpiry.rentedAt()),
                () -> assertEquals(만료_시각.plus(RENTAL_PERIOD), atExpiry.expiresAt()),
                () -> assertEquals(4, atExpiry.inkBalance()),
                () -> assertEquals(2, 대여_수를_조회한다()),
                () -> assertEquals(1, 차감_원장_수를_조회한다()));
    }

    @Test
    void T_RENT_004_시작_시각이_다른_두_페이지는_각각_독립적으로_30일_뒤_만료된다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();
        Instant 첫_페이지_차감_시각 = NOW;
        Instant 둘째_페이지_차감_시각 = NOW.plusSeconds(3600);

        clock.script(첫_페이지_차감_시각, 첫_페이지_차감_시각);
        OpenPageResponse first = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);
        clock.script(둘째_페이지_차감_시각, 둘째_페이지_차감_시각);
        OpenPageResponse second =
                readingFacade.movePage(READER_ID, UUID.fromString(first.viewerSessionId()), 2);

        assertAll(
                () -> assertEquals(첫_페이지_차감_시각.plus(RENTAL_PERIOD), first.expiresAt()),
                () -> assertEquals(둘째_페이지_차감_시각.plus(RENTAL_PERIOD), second.expiresAt()),
                () -> assertNotEquals(first.expiresAt(), second.expiresAt()),
                () -> assertEquals(2, 대여_수를_조회한다()),
                () -> assertEquals(2, 차감_원장_수를_조회한다()));
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
    void T_OWN_007_잉크_0인_소장_도서의_미대여_페이지는_차감_없이_제공된다() {
        독자를_생성한다(0);
        소장용_도서를_생성한다();

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, OWNED_BOOK_ID, 1);

        assertAll(
                () -> assertTrue(response.owned()),
                () -> assertEquals(0, response.deductedInk()),
                () -> assertEquals(0, response.inkBalance()),
                () -> assertNull(response.rentedAt()),
                () -> assertNull(response.expiresAt()),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 대여_수를_조회한다()));
    }

    @Test
    void T_OWN_008_소장_전_대여가_만료된_뒤에도_소장_도서는_새_대여_없이_제공된다() {
        독자를_생성한다(3);
        소장용_도서를_생성한다();
        활성_대여를_생성한다(
                OWNED_PAGE_ID, NOW.minus(RENTAL_PERIOD).minusSeconds(3600), NOW.minusSeconds(3600));

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, OWNED_BOOK_ID, 1);

        assertAll(
                () -> assertTrue(response.owned()),
                () -> assertEquals(0, response.deductedInk()),
                () -> assertEquals(3, response.inkBalance()),
                () -> assertNull(response.rentedAt()),
                () -> assertNull(response.expiresAt()),
                () -> assertEquals(3, 잔액을_조회한다()),
                () -> assertEquals(1, 대여_수를_조회한다()));
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

    @RepeatedTest(5)
    void T_RENT_005_같은_페이지를_동시에_열어도_1잉크만_차감하고_대여도_하나만_만든다() throws Exception {
        독자를_생성한다(5);
        대여용_도서를_생성한다();

        List<OpenPageResponse> responses =
                동시에_같은_페이지를_연다(CONCURRENT_REQUEST_COUNT, RENTAL_BOOK_ID);

        int 차감_합계 = responses.stream().mapToInt(OpenPageResponse::deductedInk).sum();
        assertAll(
                () -> assertEquals(CONCURRENT_REQUEST_COUNT, responses.size()),
                () -> assertEquals(1, 차감_합계),
                () -> assertEquals(4, 잔액을_조회한다()),
                () -> assertEquals(1, 대여_수를_조회한다()),
                () -> assertEquals(1, 차감_원장_수를_조회한다()),
                () -> assertEquals(1, 세션_수를_조회한다()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()),
                () -> assertEquals(1, responses.stream().map(OpenPageResponse::rentedAt).distinct().count()),
                () -> assertEquals(1, responses.stream().map(OpenPageResponse::expiresAt).distinct().count()));
    }

    /**
     * 선행 트랜잭션의 미커밋 대여는 첫 조회에 보이지 않는다. 페이지 열기가 계좌 잠금을 기다린 뒤
     * 커밋된 활성 대여를 다시 읽어야 중복 대여와 차감을 막을 수 있다.
     */
    @Test
    void 잠금_뒤_재확인은_선행_트랜잭션이_만든_활성_대여를_재사용한다() throws Exception {
        독자를_생성한다(1);
        대여용_도서를_생성한다();
        Instant rentedAt = NOW.minusSeconds(3600);
        Instant expiresAt = NOW.plusSeconds(3600);
        CountDownLatch rentalPrepared = new CountDownLatch(1);
        CountDownLatch accountLockRequested = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        ExecutorService lockHolder = Executors.newSingleThreadExecutor();
        ExecutorService pageOpener = Executors.newSingleThreadExecutor();

        try {
            Future<?> heldTransaction = lockHolder.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject(
                                "SELECT balance FROM ink_account WHERE reader_id = ? FOR UPDATE",
                                Integer.class,
                                READER_ID);
                        활성_대여를_생성한다(TEXT_PAGE_ID, rentedAt, expiresAt);
                        rentalPrepared.countDown();
                        래치를_기다린다(allowCommit);
                    }));
            assertTrue(rentalPrepared.await(5, TimeUnit.SECONDS));
            probedInkService.signalOnNextAccountLock(accountLockRequested);

            Future<OpenPageResponse> opening = pageOpener.submit(
                    () -> readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1));

            assertTrue(accountLockRequested.await(5, TimeUnit.SECONDS));

            allowCommit.countDown();
            heldTransaction.get(5, TimeUnit.SECONDS);
            OpenPageResponse response = opening.get(20, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(0, response.deductedInk()),
                    () -> assertEquals(1, response.inkBalance()),
                    () -> assertEquals(rentedAt, response.rentedAt()),
                    () -> assertEquals(expiresAt, response.expiresAt()),
                    () -> assertEquals(1, 잔액을_조회한다()),
                    () -> assertEquals(1, 대여_수를_조회한다()),
                    () -> assertEquals(0, 차감_원장_수를_조회한다()),
                    () -> assertEquals(1, 세션_수를_조회한다()),
                    () -> assertEquals(1, 서재_항목_수를_조회한다()));
        } finally {
            allowCommit.countDown();
            probedInkService.clearAccountLockSignal();
            lockHolder.shutdownNow();
            pageOpener.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void 잔액_1에서_서로_다른_미대여_페이지를_동시에_열면_정확히_하나만_성공한다() throws Exception {
        독자를_생성한다(1);
        대여용_도서를_생성한다();

        List<PageOpenResult> results = 동시에_서로_다른_페이지를_연다();

        List<OpenPageResponse> successes =
                results.stream()
                        .map(PageOpenResult::response)
                        .filter(response -> response != null)
                        .toList();
        List<RuntimeException> failures =
                results.stream()
                        .map(PageOpenResult::failure)
                        .filter(failure -> failure != null)
                        .toList();
        assertAll(
                () -> assertEquals(1, successes.size()),
                () -> assertEquals(1, failures.size()),
                () -> assertInstanceOf(InsufficientInkException.class, failures.getFirst()),
                () -> assertEquals(1, successes.getFirst().deductedInk()),
                () -> assertEquals(0, successes.getFirst().inkBalance()),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(1, 대여_수를_조회한다()),
                () -> assertEquals(1, 차감_원장_수를_조회한다()),
                () -> assertEquals(1, 세션_수를_조회한다()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()),
                () ->
                        assertEquals(
                                successes.getFirst().pageNumber(),
                                서재_마지막_페이지를_조회한다(RENTAL_BOOK_ID)));
    }

    @Test
    void 잠금을_기다리는_동안_시각이_흘러도_대여는_차감_시각부터_30일이다() {
        독자를_생성한다(5);
        대여용_도서를_생성한다();
        Instant 차감_시각 = NOW.plusSeconds(5);
        clock.script(NOW, 차감_시각);

        OpenPageResponse response = readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, 1);

        assertAll(
                () -> assertEquals(차감_시각, response.rentedAt()),
                () -> assertEquals(차감_시각.plus(RENTAL_PERIOD), response.expiresAt()),
                () -> assertEquals(DATETIME_FORMATTER.format(차감_시각), 대여_시작_시각을_조회한다()),
                () -> assertEquals(DATETIME_FORMATTER.format(차감_시각), 차감_원장_시각을_조회한다()));
    }

    @Test
    void 소장_도서를_동시에_열어도_세션과_서재_항목은_하나씩만_남는다() throws Exception {
        독자를_생성한다(3);
        소장용_도서를_생성한다();

        List<OpenPageResponse> responses =
                동시에_같은_페이지를_연다(CONCURRENT_REQUEST_COUNT, OWNED_BOOK_ID);

        assertAll(
                () -> assertTrue(responses.stream().allMatch(OpenPageResponse::owned)),
                () -> assertEquals(3, 잔액을_조회한다()),
                () -> assertEquals(0, 대여_수를_조회한다()),
                () -> assertEquals(0, 차감_원장_수를_조회한다()),
                () -> assertEquals(1, 세션_수를_조회한다()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()));
    }

    /** 잠금 뒤 재확인이 실제로 동작하는지 보려면 모든 요청이 잠금 전 첫 확인을 함께 통과해야 한다. */
    private List<OpenPageResponse> 동시에_같은_페이지를_연다(int 요청_수, long bookId) throws Exception {
        CyclicBarrier 출발선 = new CyclicBarrier(요청_수);
        ExecutorService executor = Executors.newFixedThreadPool(요청_수);
        try {
            List<Future<OpenPageResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 요청_수; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    출발선.await(5, TimeUnit.SECONDS);
                                    return readingFacade.openNewSession(READER_ID, bookId, 1);
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

    private List<PageOpenResult> 동시에_서로_다른_페이지를_연다() throws Exception {
        CyclicBarrier 출발선 = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<PageOpenResult>> futures =
                    List.of(
                            executor.submit(() -> 페이지를_연다(출발선, 1)),
                            executor.submit(() -> 페이지를_연다(출발선, 2)));

            List<PageOpenResult> results = new ArrayList<>();
            for (Future<PageOpenResult> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private PageOpenResult 페이지를_연다(CyclicBarrier 출발선, int pageNumber) throws Exception {
        출발선.await(5, TimeUnit.SECONDS);
        try {
            return PageOpenResult.success(
                    readingFacade.openNewSession(READER_ID, RENTAL_BOOK_ID, pageNumber));
        } catch (RuntimeException failure) {
            return PageOpenResult.failure(failure);
        }
    }

    private void 래치를_기다린다(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 래치 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 래치 대기가 중단되었습니다.", e);
        }
    }

    private record PageOpenResult(OpenPageResponse response, RuntimeException failure) {

        static PageOpenResult success(OpenPageResponse response) {
            return new PageOpenResult(response, null);
        }

        static PageOpenResult failure(RuntimeException failure) {
            return new PageOpenResult(null, failure);
        }
    }

    /**
     * 독자와 잉크 계정은 지우지 않고 갱신해 재사용한다. 매번 삭제 후 같은 {@code reader_id}로 다시
     * 넣으면 purge 전의 삭제 표시 인덱스 레코드가 남고, 동시 요청의 {@code FOR UPDATE} 스캔이 그
     * 레코드까지 잠그면서 교착이 생긴다. 운영에서는 잉크 계정을 삭제하지 않으므로 없는 상황이다.
     */
    private void 독자를_생성한다(int balance) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum411@example.com', '{noop}password', '2026-07-30 00:00:00.000000')
                AS incoming
                ON DUPLICATE KEY UPDATE password_hash = incoming.password_hash
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)
                AS incoming
                ON DUPLICATE KEY UPDATE balance = incoming.balance
                """,
                READER_ID,
                balance);
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

    private String 대여_시작_시각을_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(rented_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM page_rental WHERE reader_id = ?
                """,
                String.class,
                READER_ID);
    }

    private String 차감_원장_시각을_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(occurred_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM ink_ledger WHERE reader_id = ? AND type = 'DEDUCTION'
                """,
                String.class,
                READER_ID);
    }

    /** 네이티브 upsert가 `Instant`를 UTC로 저장하는지 확인하기 위해 저장된 문자열을 그대로 읽는다. */
    private String 세션_갱신_시각을_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM reading_session WHERE reader_id = ?
                """,
                String.class,
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
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id IN (?, ?)", RENTAL_BOOK_ID, OWNED_BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", RENTAL_BOOK_ID, OWNED_BOOK_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReadingFacadeTestConfiguration {

        @Bean
        @Primary
        ScriptedClock scriptedClock() {
            return new ScriptedClock();
        }

        @Bean
        @Primary
        ProbedInkService probedInkService(
                InkAccountRepository inkAccountRepository, InkLedgerRepository inkLedgerRepository) {
            return new ProbedInkService(inkAccountRepository, inkLedgerRepository);
        }
    }

    static class ProbedInkService extends InkService {

        private final AtomicReference<CountDownLatch> accountLockSignal = new AtomicReference<>();

        ProbedInkService(
                InkAccountRepository inkAccountRepository, InkLedgerRepository inkLedgerRepository) {
            super(inkAccountRepository, inkLedgerRepository);
        }

        void signalOnNextAccountLock(CountDownLatch signal) {
            accountLockSignal.set(signal);
        }

        void clearAccountLockSignal() {
            accountLockSignal.set(null);
        }

        @Override
        public void lockAccount(long readerId) {
            CountDownLatch signal = accountLockSignal.getAndSet(null);
            if (signal != null) {
                signal.countDown();
            }
            super.lockAccount(readerId);
        }
    }

    /**
     * 기본값은 항상 {@link #NOW}라 고정 시계와 같다. {@link #script}로 호출 순서별 시각을 지정하면
     * 잠금 전 확인과 잠금 뒤 차감이 서로 다른 시각을 쓰는지 검증할 수 있다.
     */
    static final class ScriptedClock extends Clock {

        private final Queue<Instant> scripted = new ConcurrentLinkedQueue<>();

        void script(Instant... instants) {
            scripted.clear();
            scripted.addAll(List.of(instants));
        }

        void reset() {
            scripted.clear();
        }

        @Override
        public Instant instant() {
            Instant next = scripted.poll();
            return next == null ? NOW : next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
