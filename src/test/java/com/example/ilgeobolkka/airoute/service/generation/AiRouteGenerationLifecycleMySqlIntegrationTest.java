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
import com.example.ilgeobolkka.airoute.scheduler.AiRouteGenerationMaintenanceScheduler;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>유지보수 스케줄러는 기능 플래그와 무관하게 등록되므로 첫 실행을 한 시간 뒤로 미뤄 둔다. 그러지
 * 않으면 만료 데이터를 만들어 두고 단언하는 사이 배치가 끼어들어 지워 버린다. 정리·복구는 테스트가
 * 직접 부른다.
 */
@SpringBootTest(properties = "ai-route.maintenance-initial-delay-millis=3600000")
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

    /** 복구가 매기는 코드와 구분하려고 다른 값을 쓴다. */
    private static final String 호출자_실패_코드 = "AI_ROUTE_INVALID_OUTPUT";

    private static final Instant STARTED_AT = Instant.parse("2026-08-06T00:00:00.123456Z");
    private static final Instant COMPLETED_AT = STARTED_AT.plusSeconds(5);
    private static final Instant EXPIRES_AT =
            COMPLETED_AT.plus(AiRouteGenerationLifecycleService.RESULT_RETENTION);

    /** 전체 제한을 막 넘긴 시각. 이때부터 {@code GENERATING} 은 버려진 것으로 본다. */
    private static final Instant ABANDONED_AT =
            STARTED_AT.plus(AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT).plusSeconds(1);

    private final AiRouteGenerationLifecycleService lifecycleService;
    private final AiRouteGenerationCleanupService cleanupService;
    private final AiRouteGenerationMaintenanceScheduler maintenanceScheduler;
    private final AiRouteGenerationRepository generationRepository;
    private final AiReadingRouteRepository readingRouteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MutableClock clock;

    @Autowired
    AiRouteGenerationLifecycleMySqlIntegrationTest(
            AiRouteGenerationLifecycleService lifecycleService,
            AiRouteGenerationCleanupService cleanupService,
            AiRouteGenerationMaintenanceScheduler maintenanceScheduler,
            AiRouteGenerationRepository generationRepository,
            AiReadingRouteRepository readingRouteRepository,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MutableClock clock) {
        this.lifecycleService = lifecycleService;
        this.cleanupService = cleanupService;
        this.maintenanceScheduler = maintenanceScheduler;
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

    /**
     * 항목 수만 세면 필드를 뒤바꿔 매핑해도 통과한다. 넘긴 값이 그대로 들어갔는지 열 단위로 확인한다.
     * 두 항목의 관련도·선수 여부·역할을 서로 다르게 둔 이유도 그것이다.
     */
    @Test
    void 저장한_항목은_넘긴_값을_그대로_담는다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);

        lifecycleService.completeWithRoute(generationId, 두_항목());

        List<Map<String, Object>> items = 항목을_조회한다(generationId);
        assertAll(
                () -> assertEquals(2, items.size()),
                () -> assertEquals(FIRST_PAGE_ID, 정수(items.get(0).get("book_page_id"))),
                () -> assertEquals(1, 정수(items.get(0).get("position"))),
                () -> assertEquals("HIGH", items.get(0).get("relevance")),
                () -> assertEquals(false, 참(items.get(0).get("prerequisite"))),
                () -> assertEquals("CORE", items.get(0).get("role")),
                () -> assertEquals(SECOND_PAGE_ID, 정수(items.get(1).get("book_page_id"))),
                () -> assertEquals(2, 정수(items.get(1).get("position"))),
                () -> assertEquals("MEDIUM", items.get(1).get("relevance")),
                () -> assertEquals(true, 참(items.get(1).get("prerequisite"))),
                () -> assertEquals("PREREQUISITE", items.get(1).get("role")));
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
        AiRouteGenerationView view = 소유자로_조회한다(generationId).orElseThrow();
        assertAll(
                () -> assertEquals("NO_ROUTE", row.get("status")),
                () -> assertEquals("INSUFFICIENT_BUDGET", row.get("no_route_reason")),
                () ->
                        assertEquals(
                                BUDGET + 3,
                                ((Number) row.get("minimum_required_ink")).intValue()),
                () -> assertEquals(AiRouteGenerationStatus.NO_ROUTE, view.status()),
                () ->
                        assertEquals(
                                AiRouteNoRouteReason.INSUFFICIENT_BUDGET, view.noRouteReason()),
                () -> assertEquals(BUDGET + 3, view.minimumRequiredInk()),
                () -> assertNull(view.failureCode()),
                () -> assertNull(view.savedRouteId()));
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

    /**
     * leaf 가 요구하는 {@code SAVED → CONSUMED → 만료 삭제} 사슬이다. 마지막에 저장 경로가 남아야 한다.
     * 만료가 지우는 것은 최소 멱등 상태뿐이고 기한 없는 저장 경로는 건드리지 않는다.
     */
    @Test
    void 소비까지_끝난_생성은_만료하면_지우고_저장_경로는_남긴다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(COMPLETED_AT);
        long routeId = 저장_경로로_전환한다(generationId);
        lifecycleService.markConsumed(generationId);

        clock.set(EXPIRES_AT);

        assertAll(
                () -> assertEquals(1, cleanupService.removeExpired()),
                () -> assertEquals(false, generationRepository.existsById(generationId)),
                () -> assertEquals(true, readingRouteRepository.existsById(routeId)));
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

    /**
     * 이 컨텍스트에는 {@code ai-route.enabled} 를 주지 않아 기본값 거짓이다. 그래도 유지보수 배치 빈이
     * 있어야 하고, 그 배치를 돌리면 만료 데이터가 지워져야 한다.
     *
     * <p>기능을 켠 채 임시 결과를 만들어 두고 나중에 끄는 경우가 실제 위험이다. 그때 정리 주체가
     * 사라지면 임시 목적·페이지 결과·멱등 상태가 영구히 남아 15분 보관 계약이 깨진다.
     */
    @Test
    void AI_경로가_꺼져_있어도_유지보수_배치가_만료_데이터를_지운다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT);

        maintenanceScheduler.sweep();

        assertAll(
                () -> assertEquals(false, generationRepository.existsById(generationId)),
                () -> assertEquals(0, 항목_수를_조회한다(generationId)));
    }

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

    /**
     * 목록을 뽑은 뒤 잠그기 전에 호출자가 정상 완료하는 경합이다. 복구는 잠금을 얻고 나서 상태를 다시
     * 보고 건너뛰어야 한다. 건너뛰지 않으면 Entity 전이 가드가 예외를 올려 스윕 한 사이클이 통째로
     * 롤백된다.
     *
     * <p>순서를 MVCC 로 만든다. 앞선 transaction 이 commit 하기 전에 상태를 바꿔 잠금을 쥐고 있으면,
     * 복구의 목록 조회는 아직 {@code GENERATING} 을 보고 뒤이은 잠금 조회는 최신 commit 인
     * {@code FAILED} 를 본다. 시계 읽기 래치로 복구가 목록 조회 직전에 왔음을 확인한 뒤에 놓아준다.
     *
     * <p>G05 수렴 테스트와 같은 한계가 있다. 건너뛰기로 빠졌는지 목록에서 이미 빠졌는지는 밖에서
     * 구분할 수 없다. 두 경우가 같은 결과를 내는 것이 설계 의도이기 때문이다.
     */
    @Test
    void 잠그기_전에_정상_완료된_생성은_복구하지_않는다() throws Exception {
        UUID generationId = 생성을_시작한다();
        clock.set(ABANDONED_AT);
        CountDownLatch 완료_보류 = new CountDownLatch(1);
        CountDownLatch 완료_해제 = new CountDownLatch(1);
        CountDownLatch 복구_진입 = clock.다음_읽기를_알린다();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> 먼저_완료하는_요청 =
                    executor.submit(
                            () ->
                                    transactionTemplate.executeWithoutResult(
                                            status -> {
                                                lifecycleService.fail(generationId, 호출자_실패_코드);
                                                완료_보류.countDown();
                                                해제를_기다린다(완료_해제);
                                            }));
            Future<Integer> 복구 =
                    executor.submit(
                            () -> {
                                assertTrue(완료_보류.await(5, TimeUnit.SECONDS));
                                return cleanupService.recoverAbandoned();
                            });

            assertTrue(복구_진입.await(10, TimeUnit.SECONDS));
            완료_해제.countDown();

            먼저_완료하는_요청.get(30, TimeUnit.SECONDS);
            int 복구된_수 = 복구.get(30, TimeUnit.SECONDS);

            Map<String, Object> row = 생성을_조회한다(generationId);
            assertAll(
                    () -> assertEquals(0, 복구된_수),
                    () -> assertEquals("FAILED", row.get("status")),
                    () -> assertEquals(호출자_실패_코드, row.get("failure_code")));
        } finally {
            완료_해제.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
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

    private static void 해제를_기다린다(CountDownLatch 해제) {
        try {
            if (!해제.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("해제 신호를 기다리다 시간이 지났습니다.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기가 중단됐습니다.", interrupted);
        }
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

    private List<Map<String, Object>> 항목을_조회한다(UUID generationId) {
        return jdbcTemplate.queryForList(
                """
                SELECT book_page_id, `position`, relevance, prerequisite, role
                FROM ai_route_generation_item
                WHERE generation_id = ?
                ORDER BY `position`
                """,
                generationId.toString());
    }

    /** {@code BOOLEAN} 은 {@code TINYINT(1)} 이라 드라이버 설정에 따라 {@code Boolean} 또는 수로 온다. */
    private static boolean 참(Object value) {
        return value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0;
    }

    private static long 정수(Object value) {
        return ((Number) value).longValue();
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
        private volatile CountDownLatch 읽힘 = new CountDownLatch(0);

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        /**
         * 다음 읽기를 관측할 래치를 건다. 경합 순서를 시간이 아니라 신호로 고정할 때 쓴다. 첫 읽기 뒤에는
         * 열린 래치라 아무 일도 하지 않는다.
         */
        CountDownLatch 다음_읽기를_알린다() {
            CountDownLatch 신호 = new CountDownLatch(1);
            읽힘 = 신호;
            return 신호;
        }

        @Override
        public Instant instant() {
            읽힘.countDown();
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
