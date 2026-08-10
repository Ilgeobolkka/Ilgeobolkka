package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.ilgeobolkka.airoute.service.generation.GenerationStartResult.Kind;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 한도 경계는 실제 동시 요청으로만 증명된다. 테스트 클래스에 {@code @Transactional}을 붙이지 않는 이유도
 * 같다. 붙이면 모든 작업이 한 트랜잭션에 갇혀 commit·rollback 결과를 볼 수 없다.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=12")
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteGenerationStartMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRouteGenerationStartMySqlIntegrationTest {

    private static final int LIMIT = AiRouteGenerationStartService.DAILY_GENERATION_LIMIT;

    /** 여러 독자가 빈 인덱스에 동시에 insert 하는 상황을 만들려고 넷을 쓴다. */
    private static final List<Long> READER_IDS = List.of(458_001L, 458_002L, 458_003L, 458_004L);

    private static final long READER_ID = READER_IDS.get(0);
    private static final long OTHER_READER_ID = READER_IDS.get(1);
    private static final long BOOK_ID = 458_101L;
    private static final long MISSING_BOOK_ID = 458_999L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String PURPOSE = "핵심 개념만 빠르게";

    /**
     * 이 두 시각은 UTC로는 날짜가 갈리지만 테스트 JVM 기본 시간대인 KST로는 둘 다 2026-08-07이다.
     * ({@code build.gradle}이 {@code user.timezone=Asia/Seoul}로 띄운다.) 계수 기준을 시스템 시간대로
     * 바꾸면 UTC 자정 테스트만 깨진다.
     */
    private static final Instant STARTED_AT = Instant.parse("2026-08-06T23:59:59.123456Z");

    private static final Instant NEXT_UTC_DAY = Instant.parse("2026-08-07T00:00:00.123456Z");
    private static final LocalDate USAGE_DATE = LocalDate.of(2026, 8, 6);
    private static final LocalDate NEXT_USAGE_DATE = LocalDate.of(2026, 8, 7);

    private static final Instant COMPLETED_AT = Instant.parse("2026-08-07T00:00:09.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-07T00:15:09.123456Z");

    private final AiRouteGenerationStartService startService;
    private final AiRouteGenerationRepository generationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MutableClock clock;

    @Autowired
    AiRouteGenerationStartMySqlIntegrationTest(
            AiRouteGenerationStartService startService,
            AiRouteGenerationRepository generationRepository,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MutableClock clock) {
        this.startService = startService;
        this.generationRepository = generationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        READER_IDS.forEach(this::독자를_생성한다);
        도서를_생성한다();
        clock.set(STARTED_AT);
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    // --- 멱등 -------------------------------------------------------------

    @Test
    void 같은_키_같은_입력을_두_번_시작하면_생성이_하나만_남는다() {
        UUID key = UUID.randomUUID();

        GenerationStartResult first = startService.start(READER_ID, key, 명령(PURPOSE));
        GenerationStartResult second = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, first.kind()),
                () -> assertEquals(Kind.EXISTING_GENERATING, second.kind()),
                () -> assertEquals(first.generationId(), second.generationId()),
                () -> assertEquals(AiRouteGenerationStatus.GENERATING, second.status()),
                () -> assertNull(second.expiresAt()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 같은_키_같은_입력의_동시_요청도_생성이_하나만_남는다() throws Exception {
        UUID key = UUID.randomUUID();

        List<GenerationStartResult> results = 동시에_시작한다(READER_ID, List.of(key, key));

        assertAll(
                () -> assertEquals(1, 개수(results, Kind.NEW)),
                () -> assertEquals(1, 개수(results, Kind.EXISTING_GENERATING)),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 같은_키에_다른_입력이_오면_거절하고_횟수를_쓰지_않는다() {
        UUID key = UUID.randomUUID();
        startService.start(READER_ID, key, 명령(PURPOSE));

        GenerationStartResult reused = startService.start(READER_ID, key, 명령("다른 목적으로 읽는다"));

        assertAll(
                () -> assertEquals(Kind.KEY_REUSED, reused.kind()),
                () -> assertNull(reused.generationId()),
                () -> assertNull(reused.status()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 실패로_끝난_생성을_다시_시작해도_횟수를_더_쓰지_않는다() {
        UUID key = UUID.randomUUID();
        GenerationStartResult started = startService.start(READER_ID, key, 명령(PURPOSE));
        생성을_실패로_끝낸다(started.generationId());

        GenerationStartResult retried = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.EXISTING_FINAL, retried.kind()),
                () -> assertEquals(AiRouteGenerationStatus.FAILED, retried.status()),
                () -> assertEquals(EXPIRES_AT, retried.expiresAt()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    /**
     * 생성 인덱스가 비어 있을 때 여러 독자가 동시에 처음 insert 하는 상황이다. 존재를 확인하지 않고
     * 없는 행에 바로 잠금을 걸면 각자 gap lock을 쥔 채 서로의 insert를 기다려 교착한다. 그때는 결과가
     * 아니라 예외가 올라와 {@code future.get()} 에서 터진다.
     */
    @Test
    void 여러_독자가_빈_테이블에서_동시에_시작해도_각자_한_건씩_만든다() throws Exception {
        List<GenerationStartResult> results = 독자별로_동시에_시작한다(READER_IDS);

        assertAll(
                () -> assertEquals(READER_IDS.size(), 개수(results, Kind.NEW)),
                () ->
                        assertEquals(
                                Collections.nCopies(READER_IDS.size(), 1),
                                READER_IDS.stream().map(this::생성_수를_조회한다).toList()),
                () ->
                        assertEquals(
                                Collections.nCopies(READER_IDS.size(), 1),
                                READER_IDS.stream()
                                        .map(readerId -> 사용량을_조회한다(readerId, USAGE_DATE))
                                        .toList()));
    }

    @Test
    void 다른_독자가_같은_멱등_키를_써도_각자_새로_시작한다() {
        UUID key = UUID.randomUUID();

        GenerationStartResult mine = startService.start(READER_ID, key, 명령(PURPOSE));
        GenerationStartResult others = startService.start(OTHER_READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, mine.kind()),
                () -> assertEquals(Kind.NEW, others.kind()),
                () -> assertNotEquals(mine.generationId(), others.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 생성_수를_조회한다(OTHER_READER_ID)));
    }

    // --- 일일 한도 ---------------------------------------------------------

    @Test
    void 한도_직전에는_시작하고_한도에_닿으면_거절한다() {
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT - 1);

        GenerationStartResult last = startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));
        GenerationStartResult overLimit =
                startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, last.kind()),
                () -> assertEquals(Kind.DAILY_LIMIT, overLimit.kind()),
                () -> assertNull(overLimit.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 다른_키_열한_개가_동시에_들어와도_열_건만_시작한다() throws Exception {
        List<UUID> keys = Stream.generate(UUID::randomUUID).limit(LIMIT + 1L).toList();

        List<GenerationStartResult> results = 동시에_시작한다(READER_ID, keys);

        assertAll(
                () -> assertEquals(LIMIT, 개수(results, Kind.NEW)),
                () -> assertEquals(1, 개수(results, Kind.DAILY_LIMIT)),
                () -> assertEquals(LIMIT, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 한_독자가_한도를_다_써도_다른_독자는_시작할_수_있다() {
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT);

        GenerationStartResult blocked =
                startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));
        GenerationStartResult allowed =
                startService.start(OTHER_READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.DAILY_LIMIT, blocked.kind()),
                () -> assertEquals(Kind.NEW, allowed.kind()),
                () -> assertEquals(1, 사용량을_조회한다(OTHER_READER_ID, USAGE_DATE)));
    }

    @Test
    void UTC_자정을_넘기면_사용량을_새로_센다() {
        startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        clock.set(NEXT_UTC_DAY);
        startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        assertAll(
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, NEXT_USAGE_DATE)),
                () -> assertEquals(2, 생성_수를_조회한다(READER_ID)));
    }

    // --- 저장 내용과 트랜잭션 경계 -----------------------------------------

    @Test
    void 새_생성은_지문과_정규화한_목적을_시작_시각과_함께_저장한다() {
        AiRouteGenerationCommand command = 명령("  핵심   개념만 빠르게 ");

        GenerationStartResult started = startService.start(READER_ID, UUID.randomUUID(), command);

        Map<String, Object> row = 생성을_조회한다(started.generationId());
        assertAll(
                () ->
                        assertEquals(
                                AiRouteRequestFingerprint.of(command),
                                row.get("request_fingerprint")),
                () -> assertEquals("GENERATING", row.get("status")),
                () -> assertEquals(PURPOSE, row.get("normalized_purpose")),
                () -> assertEquals("2026-08-06 23:59:59.123456", row.get("created_at")),
                () -> assertNull(row.get("expires_at")));
    }

    @Test
    void 호출자가_트랜잭션을_열면_시작하지_않는다() {
        UUID key = UUID.randomUUID();

        assertThrows(
                IllegalTransactionStateException.class,
                () ->
                        transactionTemplate.executeWithoutResult(
                                status -> startService.start(READER_ID, key, 명령(PURPOSE))));
        assertAll(
                () -> assertEquals(0, 생성_수를_조회한다(READER_ID)),
                () -> assertNull(사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 생성_저장에_실패하면_사용량도_남지_않는다() {
        AiRouteGenerationCommand 없는_도서 =
                AiRouteGenerationCommand.forInkBudget(
                        MISSING_BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> startService.start(READER_ID, UUID.randomUUID(), 없는_도서));
        assertAll(
                () -> assertEquals(0, 생성_수를_조회한다(READER_ID)),
                () -> assertNull(사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 멱등_키가_없으면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> startService.start(READER_ID, null, 명령(PURPOSE)));
    }

    // --- 동시 실행 ---------------------------------------------------------

    private List<GenerationStartResult> 동시에_시작한다(long readerId, List<UUID> keys)
            throws Exception {
        return 동시에_실행한다(
                keys.stream()
                        .map(
                                key ->
                                        (Callable<GenerationStartResult>)
                                                () -> startService.start(readerId, key, 명령(PURPOSE)))
                        .toList());
    }

    private List<GenerationStartResult> 독자별로_동시에_시작한다(List<Long> readerIds) throws Exception {
        return 동시에_실행한다(
                readerIds.stream()
                        .map(
                                readerId ->
                                        (Callable<GenerationStartResult>)
                                                () ->
                                                        startService.start(
                                                                readerId,
                                                                UUID.randomUUID(),
                                                                명령(PURPOSE)))
                        .toList());
    }

    private List<GenerationStartResult> 동시에_실행한다(List<Callable<GenerationStartResult>> calls)
            throws Exception {
        int count = calls.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<GenerationStartResult>> futures = new ArrayList<>();
            for (Callable<GenerationStartResult> call : calls) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return call.call();
                                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<GenerationStartResult> results = new ArrayList<>();
            for (Future<GenerationStartResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static long 개수(List<GenerationStartResult> results, Kind kind) {
        return results.stream().filter(result -> result.kind() == kind).count();
    }

    // --- fixture -----------------------------------------------------------

    private static AiRouteGenerationCommand 명령(String rawPurpose) {
        return AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, rawPurpose, 5, 100);
    }

    private void 생성을_실패로_끝낸다(UUID generationId) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    AiRouteGeneration generation =
                            generationRepository.findById(generationId).orElseThrow();
                    generation.fail("AI_ROUTE_TEST_FAILURE", COMPLETED_AT, EXPIRES_AT);
                });
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'hash', '2026-08-06 00:00:00.000000')
                """,
                readerId,
                "scrum-458-reader-" + readerId + "@example.com");
    }

    private void 도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-458 테스트 도서', '테스트 저자', 1, 10000)
                """,
                BOOK_ID);
    }

    private void 사용량을_심는다(long readerId, LocalDate usageDate, int generationCount) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_daily_usage (reader_id, usage_date, generation_count)
                VALUES (?, ?, ?)
                """,
                readerId,
                usageDate.toString(),
                generationCount);
    }

    private int 생성_수를_조회한다(long readerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation WHERE reader_id = ?",
                Integer.class,
                readerId);
    }

    private Integer 사용량을_조회한다(long readerId, LocalDate usageDate) {
        List<Integer> counts =
                jdbcTemplate.queryForList(
                        """
                        SELECT generation_count
                        FROM ai_route_daily_usage
                        WHERE reader_id = ? AND usage_date = ?
                        """,
                        Integer.class,
                        readerId,
                        usageDate.toString());
        return counts.isEmpty() ? null : counts.getFirst();
    }

    private Map<String, Object> 생성을_조회한다(UUID generationId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT request_fingerprint, status, normalized_purpose, expires_at,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at
                FROM ai_route_generation
                WHERE generation_id = ?
                """,
                generationId.toString());
    }

    private void 테스트_데이터를_정리한다() {
        for (long readerId : READER_IDS) {
            jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id = ?", readerId);
        }
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        for (long readerId : READER_IDS) {
            jdbcTemplate.update("DELETE FROM reader WHERE id = ?", readerId);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(STARTED_AT);
        }
    }

    /**
     * UTC 자정 경계를 한 컨텍스트 안에서 넘나들어야 해서 {@link Clock#fixed} 대신 옮길 수 있는 시계를
     * 쓴다. 동시 실행 테스트가 다른 스레드에서 읽으므로 {@code volatile} 이다.
     */
    static class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }
    }
}
