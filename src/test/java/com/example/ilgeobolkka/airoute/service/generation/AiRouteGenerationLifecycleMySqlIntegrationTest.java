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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.hibernate.resource.jdbc.spi.StatementInspector;
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
 * <p>유지보수 배치는 {@code application-test.yaml} 이 첫 실행을 한 시간 뒤로 미뤄 두어 돌지 않는다.
 * 정리·복구는 테스트가 직접 부른다.
 */
@SpringBootTest(
        properties =
                "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                        + "com.example.ilgeobolkka.airoute.service.generation"
                        + ".AiRouteGenerationLifecycleMySqlIntegrationTest$SqlRecorder")
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

    /**
     * API 계약이 항목을 생략할 수 없는 필드로 두고, 응답을 만드는 쪽은 Repository 를 직접 부르지
     * 않는다. 소유자·만료 확인과 같은 조회에서 정렬된 항목이 함께 나와야 한다.
     */
    @Test
    void 조회_결과는_추천_순서대로_정렬된_항목을_함께_준다() {
        UUID generationId = 완료된_생성을_만든다();
        clock.set(COMPLETED_AT);

        List<AiRouteGenerationItemView> items = 소유자로_조회한다(generationId).orElseThrow().items();

        assertAll(
                () -> assertEquals(2, items.size()),
                () -> assertEquals(1, items.get(0).position()),
                () -> assertEquals(1, items.get(0).pageNumber()),
                () -> assertEquals(AiRouteItemRelevance.HIGH, items.get(0).relevance()),
                () -> assertEquals(false, items.get(0).prerequisite()),
                () -> assertEquals(AiRouteItemRole.CORE, items.get(0).role()),
                () -> assertEquals(2, items.get(1).position()),
                () -> assertEquals(2, items.get(1).pageNumber()),
                () -> assertEquals(AiRouteItemRole.PREREQUISITE, items.get(1).role()));
    }

    @Test
    void ROUTE가_아닌_결과의_항목은_빈_목록이다() {
        UUID generationId = 생성을_시작한다();
        clock.set(COMPLETED_AT);
        lifecycleService.completeWithoutRoute(
                generationId, AiRouteNoRouteReason.NO_RELEVANT_PAGES, null);

        assertEquals(List.of(), 소유자로_조회한다(generationId).orElseThrow().items());
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

    /**
     * 상한이 실제로 적용되는지 본다. {@code Pageable} 이 무시돼도 결과만 보면 한 번에 다 지워져 통과해
     * 버리므로, 두 스윕으로 나뉘는 것까지 확인해야 잡힌다.
     */
    @Test
    void 정리는_한_번에_상한만큼만_지우고_나머지는_다음_주기로_넘긴다() {
        int 상한 = AiRouteGenerationCleanupService.BATCH_SIZE;
        만료된_생성을_여러_개_넣는다(상한 + 1);
        clock.set(STARTED_AT);

        int 첫_스윕 = cleanupService.removeExpired();
        int 남은_수 = 생성_수를_조회한다();
        int 둘째_스윕 = cleanupService.removeExpired();

        assertAll(
                () -> assertEquals(상한, 첫_스윕),
                () -> assertEquals(1, 남은_수),
                () -> assertEquals(1, 둘째_스윕),
                () -> assertEquals(0, 생성_수를_조회한다()));
    }

    /**
     * 예전에는 스윕이 한 번의 호출로 상한만큼만 지우고 나머지를 다음 주기(1분 뒤)로 미뤄, 대상이
     * 상한을 넘는 만큼 15분 보관 계약을 넘겨 DB 에 남았다. {@code sweep} 은 이제 반환 건수가 상한과
     * 같은 동안 같은 sweep 안에서 반복해 부르므로, 대상이 상한을 넘어도 한 번의 스윕으로 모두
     * 지워져야 한다.
     */
    @Test
    void 정리_대상이_상한을_넘어도_한_스윕_안에서_모두_지운다() {
        int 상한 = AiRouteGenerationCleanupService.BATCH_SIZE;
        만료된_생성을_여러_개_넣는다(상한 + 1);
        clock.set(STARTED_AT);

        maintenanceScheduler.sweep();

        assertEquals(0, 생성_수를_조회한다());
    }

    /**
     * 잠금 순서는 주석 세 군데가 유일한 방어였다. {@code removeExpired} 에서 두 줄만 바꿔도 모든 테스트가
     * 통과해 버리므로, 실제로 나간 SQL 순서를 단언한다. 교착을 재현하는 것보다 결정적이다.
     */
    @Test
    void 정리는_생성_행을_항목보다_먼저_잠근다() {
        완료된_생성을_만든다();
        clock.set(EXPIRES_AT);

        SqlRecorder.start();
        cleanupService.removeExpired();
        List<String> sql = SqlRecorder.stop();

        int 생성_잠금 =
                첫_위치(
                        sql,
                        statement ->
                                statement.contains("ai_route_generation ")
                                        && statement.contains("for update"));
        int 항목_삭제 =
                첫_위치(
                        sql,
                        statement ->
                                statement.startsWith("delete")
                                        && statement.contains("ai_route_generation_item"));
        assertAll(
                () -> assertTrue(생성_잠금 >= 0, "생성 행 잠금 문장이 없습니다: " + sql),
                () -> assertTrue(항목_삭제 >= 0, "항목 삭제 문장이 없습니다: " + sql),
                () -> assertTrue(생성_잠금 < 항목_삭제, "생성 잠금이 항목 삭제보다 뒤입니다: " + sql));
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

    /**
     * 보관 기간의 기준점은 복구를 돌린 시각이 아니라 논리적으로 실패한 시각이다. 지금 시각으로 잡으면
     * 오래전에 사라졌어야 할 멱등 상태가 복구할 때마다 15분씩 되살아난다.
     */
    @Test
    void 복구는_논리적_실패_시각부터_보관_기간을_잰다() {
        UUID generationId = 생성을_시작한다();
        clock.set(STARTED_AT.plus(Duration.ofHours(1)));

        cleanupService.recoverAbandoned();

        Instant 논리적_실패 = STARTED_AT.plus(AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT);
        Map<String, Object> row = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("FAILED", row.get("status")),
                () -> assertEquals(UTC_문자열(논리적_실패), row.get("completed_at")),
                () ->
                        assertEquals(
                                UTC_문자열(
                                        논리적_실패.plus(
                                                AiRouteGenerationLifecycleService
                                                        .RESULT_RETENTION)),
                                row.get("expires_at")));
    }

    /** 보관 기간까지 이미 지난 중단 생성은 다음 주기를 기다리지 않고 같은 스윕에서 사라져야 한다. */
    @Test
    void 오래_방치된_생성은_복구된_같은_스윕에서_지워진다() {
        UUID generationId = 생성을_시작한다();
        clock.set(STARTED_AT.plus(Duration.ofHours(1)));

        maintenanceScheduler.sweep();

        assertEquals(false, generationRepository.existsById(generationId));
    }

    /**
     * 중단 복구도 정리와 같은 문제를 가지고 있었다: 대상이 상한(200) 을 넘으면 남은 몫이 다음 주기로
     * 밀렸다. {@link #drain} 과 같은 방식으로 {@code sweep} 이 반복해 부르므로, 대상이 상한을 넘어도
     * 한 번의 스윕에서 모두 복구되고 — 이미 만료 상태이므로 — 곧바로 지워져야 한다.
     */
    @Test
    void 중단_복구_대상이_상한을_넘어도_한_스윕_안에서_모두_복구하고_지운다() {
        int 상한 = AiRouteGenerationCleanupService.BATCH_SIZE;
        중단된_GENERATING을_여러_개_넣는다(상한 + 1);
        clock.set(STARTED_AT);

        maintenanceScheduler.sweep();

        assertEquals(0, 생성_수를_조회한다());
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

    private static String UTC_문자열(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }

    private static int 첫_위치(List<String> sql, Predicate<String> 조건) {
        for (int index = 0; index < sql.size(); index++) {
            if (조건.test(sql.get(index))) {
                return index;
            }
        }
        return -1;
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

    /** 이미 만료한 {@code FAILED} 행을 한꺼번에 넣는다. 시각은 {@link #STARTED_AT} 보다 앞이다. */
    private void 만료된_생성을_여러_개_넣는다(int count) {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(new Object[] {UUID.randomUUID().toString(), UUID.randomUUID().toString()});
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO ai_route_generation
                    (generation_id, reader_id, book_id, content_version, idempotency_key,
                     request_fingerprint, normalized_purpose, request_type, max_additional_ink,
                     status, failure_code, created_at, completed_at, expires_at)
                VALUES (?, %d, %d, '%s', ?, REPEAT('a', 64), '%s', 'INK_BUDGET', %d,
                        'FAILED', 'AI_ROUTE_GENERATION_TIMEOUT',
                        '2026-08-05 00:00:00.000000',
                        '2026-08-05 00:00:00.000000',
                        '2026-08-05 00:15:00.000000')
                """
                        .formatted(READER_ID, BOOK_ID, CONTENT_VERSION, PURPOSE, BUDGET),
                rows);
    }

    /**
     * 아직 복구되지 않은 채 오래 멈춘 {@code GENERATING} 행을 한꺼번에 넣는다. 생성 시각은
     * {@link #STARTED_AT} 보다 훨씬 앞이라, 스윕을 {@link #STARTED_AT} 기준으로 돌리면 전체 제한을
     * 한참 넘긴 상태다.
     */
    private void 중단된_GENERATING을_여러_개_넣는다(int count) {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(new Object[] {UUID.randomUUID().toString(), UUID.randomUUID().toString()});
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO ai_route_generation
                    (generation_id, reader_id, book_id, content_version, idempotency_key,
                     request_fingerprint, normalized_purpose, request_type, max_additional_ink,
                     status, created_at)
                VALUES (?, %d, %d, '%s', ?, REPEAT('a', 64), '%s', 'INK_BUDGET', %d,
                        'GENERATING', '2026-08-05 00:00:00.000000')
                """
                        .formatted(READER_ID, BOOK_ID, CONTENT_VERSION, PURPOSE, BUDGET),
                rows);
    }

    private int 생성_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation WHERE reader_id = ?",
                Integer.class,
                READER_ID);
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

    /**
     * Hibernate 가 이름으로 만들 수 있어야 해서 public 무인자 생성자가 필요하다. 그래서 수집 지점이
     * 정적이다. 켠 구간에서만 모으므로 다른 테스트의 SQL 이 섞이지 않는다.
     */
    public static class SqlRecorder implements StatementInspector {

        private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();
        private static volatile boolean recording;

        static void start() {
            STATEMENTS.clear();
            recording = true;
        }

        static List<String> stop() {
            recording = false;
            return List.copyOf(STATEMENTS);
        }

        @Override
        public String inspect(String sql) {
            if (recording) {
                STATEMENTS.add(sql.toLowerCase(Locale.ROOT));
            }
            return sql;
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
