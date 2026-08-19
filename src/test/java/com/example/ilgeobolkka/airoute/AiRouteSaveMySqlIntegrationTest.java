package com.example.ilgeobolkka.airoute;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.facade.AiRouteSaveFacade;
import com.example.ilgeobolkka.airoute.repository.AiRouteDailyUsageRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationRequestView;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationStartService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteRequestFingerprint;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteResultItem;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/ai-route-generations/{generationId}/routes} 의 저장·재시도·거부 계약을 실제 MySQL 로
 * 확인한다.
 *
 * <p>클래스에 {@code @Transactional} 을 붙이지 않는다. 붙이면 모든 요청이 한 transaction 에 갇혀 재시도와
 * 동시 저장이 서로의 commit 을 볼 수 없고, 거부가 아무것도 남기지 않았다는 단언도 rollback 과 구분되지
 * 않는다.
 *
 * <p>시간은 주입한 시계로만 움직인다. 대여 만료가 임시 결과 만료보다 먼저 오는 상황(T-AIR-020)은 실제
 * 시간을 기다려서는 만들 수 없다.
 *
 * <p>동시성은 Facade 를 직접 부른다. MockMvc 인스턴스를 여러 thread 가 공유하는 것을 피하려는 것이고,
 * 저장의 직렬화는 Controller 가 아니라 Facade 의 transaction 경계에 있다.
 */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum468-test",
            "openai.api-key=not-a-real-key-scrum468-test",
            "openai.data-policy-version=policy-test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteSaveMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRouteSaveMySqlIntegrationTest {

    private static final long READER_ID = 468_001L;
    private static final long OTHER_READER_ID = 468_002L;
    private static final long BOOK_ID = 468_101L;
    private static final long OTHER_BOOK_ID = 468_102L;
    private static final long FIRST_PAGE_ID = 468_201L;
    private static final long SECOND_PAGE_ID = 468_202L;
    private static final long OTHER_BOOK_PAGE_ID = 468_203L;
    private static final long FIRST_RENTAL_ID = 468_301L;
    private static final long SECOND_RENTAL_ID = 468_302L;

    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String PURPOSE = "핵심 개념만 빠르게";

    /** 예산 1. 항목 둘 중 하나만 권한이 있어야 저장이 되므로 대여 한 건이 곧 경계다. */
    private static final int BUDGET = 1;

    private static final int INK_BALANCE = 10;

    private static final Instant STARTED_AT = Instant.parse("2026-08-06T00:00:00.123456Z");
    private static final Instant COMPLETED_AT = STARTED_AT.plusSeconds(5);
    private static final Instant EXPIRES_AT =
            COMPLETED_AT.plus(AiRouteGenerationLifecycleService.RESULT_RETENTION);

    /** 30일 대여. T-AIR-020 은 이 기간의 끝이 임시 결과 만료보다 먼저 오도록 시계를 맞춘다. */
    private static final Duration RENTAL_PERIOD = Duration.ofDays(30);

    /** {@code DATETIME(6)} 픽스처를 UTC 로 넣는 형식. 애플리케이션이 같은 열을 UTC 로 읽는다. */
    private static final DateTimeFormatter UTC_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiRouteSaveFacade saveFacade;
    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final AiRouteDailyUsageRepository dailyUsageRepository;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final TransactionTemplate transactionTemplate;
    private final MutableClock clock;

    @Autowired
    AiRouteSaveMySqlIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiRouteSaveFacade saveFacade,
            AiRouteGenerationRepository generationRepository,
            AiRouteGenerationItemRepository generationItemRepository,
            AiRouteDailyUsageRepository dailyUsageRepository,
            AiRouteGenerationLifecycleService lifecycleService,
            PlatformTransactionManager transactionManager,
            MutableClock clock) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.saveFacade = saveFacade;
        this.generationRepository = generationRepository;
        this.generationItemRepository = generationItemRepository;
        this.dailyUsageRepository = dailyUsageRepository;
        this.lifecycleService = lifecycleService;
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

    // --- 첫 저장과 재시도 ----------------------------------------------------

    @Test
    void 유효한_생성은_201과_저장_경로를_반환하고_현재_경로가_된다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        Map<String, Object> 이전 = 잉크와_대여_상태();

        MvcResult result =
                저장을_요청한다(READER_ID, generationId)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                        .andExpect(jsonPath("$.purpose").value(PURPOSE))
                        .andExpect(jsonPath("$.current").value(true))
                        .andExpect(jsonPath("$.completedAt").value(nullValue()))
                        .andExpect(jsonPath("$.evaluationAvailable").value(false))
                        .andExpect(jsonPath("$.rating").value(nullValue()))
                        .andExpect(jsonPath("$.items.length()").value(2))
                        .andExpect(jsonPath("$.items[0].position").value(1))
                        .andExpect(jsonPath("$.items[0].pageNumber").value(1))
                        .andExpect(jsonPath("$.items[0].relevance").value("HIGH"))
                        .andExpect(jsonPath("$.items[0].prerequisite").value(false))
                        .andExpect(jsonPath("$.items[0].role").value("CORE"))
                        .andExpect(jsonPath("$.items[0].additionalCostStatus").value("ACTIVE_RENTAL"))
                        .andExpect(jsonPath("$.items[0].openedAt").value(nullValue()))
                        .andExpect(jsonPath("$.items[1].position").value(2))
                        .andExpect(jsonPath("$.items[1].prerequisite").value(true))
                        .andExpect(jsonPath("$.items[1].additionalCostStatus").value("ONE_INK"))
                        .andReturn();

        long routeId = 경로_식별자(result);
        Map<String, Object> route = 경로를_조회한다(routeId);
        assertAll(
                () -> assertEquals(generationId.toString(), route.get("generation_id")),
                () -> assertEquals(READER_ID, 정수(route.get("reader_id"))),
                () -> assertEquals(BOOK_ID, 정수(route.get("book_id"))),
                () -> assertEquals(CONTENT_VERSION, route.get("content_version")),
                () -> assertEquals(PURPOSE, route.get("normalized_purpose")),
                () -> assertEquals("INK_BUDGET", route.get("request_type")),
                () -> assertEquals(BUDGET, 정수(route.get("max_additional_ink"))),
                () -> assertNull(route.get("depth")),
                () -> assertNull(route.get("completed_at")),
                () -> assertEquals(2, 경로_항목_수(routeId)),
                () -> assertEquals(Long.valueOf(routeId), 현재_경로_식별자(READER_ID, BOOK_ID)),
                // 성공 바디도 키 집합 전체를 고정한다. api-spec 이 nullable 필드를 생략하지 않고 null 로
                // 반환하라고 정했으므로, 필드가 빠지거나 새로 생기면 여기서 실패해야 한다.
                () -> assertEquals(
                        Set.of(
                                "routeId",
                                "bookId",
                                "bookTitle",
                                "purpose",
                                "current",
                                "createdAt",
                                "completedAt",
                                "evaluationAvailable",
                                "rating",
                                "items"),
                        응답_키_집합(result)),
                () -> assertEquals(
                        Set.of(
                                "position",
                                "pageNumber",
                                "relevance",
                                "prerequisite",
                                "role",
                                "estimatedMinutes",
                                "guide",
                                "additionalCostStatus",
                                "openedAt"),
                        항목_키_집합(result, 0)),
                // 저장은 권한을 만들지 않는다. 성공해도 잉크·원장·대여·세션·서재가 그대로여야 한다.
                () -> assertEquals(이전, 잉크와_대여_상태()));
    }

    /** 저장은 생성의 임시 목적·입력·항목을 지우고 최소 멱등 상태만 원래 만료까지 남긴다. */
    @Test
    void 저장한_생성은_SAVED로_남고_임시_결과를_지운다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();

        long routeId = 경로_식별자(저장을_요청한다(READER_ID, generationId).andReturn());

        Map<String, Object> generation = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("SAVED", generation.get("status")),
                () -> assertEquals(routeId, 정수(generation.get("saved_route_id"))),
                () -> assertNull(generation.get("normalized_purpose")),
                () -> assertNull(generation.get("request_type")),
                () -> assertNull(generation.get("max_additional_ink")),
                () -> assertNull(generation.get("depth")),
                () -> assertEquals(0, 생성_항목_수(generationId)));
    }

    @Test
    void 같은_생성의_재시도는_200과_같은_경로를_반환한다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        long routeId = 경로_식별자(저장을_요청한다(READER_ID, generationId).andReturn());

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.items.length()").value(2));

        assertEquals(1, 경로_수(READER_ID, BOOK_ID));
    }

    /**
     * 재시도가 현재 경로를 되돌리지 않는다. 되돌리면 독자가 저장 뒤에 고른 현재 경로가 재시도 한 번으로
     * 뒤집힌다.
     */
    @Test
    void 재시도는_그사이_바뀐_현재_경로를_되돌리지_않는다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        long routeId = 경로_식별자(저장을_요청한다(READER_ID, generationId).andReturn());

        long 다른_경로 = 다른_경로를_현재로_지정한다();

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.current").value(false));

        assertEquals(Long.valueOf(다른_경로), 현재_경로_식별자(READER_ID, BOOK_ID));
    }

    /**
     * 같은 도서를 다시 저장하면 현재 경로가 새 경로로 바뀐다. current upsert 의 UPDATE 분기이며, 재시도가
     * 되돌리지 않는다는 위 계약과 짝으로 "새 저장은 바꾼다"를 고정한다.
     */
    @Test
    void 같은_도서를_다시_저장하면_현재_경로가_교체된다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        long 첫_경로 = 경로_식별자(저장을_요청한다(READER_ID, 완료된_생성을_만든다()).andReturn());

        MvcResult 둘째 =
                저장을_요청한다(READER_ID, 완료된_생성을_만든다())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.current").value(true))
                        .andReturn();

        long 둘째_경로 = 경로_식별자(둘째);
        assertAll(
                () -> assertEquals(2, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(Long.valueOf(둘째_경로), 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertTrue(첫_경로 != 둘째_경로));
    }

    @Test
    void 저장_경로를_삭제한_생성은_409로_거부한다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        long routeId = 경로_식별자(저장을_요청한다(READER_ID, generationId).andReturn());
        경로를_삭제한다(routeId, generationId);

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_GENERATION_CONSUMED"));

        assertEquals(0, 경로_수(READER_ID, BOOK_ID));
    }

    // --- 소유자·상태·콘텐츠 거부 ---------------------------------------------

    @Test
    void 다른_독자의_생성은_404로_거부한다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();

        저장을_요청한다(OTHER_READER_ID, generationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertAll(
                () -> assertEquals("ROUTE", 생성을_조회한다(generationId).get("status")),
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)));
    }

    @Test
    void 만료한_생성은_정리_전에도_404로_거부한다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT);

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertAll(
                () -> assertEquals("ROUTE", 생성을_조회한다(generationId).get("status")),
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)));
    }

    /** 경계는 {@code now < expiresAt} 이 유효다. 만료 시각 직전은 아직 저장할 수 있어야 한다. */
    @Test
    void 만료_직전은_아직_저장할_수_있다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        clock.set(EXPIRES_AT.minusMillis(1));

        저장을_요청한다(READER_ID, generationId).andExpect(status().isCreated());
    }

    @Test
    void 경로가_없는_생성은_404로_거부한다() throws Exception {
        UUID generationId = 생성을_시작한다(READER_ID);
        clock.set(COMPLETED_AT);
        transactionTemplate.executeWithoutResult(
                status ->
                        lifecycleService.completeWithoutRoute(
                                generationId, AiRouteNoRouteReason.NO_RELEVANT_PAGES, null));

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertEquals(0, 경로_수(READER_ID, BOOK_ID));
    }

    @Test
    void 실패한_생성은_404로_거부한다() throws Exception {
        UUID generationId = 생성을_시작한다(READER_ID);
        clock.set(COMPLETED_AT);
        transactionTemplate.executeWithoutResult(
                status -> lifecycleService.fail(generationId, "AI_ROUTE_GENERATION_TIMEOUT"));

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 생성_중인_결과는_404로_거부한다() throws Exception {
        UUID generationId = 생성을_시작한다(READER_ID);

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 없는_생성은_404로_거부한다() throws Exception {
        저장을_요청한다(READER_ID, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /**
     * 2차 MVP 는 {@code contentVersion} 을 재발급하지 않아 사용자 경로로는 닿을 수 없다. 저장값을 직접
     * 조작한 불변식 검사로 둔다.
     */
    @Test
    void 도서_콘텐츠_버전이_바뀌면_409로_거부한다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        jdbcTemplate.update(
                "UPDATE book SET content_version = 'ai-route-v3' WHERE id = ?", BOOK_ID);

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_CONTENT_CHANGED"));

        assertAll(
                () -> assertEquals("ROUTE", 생성을_조회한다(generationId).get("status")),
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)));
    }

    // --- 권한 변동 거부 (T-AIR-020) ------------------------------------------

    /**
     * 저장 절차는 만료 확인이 권한 재계산보다 먼저다. 그래서 대여 만료가 임시 결과 만료보다 먼저 와야
     * 권한 거부에 닿는다. 30일 대여의 잔여 기간이 15분 미만이 되도록 시계를 맞춘 뒤 생성한다.
     */
    @Test
    void 대여가_만료돼_비용이_예산을_넘으면_409로_거부한다() throws Exception {
        UUID generationId = 대여가_먼저_만료되는_생성을_만든다();
        clock.set(대여_만료_시각());

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_ENTITLEMENT_CHANGED"));

        Map<String, Object> generation = 생성을_조회한다(generationId);
        assertAll(
                () -> assertEquals("ROUTE", generation.get("status")),
                () -> assertNull(generation.get("saved_route_id")),
                () -> assertEquals(PURPOSE, generation.get("normalized_purpose")),
                () -> assertEquals(2, 생성_항목_수(generationId)),
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)),
                () -> assertNull(현재_경로_식별자(READER_ID, BOOK_ID)));
    }

    /** 거부는 잉크·대여·열람 세션·서재를 건드리지 않는다. 저장 경로 전체가 그 넷을 바꾸지 않는다. */
    @Test
    void 권한_거부는_잉크와_대여_내역을_바꾸지_않는다() throws Exception {
        UUID generationId = 대여가_먼저_만료되는_생성을_만든다();
        clock.set(대여_만료_시각());
        Map<String, Object> 이전 = 잉크와_대여_상태();

        저장을_요청한다(READER_ID, generationId).andExpect(status().isConflict());

        assertEquals(이전, 잉크와_대여_상태());
    }

    /** 거부 바디에 재계산 비용·권한·페이지 상세가 새면 실패하도록 전체 키 집합을 단언한다. */
    @Test
    void 권한_거부_응답은_code와_message만_담는다() throws Exception {
        UUID generationId = 대여가_먼저_만료되는_생성을_만든다();
        clock.set(대여_만료_시각());

        MvcResult result =
                저장을_요청한다(READER_ID, generationId)
                        .andExpect(status().isConflict())
                        .andReturn();

        assertEquals(Set.of("code", "message"), 응답_키_집합(result));
    }

    /**
     * 최초 거부를 기록하는 상태를 두지 않는다. 권한이 그대로면 같은 409 이고, 만료 전에 권한이 예산 이하로
     * 돌아오면 같은 {@code generationId} 저장이 다시 된다.
     */
    @Test
    void 권한이_회복되면_같은_생성을_다시_저장할_수_있다() throws Exception {
        UUID generationId = 대여가_먼저_만료되는_생성을_만든다();
        clock.set(대여_만료_시각());

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_ENTITLEMENT_CHANGED"));
        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_ENTITLEMENT_CHANGED"));

        페이지를_대여한다(READER_ID, SECOND_RENTAL_ID, FIRST_PAGE_ID, 대여_만료_시각());

        저장을_요청한다(READER_ID, generationId).andExpect(status().isCreated());
        assertEquals(1, 경로_수(READER_ID, BOOK_ID));
    }

    @Test
    void 소장한_도서는_모든_페이지가_무료라_저장할_수_있다() throws Exception {
        도서를_소장한다(READER_ID, 468_401L, BOOK_ID);
        UUID generationId = 완료된_생성을_만든다();

        저장을_요청한다(READER_ID, generationId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].additionalCostStatus").value("OWNED"))
                .andExpect(jsonPath("$.items[1].additionalCostStatus").value("OWNED"));
    }

    // --- 동시성 -------------------------------------------------------------

    @Test
    void 같은_생성을_동시에_저장해도_경로는_한_건이다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();

        List<Boolean> 만들었는지 =
                동시에_저장한다(
                        () -> saveFacade.saveRoute(READER_ID, generationId).created(),
                        () -> saveFacade.saveRoute(READER_ID, generationId).created());

        assertAll(
                () -> assertEquals(List.of(false, true), 만들었는지.stream().sorted().toList()),
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)));
    }

    /**
     * 일반 존재 조회가 {@code REPEATABLE READ}의 read view를 만든 직후 저장을 커밋한다. 뒤따르는 잠금 조회는
     * 최신 {@code SAVED}를 보지만, 다시 일반 조회를 내면 앞선 read view라 새 경로를 볼 수 없는 순서다.
     */
    @Test
    void 멱등_선조회_뒤에_저장이_커밋돼도_저장_경로를_같은_스냅샷에서_다시_읽지_않는다()
            throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID idempotencyKey = UUID.randomUUID();
        UUID generationId = 완료된_생성을_만든다(idempotencyKey);
        CountDownLatch 선조회_완료 = new CountDownLatch(1);
        CountDownLatch 저장_완료 = new CountDownLatch(1);
        AiRouteGenerationRepository 순서를_고정한_저장소 =
                선조회_뒤에_기다리는_저장소(선조회_완료, 저장_완료);
        AiRouteGenerationStartService 순서를_고정한_시작_서비스 =
                new AiRouteGenerationStartService(
                        순서를_고정한_저장소,
                        generationItemRepository,
                        dailyUsageRepository,
                        transactionTemplate,
                        clock);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AiRouteGenerationRequestView> 재요청 = executor.submit(() ->
                    순서를_고정한_시작_서비스
                            .findExistingRequest(READER_ID, idempotencyKey)
                            .orElseThrow());

            assertTrue(선조회_완료.await(10, TimeUnit.SECONDS));
            saveFacade.saveRoute(READER_ID, generationId);
            저장_완료.countDown();

            AiRouteGenerationRequestView existing = 재요청.get(30, TimeUnit.SECONDS);
            assertEquals(generationId, existing.generationId());
        } finally {
            저장_완료.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void 두_생성을_동시에_저장하면_경로는_둘이고_현재_경로는_하나다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID 첫_생성 = 완료된_생성을_만든다();
        UUID 둘째_생성 = 완료된_생성을_만든다();

        List<Boolean> 만들었는지 =
                동시에_저장한다(
                        () -> saveFacade.saveRoute(READER_ID, 첫_생성).created(),
                        () -> saveFacade.saveRoute(READER_ID, 둘째_생성).created());

        assertAll(
                () -> assertEquals(List.of(true, true), 만들었는지),
                () -> assertEquals(2, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)));
    }

    // --- rollback -----------------------------------------------------------

    /**
     * 저장 도중 실패하면 경로·항목·현재 경로·생성 상태가 함께 되돌아간다. 같은 {@code generationId} 를
     * 가리키는 경로를 미리 넣어 두어 {@code uk_ai_reading_route_generation} 을 flush 시점에 터뜨린다.
     */
    @Test
    void 저장_도중_실패하면_경로와_생성_상태가_함께_되돌아간다() throws Exception {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);
        UUID generationId = 완료된_생성을_만든다();
        같은_생성을_가리키는_경로를_미리_넣는다(generationId);

        저장을_요청한다(READER_ID, generationId).andExpect(status().isInternalServerError());

        assertAll(
                () -> assertEquals("ROUTE", 생성을_조회한다(generationId).get("status")),
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 생성_항목_수(generationId)),
                () -> assertNull(현재_경로_식별자(READER_ID, BOOK_ID)));
    }

    // --- 요청 --------------------------------------------------------------

    private ResultActions 저장을_요청한다(long readerId, UUID generationId) throws Exception {
        return mockMvc.perform(
                post("/api/ai-route-generations/{generationId}/routes", generationId)
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf()));
    }

    private List<Boolean> 동시에_저장한다(Callable<Boolean> 첫째, Callable<Boolean> 둘째) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch 출발 = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> 작업 : List.of(첫째, 둘째)) {
                futures.add(
                        executor.submit(
                                () -> {
                                    출발.await();
                                    return 작업.call();
                                }));
            }
            출발.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "동시 요청이 끝나지 않았다");

            List<Boolean> 결과 = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                결과.add(future.get());
            }
            return 결과;
        } finally {
            executor.shutdownNow();
        }
    }

    /** 실제 Repository 호출은 그대로 위임하고 존재 조회 직후에만 래치로 transaction을 멈춘다. */
    private AiRouteGenerationRepository 선조회_뒤에_기다리는_저장소(
            CountDownLatch 선조회_완료, CountDownLatch 저장_완료) {
        return (AiRouteGenerationRepository) Proxy.newProxyInstance(
                AiRouteGenerationRepository.class.getClassLoader(),
                new Class<?>[] {AiRouteGenerationRepository.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = method.invoke(generationRepository, arguments);
                        if (method.getName().equals("existsByReaderIdAndIdempotencyKey")) {
                            선조회_완료.countDown();
                            if (!저장_완료.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("저장 완료 신호를 기다리다 시간이 지났습니다.");
                            }
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    // --- 픽스처 ------------------------------------------------------------

    private UUID 생성을_시작한다(long readerId) {
        return 생성을_시작한다(readerId, UUID.randomUUID());
    }

    private UUID 생성을_시작한다(long readerId, UUID idempotencyKey) {
        UUID generationId = UUID.randomUUID();
        AiRouteGenerationCommand command =
                AiRouteGenerationCommand.forInkBudget(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, BUDGET, INK_BALANCE);
        Instant createdAt = clock.instant();
        transactionTemplate.executeWithoutResult(
                status ->
                        generationRepository.save(
                                AiRouteGeneration.start(
                                        generationId,
                                        readerId,
                                        idempotencyKey,
                                        AiRouteRequestFingerprint.of(command),
                                        command,
                                        createdAt)));
        return generationId;
    }

    /** 두 페이지를 담은 {@code ROUTE} 생성. 완료 시각은 {@link #COMPLETED_AT} 이다. */
    private UUID 완료된_생성을_만든다() {
        return 완료된_생성을_만든다(UUID.randomUUID());
    }

    private UUID 완료된_생성을_만든다(UUID idempotencyKey) {
        clock.set(STARTED_AT);
        UUID generationId = 생성을_시작한다(READER_ID, idempotencyKey);
        clock.set(COMPLETED_AT);
        transactionTemplate.executeWithoutResult(
                status -> lifecycleService.completeWithRoute(generationId, 두_항목()));
        return generationId;
    }

    /**
     * 30일 대여의 잔여 기간이 임시 결과 보관 기간보다 짧아지도록 시계를 맞춘 뒤 생성한다. 두 페이지 중
     * 하나만 대여해 두므로 대여가 살아 있는 동안 추가 비용은 예산과 같은 1 이고, 만료하면 2 가 된다.
     */
    private UUID 대여가_먼저_만료되는_생성을_만든다() {
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID, STARTED_AT);

        clock.set(대여_만료_시각().minus(Duration.ofMinutes(10)));
        UUID generationId = 생성을_시작한다(READER_ID);
        transactionTemplate.executeWithoutResult(
                status -> lifecycleService.completeWithRoute(generationId, 두_항목()));
        return generationId;
    }

    /** 대여만 만료되고 임시 결과는 아직 유효한 시점. */
    private static Instant 대여_만료_시각() {
        return STARTED_AT.plus(RENTAL_PERIOD);
    }

    private static List<AiRouteResultItem> 두_항목() {
        return List.of(
                new AiRouteResultItem(
                        FIRST_PAGE_ID,
                        1,
                        AiRouteItemRelevance.HIGH,
                        false,
                        AiRouteItemRole.CORE,
                        AiRouteAdditionalCostStatus.ONE_INK),
                new AiRouteResultItem(
                        SECOND_PAGE_ID,
                        2,
                        AiRouteItemRelevance.MEDIUM,
                        true,
                        AiRouteItemRole.PREREQUISITE,
                        AiRouteAdditionalCostStatus.ONE_INK));
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-08-05 00:00:00.000000')
                """,
                readerId,
                "scrum468-" + readerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)",
                readerId,
                INK_BALANCE);
    }

    private void 도서와_페이지를_생성한다() {
        도서를_생성한다(BOOK_ID, "SCRUM-468 테스트 도서");
        도서를_생성한다(OTHER_BOOK_ID, "SCRUM-468 다른 도서");
        페이지를_생성한다(FIRST_PAGE_ID, BOOK_ID, 1);
        페이지를_생성한다(SECOND_PAGE_ID, BOOK_ID, 2);
        페이지를_생성한다(OTHER_BOOK_PAGE_ID, OTHER_BOOK_ID, 1);
    }

    private void 도서를_생성한다(long bookId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won, content_version)
                VALUES (?, '인문', ?, '테스트 저자', 2, 10000, ?)
                """,
                bookId,
                title,
                CONTENT_VERSION);
    }

    private void 페이지를_생성한다(long pageId, long bookId, int pageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, ?, 'TEXT', '샘플 본문', '샘플 주제', 120)
                """,
                pageId,
                bookId,
                pageNumber);
    }

    /**
     * 시각을 UTC 문자열로 넣는다. {@code java.sql.Timestamp} 로 바인딩하면 드라이버가 JVM 기본
     * 타임존(테스트는 {@code Asia/Seoul})으로 저장하는데, 애플리케이션은 같은 열을 UTC 로 읽으므로
     * 대여 기간이 9시간 어긋난다.
     */
    private void 페이지를_대여한다(long readerId, long rentalId, long bookPageId, Instant rentedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rentalId,
                readerId,
                bookPageId,
                UTC_DATETIME.format(rentedAt),
                UTC_DATETIME.format(rentedAt.plus(RENTAL_PERIOD)));
    }

    private void 도서를_소장한다(long readerId, long ownershipId, long bookId) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 10000, '2026-08-01 08:00:00.000000',
                        '2026-08-01 08:00:00.000000')
                """,
                ownershipId,
                readerId,
                bookId,
                new UUID(1L, ownershipId).toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, '2026-08-01 08:00:00.000000')
                """,
                ownershipId,
                readerId,
                bookId,
                ownershipId);
    }

    /** 같은 독자·도서의 다른 저장 경로를 만들어 현재 경로로 지정한다. */
    private long 다른_경로를_현재로_지정한다() {
        long routeId = 468_501L;
        경로를_직접_넣는다(routeId, new UUID(2L, routeId), READER_ID, BOOK_ID);
        jdbcTemplate.update(
                """
                UPDATE ai_route_current SET route_id = ?, updated_at = '2026-08-06 00:10:00.000000'
                WHERE reader_id = ? AND book_id = ?
                """,
                routeId,
                READER_ID,
                BOOK_ID);
        return routeId;
    }

    /**
     * 다른 독자의 경로가 같은 {@code generationId} 를 이미 쥐고 있게 만든다. 정상 경로로는 생길 수 없는
     * 상태이며 저장 도중 실패를 주입하려고만 쓴다.
     */
    private void 같은_생성을_가리키는_경로를_미리_넣는다(UUID generationId) {
        경로를_직접_넣는다(468_502L, generationId, OTHER_READER_ID, OTHER_BOOK_ID);
    }

    private void 경로를_직접_넣는다(long routeId, UUID generationId, long readerId, long bookId) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version, normalized_purpose,
                     request_type, max_additional_ink, created_at)
                VALUES (?, ?, ?, ?, ?, '다른 목적', 'INK_BUDGET', 1,
                        '2026-08-06 00:10:00.000000')
                """,
                routeId,
                generationId.toString(),
                readerId,
                bookId,
                CONTENT_VERSION);
    }

    /**
     * S03 이 오기 전이라 경로 삭제를 SQL 과 G06 API 로 흉내 낸다.
     *
     * <p>소비 처리를 먼저 한다. {@code fk_ai_route_generation_saved_route} 가 생성의
     * {@code saved_route_id} 를 경로에 묶고 있어서, 먼저 지우면 참조 무결성에 걸린다.
     */
    private void 경로를_삭제한다(long routeId, UUID generationId) {
        transactionTemplate.executeWithoutResult(
                status -> lifecycleService.markConsumed(generationId));
        jdbcTemplate.update("DELETE FROM ai_route_current WHERE route_id = ?", routeId);
        jdbcTemplate.update("DELETE FROM ai_reading_route_item WHERE route_id = ?", routeId);
        jdbcTemplate.update("DELETE FROM ai_reading_route WHERE id = ?", routeId);
    }

    // --- 조회 --------------------------------------------------------------

    private Map<String, Object> 생성을_조회한다(UUID generationId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT status, normalized_purpose, request_type, max_additional_ink, depth,
                       saved_route_id
                FROM ai_route_generation
                WHERE generation_id = ?
                """,
                generationId.toString());
    }

    private Map<String, Object> 경로를_조회한다(long routeId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT generation_id, reader_id, book_id, content_version, normalized_purpose,
                       request_type, max_additional_ink, depth, completed_at
                FROM ai_reading_route
                WHERE id = ?
                """,
                routeId);
    }

    private int 생성_항목_수(UUID generationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation_item WHERE generation_id = ?",
                Integer.class,
                generationId.toString());
    }

    private int 경로_항목_수(long routeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_reading_route_item WHERE route_id = ?",
                Integer.class,
                routeId);
    }

    private int 경로_수(long readerId, long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_reading_route WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                readerId,
                bookId);
    }

    private int 현재_경로_수(long readerId, long bookId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_current WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                readerId,
                bookId);
    }

    private Long 현재_경로_식별자(long readerId, long bookId) {
        List<Long> routeIds =
                jdbcTemplate.queryForList(
                        "SELECT route_id FROM ai_route_current WHERE reader_id = ? AND book_id = ?",
                        Long.class,
                        readerId,
                        bookId);
        return routeIds.isEmpty() ? null : routeIds.get(0);
    }

    /** 저장 경로가 건드리지 않아야 하는 값들. */
    private Map<String, Object> 잉크와_대여_상태() {
        Map<String, Object> 상태 = new LinkedHashMap<>();
        상태.put("balance", 건수("SELECT balance FROM ink_account WHERE reader_id = ?"));
        상태.put("ledger", 건수("SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?"));
        상태.put("rental", 건수("SELECT COUNT(*) FROM page_rental WHERE reader_id = ?"));
        상태.put("session", 건수("SELECT COUNT(*) FROM reading_session WHERE reader_id = ?"));
        상태.put("library", 건수("SELECT COUNT(*) FROM library_entry WHERE reader_id = ?"));
        return 상태;
    }

    private Integer 건수(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class, READER_ID);
    }

    private long 경로_식별자(MvcResult result) throws Exception {
        return 응답(result).get("routeId").asLong();
    }

    private Set<String> 응답_키_집합(MvcResult result) throws Exception {
        return new LinkedHashSet<>(응답(result).propertyNames());
    }

    private Set<String> 항목_키_집합(MvcResult result, int index) throws Exception {
        return new LinkedHashSet<>(응답(result).get("items").get(index).propertyNames());
    }

    private JsonNode 응답(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static long 정수(Object value) {
        return ((Number) value).longValue();
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update(
                "DELETE FROM ai_route_current WHERE book_id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
            // 생성을 경로보다 먼저 지운다. fk_ai_route_generation_saved_route 가 SAVED 생성의
            // saved_route_id 를 경로에 묶고 있어서 순서를 뒤집으면 참조 무결성에 걸린다.
            jdbcTemplate.update(
                    """
                    DELETE FROM ai_route_generation_item
                    WHERE generation_id IN (
                        SELECT generation_id FROM ai_route_generation WHERE reader_id = ?
                    )
                    """,
                    readerId);
            jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id = ?", readerId);
            jdbcTemplate.update(
                    """
                    DELETE FROM ai_reading_route_item
                    WHERE route_id IN (SELECT id FROM ai_reading_route WHERE reader_id = ?)
                    """,
                    readerId);
            jdbcTemplate.update("DELETE FROM ai_reading_route WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", readerId);
        }
        for (long bookId : List.of(BOOK_ID, OTHER_BOOK_ID)) {
            jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", bookId);
            jdbcTemplate.update("DELETE FROM book WHERE id = ?", bookId);
        }
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
            jdbcTemplate.update("DELETE FROM reader WHERE id = ?", readerId);
        }
    }

    @TestConfiguration
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(STARTED_AT);
        }
    }

    /**
     * 만료 경계를 한 컨텍스트 안에서 넘나들어야 해서 {@link Clock#fixed} 대신 옮길 수 있는 시계를 쓴다.
     *
     * <p>G05·G06 통합 테스트에도 같은 시계가 있다. 셋째 사용처라 공용 fixture 로 뽑는 것이 맞지만 그
     * 파일들은 이 작업의 수정 허용 범위 밖이라 여기서는 복사해 둔다.
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
