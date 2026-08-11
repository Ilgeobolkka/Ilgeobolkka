package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 만료 경계와 정리 전후 동작을 실제 MySQL 로 확인한다. 클래스에 {@code @Transactional} 을 붙이지 않는다.
 * 붙이면 모든 작업이 한 transaction 에 갇혀 정리의 commit·rollback 결과를 볼 수 없다.
 *
 * <p>유지보수 스케줄러는 {@code ai-route.enabled} 기본값이 거짓이라 등록되지 않는다. 배치가 테스트와
 * 겹쳐 도는 일이 없으므로 정리·복구는 테스트가 직접 부른다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteGenerationLifecycleMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRouteGenerationLifecycleMySqlIntegrationTest {

    private static final long READER_ID = 462_001L;
    private static final long OTHER_READER_ID = 462_002L;
    private static final long BOOK_ID = 462_101L;
    private static final long FIRST_PAGE_ID = 462_201L;
    private static final long SECOND_PAGE_ID = 462_202L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String PURPOSE = "핵심 개념만 빠르게";
    private static final int BUDGET = 5;

    private static final Instant STARTED_AT = Instant.parse("2026-08-06T00:00:00.123456Z");
    private static final Instant COMPLETED_AT = STARTED_AT.plusSeconds(5);
    private static final Instant EXPIRES_AT =
            COMPLETED_AT.plus(AiRouteGenerationLifecycleService.RESULT_RETENTION);

    /** 전체 제한을 막 넘긴 시각. 이때부터 {@code GENERATING} 은 버려진 것으로 본다. */
    private static final Instant ABANDONED_AT =
            STARTED_AT.plus(AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT).plusSeconds(1);

    private final AiRouteGenerationLifecycleService lifecycleService;
    private final AiRouteGenerationCleanupService cleanupService;
    private final AiRouteGenerationRepository generationRepository;
    private final AiReadingRouteRepository readingRouteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MutableClock clock;

    @Autowired
    AiRouteGenerationLifecycleMySqlIntegrationTest(
            AiRouteGenerationLifecycleService lifecycleService,
            AiRouteGenerationCleanupService cleanupService,
            AiRouteGenerationRepository generationRepository,
            AiReadingRouteRepository readingRouteRepository,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MutableClock clock) {
        this.lifecycleService = lifecycleService;
        this.cleanupService = cleanupService;
        this.generationRepository = generationRepository;
        this.readingRouteRepository = readingRouteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자를_생성한다(READER_ID);
        독자를_생성한다(OTHER_READER_ID);
        도서와_페이지를_생성한다();
        clock.set(STARTED_AT);
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    // --- 허용·금지 전이와 상태별 field --------------------------------------

    @Test
    void 경로를_찾으면_ROUTE로_완료하고_항목을_저장한다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);

        lifecycleService.completeWithRoute(generationId, 두_항목());

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("ROUTE", row.get("status")),
                () -> assertEquals(PURPOSE, row.get("normalized_purpose")),
                () -> assertNull(row.get("no_route_reason")),
                () -> assertNull(row.get("minimum_required_ink")),
                () -> assertNull(row.get("failure_code")),
                () -> assertNull(row.get("saved_route_id")),
                () -> assertEquals("2026-08-06 00:00:05.123456", row.get("completed_at")),
                () -> assertEquals("2026-08-06 00:15:05.123456", row.get("expires_at")),
                () -> assertEquals(2, 항목_수를_조회한다(generationId)));
    }

    @Test
    void 항목이_없으면_ROUTE로_완료할_수_없다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);

        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleService.completeWithRoute(generationId, List.of()));
        assertAll(
                () -> assertEquals("GENERATING", 생성을_조회한다(generationId).get("status")),
                () -> assertEquals(0, 항목_수를_조회한다(generationId)));
    }

    @Test
    void 관련_경로가_없으면_최소_잉크_없이_NO_ROUTE로_완료한다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);

        lifecycleService.completeWithoutRoute(
                generationId, AiRouteNoRouteReason.NO_RELEVANT_PAGES, null);

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("NO_ROUTE", row.get("status")),
                () -> assertEquals("NO_RELEVANT_PAGES", row.get("no_route_reason")),
                () -> assertNull(row.get("minimum_required_ink")),
                () -> assertEquals(0, 항목_수를_조회한다(generationId)));
    }

    @Test
    void 예산이_부족하면_예산보다_큰_최소_잉크와_함께_NO_ROUTE로_완료한다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);

        lifecycleService.completeWithoutRoute(
                generationId, AiRouteNoRouteReason.INSUFFICIENT_BUDGET, BUDGET + 3);

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("NO_ROUTE", row.get("status")),
                () -> assertEquals("INSUFFICIENT_BUDGET", row.get("no_route_reason")),
                () ->
                        assertEquals(
                                BUDGET + 3,
                                ((Number) row.get("minimum_required_ink")).intValue()));
    }

    @Test
    void 실패하면_공개_코드와_함께_FAILED로_완료한다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);

        lifecycleService.fail(generationId, AiRouteGenerationCleanupService.TIMEOUT_FAILURE_CODE);

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("FAILED", row.get("status")),
                () ->
                        assertEquals(
                                AiRouteGenerationCleanupService.TIMEOUT_FAILURE_CODE,
                                row.get("failure_code")),
                () -> assertEquals("2026-08-06 00:15:05.123456", row.get("expires_at")));
    }

    @Test
    void 이미_완료한_생성은_다시_완료할_수_없다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);
        lifecycleService.completeWithRoute(generationId, 두_항목());

        assertAll(
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        lifecycleService.fail(
                                                generationId, "AI_ROUTE_INVALID_OUTPUT")),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        lifecycleService.completeWithoutRoute(
                                                generationId,
                                                AiRouteNoRouteReason.NO_RELEVANT_PAGES,
                                                null)),
                () -> assertEquals("ROUTE", 생성을_조회한다(generationId).get("status")));
    }

    @Test
    void 없는_생성을_완료하려_하면_거부한다() {
        assertThrows(
                AiRouteGenerationNotFoundException.class,
                () -> lifecycleService.fail(UUID.randomUUID(), "AI_ROUTE_INVALID_OUTPUT"));
    }

    // --- 만료 경계 ----------------------------------------------------------

    @Test
    void 만료_직전에는_조회되고_만료_시각부터는_조회되지_않는다() {
        UUID generationId = 완료된_생성을_만든다();

        clock.set(EXPIRES_AT.minusNanos(1000));
        Optional<AiRouteGenerationView> 직전 = 소유자로_조회한다(generationId);
        clock.set(EXPIRES_AT);
        Optional<AiRouteGenerationView> 정각 = 소유자로_조회한다(generationId);
        clock.set(EXPIRES_AT.plusNanos(1000));
        Optional<AiRouteGenerationView> 직후 = 소유자로_조회한다(generationId);

        assertAll(
                () -> assertTrue(직전.isPresent()),
                () -> assertEquals(AiRouteGenerationStatus.ROUTE, 직전.orElseThrow().status()),
                () -> assertEquals(EXPIRES_AT, 직전.orElseThrow().expiresAt()),
                () -> assertTrue(정각.isEmpty()),
                () -> assertTrue(직후.isEmpty()));
    }

    @Test
    void 만료한_생성은_정리_전후로_같게_보이지_않는다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT);

        boolean 정리_전_조회 = lifecycleService.findOwnedResult(generationId, READER_ID).isPresent();
        boolean 정리_전_행 = generationRepository.existsById(generationId);
        int 지운_수 = cleanupService.removeExpired();
        boolean 정리_후_조회 = lifecycleService.findOwnedResult(generationId, READER_ID).isPresent();
        boolean 정리_후_행 = generationRepository.existsById(generationId);

        assertAll(
                () -> assertEquals(false, 정리_전_조회),
                () -> assertEquals(true, 정리_전_행),
                () -> assertEquals(1, 지운_수),
                () -> assertEquals(false, 정리_후_조회),
                () -> assertEquals(false, 정리_후_행),
                () -> assertEquals(0, 항목_수를_조회한다(generationId)));
    }

    @Test
    void 다른_독자는_유효한_생성도_조회할_수_없다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(COMPLETED_AT);

        assertTrue(lifecycleService.findOwnedResult(generationId, OTHER_READER_ID).isEmpty());
    }

    // --- 저장과 소비 --------------------------------------------------------

    @Test
    void 저장하면_임시_목적과_항목을_지우고_경로_식별자를_남긴다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(COMPLETED_AT);

        long routeId = 저장_경로로_전환한다(generationId);

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("SAVED", row.get("status")),
                () -> assertNull(row.get("normalized_purpose")),
                () -> assertNull(row.get("request_type")),
                () -> assertNull(row.get("max_additional_ink")),
                () -> assertEquals(routeId, ((Number) row.get("saved_route_id")).longValue()),
                () -> assertEquals("2026-08-06 00:15:05.123456", row.get("expires_at")),
                () -> assertEquals(0, 항목_수를_조회한다(generationId)),
                () -> assertNotNull(row.get("request_fingerprint")));
    }

    @Test
    void 만료한_생성은_저장할_수_없다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT);

        assertThrows(
                AiRouteGenerationNotFoundException.class, () -> 저장_경로로_전환한다(generationId));
        assertAll(
                () -> assertEquals("ROUTE", 생성을_조회한다(generationId).get("status")),
                () -> assertEquals(2, 항목_수를_조회한다(generationId)));
    }

    @Test
    void 경로를_삭제하면_CONSUMED로_바꾸고_재저장을_막는다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(COMPLETED_AT);
        long routeId = 저장_경로로_전환한다(generationId);

        lifecycleService.markConsumed(generationId);

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("CONSUMED", row.get("status")),
                () -> assertNull(row.get("saved_route_id")),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> 같은_경로로_다시_저장한다(generationId, routeId)));
    }

    @Test
    void 임시_상태가_이미_정리됐으면_소비_처리는_아무_일도_하지_않는다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT);
        cleanupService.removeExpired();

        lifecycleService.markConsumed(generationId);

        assertEquals(false, generationRepository.existsById(generationId));
    }

    // --- 정리 ---------------------------------------------------------------

    @Test
    void 만료하지_않은_생성은_정리하지_않는다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT.minusNanos(1000));

        assertAll(
                () -> assertEquals(0, cleanupService.removeExpired()),
                () -> assertEquals(true, generationRepository.existsById(generationId)),
                () -> assertEquals(2, 항목_수를_조회한다(generationId)));
    }

    @Test
    void 정리_도중_실패하면_항목과_생성이_모두_남는다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT);

        assertThrows(
                IllegalStateException.class,
                () ->
                        transactionTemplate.executeWithoutResult(
                                status -> {
                                    cleanupService.removeExpired();
                                    throw new IllegalStateException("정리 도중 주입한 실패");
                                }));
        assertAll(
                () -> assertEquals(true, generationRepository.existsById(generationId)),
                () -> assertEquals(2, 항목_수를_조회한다(generationId)));
    }

    // --- 중단 복구 ----------------------------------------------------------

    @Test
    void 전체_제한을_지난_GENERATING은_FAILED로_되돌린다() {
        UUID generationId = 생성을_시작한다();
        clock.set(ABANDONED_AT);

        assertEquals(1, cleanupService.recoverAbandoned());

        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("FAILED", row.get("status")),
                () ->
                        assertEquals(
                                AiRouteGenerationCleanupService.TIMEOUT_FAILURE_CODE,
                                row.get("failure_code")),
                () -> assertNotNull(row.get("completed_at")),
                () -> assertNotNull(row.get("expires_at")));
    }

    @Test
    void 제한_안의_GENERATING은_복구하지_않는다() {
        UUID generationId = 생성을_시작한다();
        clock.set(STARTED_AT.plus(AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT));

        assertAll(
                () -> assertEquals(0, cleanupService.recoverAbandoned()),
                () -> assertEquals("GENERATING", 생성을_조회한다(generationId).get("status")));
    }

    @Test
    void 복구된_생성은_재조회에_진행_중이_아니라_실패로_보인다() {
        UUID generationId = 생성을_시작한다();
        clock.set(ABANDONED_AT);
        cleanupService.recoverAbandoned();

        AiRouteGenerationView view =
                lifecycleService.findOwnedResult(generationId, READER_ID).orElseThrow();

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.FAILED, view.status()),
                () ->
                        assertEquals(
                                AiRouteGenerationCleanupService.TIMEOUT_FAILURE_CODE,
                                view.failureCode()),
                () -> assertNotNull(view.expiresAt()));
    }

    @Test
    void 복구가_실패로_바꾼_생성도_만료하면_정리한다() {
        UUID generationId = 생성을_시작한다();
        clock.set(ABANDONED_AT);
        cleanupService.recoverAbandoned();

        clock.set(ABANDONED_AT.plus(AiRouteGenerationLifecycleService.RESULT_RETENTION));

        assertAll(
                () -> assertEquals(1, cleanupService.removeExpired()),
                () -> assertEquals(false, generationRepository.existsById(generationId)));
    }

    // --- fixture ------------------------------------------------------------

    private Optional<AiRouteGenerationView> 소유자로_조회한다(UUID generationId) {
        return lifecycleService.findOwnedResult(generationId, READER_ID);
    }

    private static AiRouteGenerationCommand 명령() {
        return AiRouteGenerationCommand.forInkBudget(
                BOOK_ID, CONTENT_VERSION, PURPOSE, BUDGET, 100);
    }

    private static List<AiRouteResultItem> 두_항목() {
        return List.of(
                new AiRouteResultItem(
                        FIRST_PAGE_ID, 1, AiRouteItemRelevance.HIGH, false, AiRouteItemRole.CORE),
                new AiRouteResultItem(
                        SECOND_PAGE_ID,
                        2,
                        AiRouteItemRelevance.MEDIUM,
                        true,
                        AiRouteItemRole.PREREQUISITE));
    }

    private UUID 생성을_시작한다() {
        UUID generationId = UUID.randomUUID();
        AiRouteGenerationCommand command = 명령();
        transactionTemplate.executeWithoutResult(
                status ->
                        generationRepository.save(
                                AiRouteGeneration.start(
                                        generationId,
                                        READER_ID,
                                        UUID.randomUUID(),
                                        AiRouteRequestFingerprint.of(command),
                                        command,
                                        STARTED_AT)));
        return generationId;
    }

    /** {@code ROUTE} 로 완료한 생성. 완료 시각은 {@link #COMPLETED_AT}, 만료는 {@link #EXPIRES_AT} 이다. */
    private UUID 완료된_생성을_만든다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);
        lifecycleService.completeWithRoute(generationId, 두_항목());
        return generationId;
    }

    private long 저장_경로로_전환한다(UUID generationId) {
        return transactionTemplate.execute(
                status -> {
                    AiReadingRoute route =
                            readingRouteRepository.saveAndFlush(
                                    AiReadingRoute.createWithInkBudget(
                                            generationId,
                                            READER_ID,
                                            BOOK_ID,
                                            CONTENT_VERSION,
                                            PURPOSE,
                                            BUDGET,
                                            clock.instant()));
                    lifecycleService.markSaved(generationId, route);
                    return route.getId();
                });
    }

    /**
     * 이미 저장한 그 경로로 다시 저장을 시도한다. 새 경로를 만들면
     * {@code uk_ai_reading_route_generation} 에 먼저 걸려 생성 쪽 상태 가드까지 닿지 못한다.
     */
    private void 같은_경로로_다시_저장한다(UUID generationId, long routeId) {
        transactionTemplate.executeWithoutResult(
                status ->
                        lifecycleService.markSaved(
                                generationId,
                                readingRouteRepository.findById(routeId).orElseThrow()));
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'hash', '2026-08-05 00:00:00.000000')
                """,
                readerId,
                "scrum-462-reader-" + readerId + "@example.com");
    }

    private void 도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '인문', 'SCRUM-462 테스트 도서', '테스트 저자', 2, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지')
                """,
                FIRST_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 2, 'TEXT', '둘째 페이지')
                """,
                SECOND_PAGE_ID,
                BOOK_ID);
    }

    private Map<String, Object> 생성을_조회한다(UUID generationId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT status, normalized_purpose, request_type, max_additional_ink, depth,
                       no_route_reason, minimum_required_ink, failure_code, saved_route_id,
                       request_fingerprint,
                       DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s.%f') AS completed_at,
                       DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i:%s.%f') AS expires_at
                FROM ai_route_generation
                WHERE generation_id = ?
                """,
                generationId.toString());
    }

    private int 항목_수를_조회한다(UUID generationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation_item WHERE generation_id = ?",
                Integer.class,
                generationId.toString());
    }

    private void 테스트_데이터를_정리한다() {
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
            jdbcTemplate.update(
                    """
                    DELETE FROM ai_route_generation_item
                    WHERE generation_id IN (
                        SELECT generation_id FROM ai_route_generation WHERE reader_id = ?
                    )
                    """,
                    readerId);
            jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ai_reading_route WHERE reader_id = ?", readerId);
        }
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
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
     * 만료 경계를 한 컨텍스트 안에서 넘나들어야 해서 {@link Clock#fixed} 대신 옮길 수 있는 시계를 쓴다.
     * G05 시작 테스트에도 같은 시계가 있는데, 셋째 사용처가 생기면 공용 fixture 로 뽑는 것이 맞다.
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
