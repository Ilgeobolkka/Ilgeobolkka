package com.example.ilgeobolkka.airoute;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.facade.AiRouteCurrentFacade;
import com.example.ilgeobolkka.airoute.facade.AiRouteDeleteFacade;
import com.example.ilgeobolkka.airoute.facade.AiRouteSaveFacade;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.ilgeobolkka.airoute.service.delete.AiRouteDeleteService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteRequestFingerprint;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteResultItem;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT /api/books/{bookId}/ai-routes/current} 와 {@code DELETE /api/ai-routes/{routeId}} 의
 * 소유·직렬화·후속 선택 계약을 실제 MySQL 로 확인한다. T-AIR-005·016·019 의 현재 경로·삭제 부분이다.
 *
 * <p>클래스에 {@code @Transactional} 을 붙이지 않는다. 붙이면 모든 요청이 한 transaction 에 갇혀 지정·
 * 삭제·저장이 서로의 commit 을 볼 수 없고, 실패가 아무것도 남기지 않았다는 단언도 rollback 과 구분되지
 * 않는다.
 *
 * <p>동시성은 Facade 를 직접 부른다. MockMvc 인스턴스를 여러 thread 가 공유하는 것을 피하려는 것이고,
 * 직렬화는 Controller 가 아니라 Facade 의 transaction 경계에 있다.
 */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum471-test",
            "openai.api-key=not-a-real-key-scrum471-test",
            "openai.data-policy-version=policy-test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteCurrentDeleteMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRouteCurrentDeleteMySqlIntegrationTest {

    private static final long READER_ID = 471_001L;
    private static final long OTHER_READER_ID = 471_002L;
    private static final long BOOK_ID = 471_101L;
    private static final long OTHER_BOOK_ID = 471_102L;
    private static final long FIRST_PAGE_ID = 471_201L;
    private static final long SECOND_PAGE_ID = 471_202L;
    private static final long OTHER_BOOK_PAGE_ID = 471_203L;
    private static final long FIRST_RENTAL_ID = 471_301L;
    private static final long SECOND_RENTAL_ID = 471_302L;
    private static final long OTHER_BOOK_RENTAL_ID = 471_303L;
    private static final long OTHER_READER_FIRST_RENTAL_ID = 471_304L;
    private static final long OTHER_READER_SECOND_RENTAL_ID = 471_305L;

    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String PURPOSE = "핵심 개념만 빠르게";

    /** 두 페이지를 모두 대여해 추가 잉크를 0 으로 만든다. 이 테스트의 관심사는 예산이 아니다. */
    private static final int BUDGET = 0;

    private static final int INK_BALANCE = 10;

    private static final Instant STARTED_AT = Instant.parse("2026-08-13T00:00:00.123456Z");

    /** 경로 저장 사이에 시계를 옮기는 간격. 저장 순서가 {@code createdAt} 만으로도 정해진다. */
    private static final Duration SAVE_INTERVAL = Duration.ofMinutes(1);

    /** 30일 대여. 경로를 여러 개 저장하는 동안 대여가 만료되지 않아야 한다. */
    private static final Duration RENTAL_PERIOD = Duration.ofDays(30);

    /** {@code DATETIME(6)} 픽스처를 UTC 로 넣는 형식. 애플리케이션이 같은 열을 UTC 로 읽는다. */
    private static final DateTimeFormatter UTC_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final AiRouteSaveFacade saveFacade;
    private final AiRouteCurrentFacade currentFacade;
    private final AiRouteDeleteFacade deleteFacade;
    private final AiRouteDeleteService deleteService;
    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final TransactionTemplate transactionTemplate;
    private final MutableClock clock;

    @Autowired
    AiRouteCurrentDeleteMySqlIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            AiRouteSaveFacade saveFacade,
            AiRouteCurrentFacade currentFacade,
            AiRouteDeleteFacade deleteFacade,
            AiRouteDeleteService deleteService,
            AiRouteGenerationRepository generationRepository,
            AiRouteGenerationLifecycleService lifecycleService,
            PlatformTransactionManager transactionManager,
            MutableClock clock) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.saveFacade = saveFacade;
        this.currentFacade = currentFacade;
        this.deleteFacade = deleteFacade;
        this.deleteService = deleteService;
        this.generationRepository = generationRepository;
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
        페이지를_대여한다(READER_ID, FIRST_RENTAL_ID, FIRST_PAGE_ID);
        페이지를_대여한다(READER_ID, SECOND_RENTAL_ID, SECOND_PAGE_ID);
        페이지를_대여한다(READER_ID, OTHER_BOOK_RENTAL_ID, OTHER_BOOK_PAGE_ID);
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    // --- 현재 경로 지정 -------------------------------------------------------

    @Test
    void 같은_도서의_다른_경로를_현재로_지정한다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        경로를_저장한다(READER_ID, BOOK_ID);

        현재로_지정한다(READER_ID, BOOK_ID, 먼저)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(먼저))
                .andExpect(jsonPath("$.current").value(true));

        assertAll(
                () -> assertEquals(먼저, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_수(READER_ID, BOOK_ID)));
    }

    @Test
    void 이미_현재인_경로를_다시_지정해도_한_건이다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);

        현재로_지정한다(READER_ID, BOOK_ID, routeId).andExpect(status().isOk());

        assertAll(
                () -> assertEquals(routeId, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)));
    }

    @Test
    void 다른_도서의_경로는_404로_거부한다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);
        long 다른_도서_경로 = 경로를_저장한다(READER_ID, OTHER_BOOK_ID);

        현재로_지정한다(READER_ID, OTHER_BOOK_ID, routeId).andExpect(status().isNotFound());

        assertAll(
                () -> assertEquals(routeId, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(다른_도서_경로, 현재_경로_식별자(READER_ID, OTHER_BOOK_ID)));
    }

    @Test
    void 다른_독자의_경로는_404로_거부하고_현재_경로를_바꾸지_않는다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);

        현재로_지정한다(OTHER_READER_ID, BOOK_ID, routeId).andExpect(status().isNotFound());

        assertAll(
                () -> assertEquals(routeId, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertNull(현재_경로_식별자(OTHER_READER_ID, BOOK_ID)));
    }

    @Test
    void 없는_경로는_404로_거부한다() throws Exception {
        현재로_지정한다(READER_ID, BOOK_ID, 471_999L).andExpect(status().isNotFound());

        assertNull(현재_경로_식별자(READER_ID, BOOK_ID));
    }

    @Test
    void routeId가_없는_요청은_400이다() throws Exception {
        mockMvc.perform(
                        put("/api/books/{bookId}/ai-routes/current", BOOK_ID)
                                .with(authentication(인증된_독자(READER_ID)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 현재_지정은_경로의_항목과_진행을_바꾸지_않는다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        경로를_저장한다(READER_ID, BOOK_ID);
        항목을_열람_완료로_표시한다(먼저);
        Map<String, Object> 이전 = 경로_내용_상태(먼저);

        현재로_지정한다(READER_ID, BOOK_ID, 먼저).andExpect(status().isOk());

        assertEquals(이전, 경로_내용_상태(먼저));
    }

    // --- 삭제 ---------------------------------------------------------------

    @Test
    void 현재가_아닌_경로를_지우면_현재_경로가_그대로다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);

        삭제를_요청한다(READER_ID, 먼저).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(나중, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(0, 경로_항목_수(먼저)));
    }

    @Test
    void 현재_경로를_지우면_남은_최신_경로가_현재가_된다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 중간 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);

        삭제를_요청한다(READER_ID, 나중).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(중간, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_항목_수(먼저)));
    }

    /**
     * 저장 시각이 같은 경로들의 후속 선택. {@code createdAt} 만으로는 순서가 정해지지 않아 보조 정렬
     * {@code id DESC} 가 정본이다.
     */
    @Test
    void 저장_시각이_같으면_식별자가_큰_경로가_현재가_된다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 중간 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);
        저장_시각을_맞춘다(먼저, 중간, 나중);

        삭제를_요청한다(READER_ID, 나중).andExpect(status().isNoContent());

        assertEquals(중간, 현재_경로_식별자(READER_ID, BOOK_ID));
    }

    @Test
    void 마지막_경로를_지우면_현재_경로가_없다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);

        삭제를_요청한다(READER_ID, routeId).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(0, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(0, 경로_항목_수(routeId)));
    }

    @Test
    void 삭제는_다른_도서의_현재_경로를_건드리지_않는다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);
        long 다른_도서_경로 = 경로를_저장한다(READER_ID, OTHER_BOOK_ID);

        삭제를_요청한다(READER_ID, routeId).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(0, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(다른_도서_경로, 현재_경로_식별자(READER_ID, OTHER_BOOK_ID)));
    }

    /**
     * 같은 도서에 두 독자가 각자 현재 경로를 가진 상태에서 한쪽이 자기 경로를 지운다. 삭제가 쓰는 잠금·
     * 후속 선택·포인터 삭제에서 {@code readerId} 조건이 빠지면 남의 현재 경로를 지우거나
     * {@code uk_ai_route_current_route} 매칭으로 남의 행을 조용히 갱신하는데, 그 회귀를 여기서 잡는다.
     */
    @Test
    void 삭제는_같은_도서의_다른_독자_현재_경로를_건드리지_않는다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 다른_독자_경로 = 다른_독자의_경로를_저장한다();

        삭제를_요청한다(READER_ID, 나중).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(먼저, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(다른_독자_경로, 현재_경로_식별자(OTHER_READER_ID, BOOK_ID)),
                () -> assertEquals(1, 경로_수(OTHER_READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_항목_수(다른_독자_경로)));
    }

    @Test
    void 다른_독자의_경로_삭제는_404이고_아무것도_지우지_않는다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);

        삭제를_요청한다(OTHER_READER_ID, routeId).andExpect(status().isNotFound());

        assertAll(
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_항목_수(routeId)),
                () -> assertEquals(routeId, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals("SAVED", 생성_상태(경로의_생성(routeId))));
    }

    @Test
    void 삭제_재시도는_404이고_남은_경로를_건드리지_않는다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);
        UUID 남은_생성 = 경로의_생성(먼저);

        삭제를_요청한다(READER_ID, 나중).andExpect(status().isNoContent());
        삭제를_요청한다(READER_ID, 나중).andExpect(status().isNotFound());

        assertAll(
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(먼저, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_항목_수(먼저)),
                () -> assertEquals("SAVED", 생성_상태(남은_생성)));
    }

    // --- CONSUMED 전이 -------------------------------------------------------

    @Test
    void 삭제는_남은_생성을_CONSUMED로_바꾼다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);
        UUID generationId = 경로의_생성(routeId);

        삭제를_요청한다(READER_ID, routeId).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals("CONSUMED", 생성_상태(generationId)),
                () -> assertNull(저장_경로_식별자(generationId)));
    }

    @Test
    void 삭제한_경로의_생성은_다시_저장할_수_없다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);
        UUID generationId = 경로의_생성(routeId);

        삭제를_요청한다(READER_ID, routeId).andExpect(status().isNoContent());
        저장을_요청한다(READER_ID, generationId).andExpect(status().isConflict());

        assertAll(
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(0, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals("CONSUMED", 생성_상태(generationId)));
    }

    /** 저장 경로는 기한이 없어 멱등 상태가 정리된 뒤에도 지울 수 있다. 그때 삭제가 실패하면 안 된다. */
    @Test
    void 멱등_상태가_정리된_뒤에도_경로를_지울_수_있다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);
        멱등_상태를_지운다(경로의_생성(routeId));

        삭제를_요청한다(READER_ID, routeId).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(0, 현재_경로_수(READER_ID, BOOK_ID)));
    }

    // --- 보존 불변식 ----------------------------------------------------------

    @Test
    void 삭제는_잉크와_대여와_서재와_세션을_보존한다() throws Exception {
        long routeId = 경로를_저장한다(READER_ID, BOOK_ID);
        서재와_세션을_남긴다();
        Map<String, Object> 이전 = 잉크와_대여_상태();

        삭제를_요청한다(READER_ID, routeId).andExpect(status().isNoContent());

        assertEquals(이전, 잉크와_대여_상태());
    }

    /**
     * 포인터·항목·경로 삭제와 {@code CONSUMED} 전이, 후속 지정이 정말 한 transaction 인지 본다. 삭제가
     * 끝난 직후에 실패를 주입해, 그때까지 쌓인 변경이 하나도 남지 않는지 확인한다.
     */
    @Test
    void 삭제_도중_실패하면_경로와_현재_경로와_생성_상태가_함께_되돌아간다() {
        경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);
        UUID generationId = 경로의_생성(나중);

        assertThrows(
                InjectedFailure.class,
                () ->
                        transactionTemplate.executeWithoutResult(
                                status -> {
                                    deleteService.delete(READER_ID, 나중);
                                    throw new InjectedFailure();
                                }));

        assertAll(
                () -> assertEquals(2, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(2, 경로_항목_수(나중)),
                () -> assertEquals(나중, 현재_경로_식별자(READER_ID, BOOK_ID)),
                () -> assertEquals("SAVED", 생성_상태(generationId)),
                () -> assertEquals(나중, 저장_경로_식별자(generationId)));
    }

    // --- 동시성 --------------------------------------------------------------

    @Test
    void 서로_다른_경로를_동시에_현재로_지정해도_한_건이다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);

        동시에_실행한다(
                () -> currentFacade.changeCurrent(READER_ID, BOOK_ID, 먼저),
                () -> currentFacade.changeCurrent(READER_ID, BOOK_ID, 나중));

        assertAll(
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertTrue(List.of(먼저, 나중).contains(현재_경로_식별자(READER_ID, BOOK_ID))));
    }

    @Test
    void 현재_지정과_삭제를_교차해도_현재_경로는_최대_하나다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);

        동시에_실행한다(
                () -> currentFacade.changeCurrent(READER_ID, BOOK_ID, 먼저),
                () -> deleteFacade.deleteRoute(READER_ID, 나중));

        assertAll(
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(먼저, 현재_경로_식별자(READER_ID, BOOK_ID)));
    }

    @Test
    void 두_경로를_동시에_지우면_현재_경로가_없다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);

        동시에_실행한다(
                () -> deleteFacade.deleteRoute(READER_ID, 먼저),
                () -> deleteFacade.deleteRoute(READER_ID, 나중));

        assertAll(
                () -> assertEquals(0, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(0, 현재_경로_수(READER_ID, BOOK_ID)));
    }

    /** 저장의 {@code generation → current} 와 지정의 {@code route → current} 가 만나는 유일한 조합이다. */
    @Test
    void 저장과_현재_지정을_교차해도_현재_경로는_최대_하나다() throws Exception {
        long 기존 = 경로를_저장한다(READER_ID, BOOK_ID);
        UUID 새_생성 = 완료된_생성을_만든다(READER_ID, BOOK_ID);

        동시에_실행한다(
                () -> saveFacade.saveRoute(READER_ID, 새_생성),
                () -> currentFacade.changeCurrent(READER_ID, BOOK_ID, 기존));

        assertAll(
                () -> assertEquals(2, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)));
    }

    /**
     * 같은 경로를 동시에 지정·삭제한다. "경로 행 잠금이 지정과 삭제의 직렬화 지점"이라는 설계의 핵심 주장을
     * 직접 본다. 지정이 먼저면 둘 다 성공하고, 삭제가 먼저면 지정이 404 로 진다. 어느 쪽이든 그 경로는
     * 사라지고 현재 경로는 남은 경로 하나를 가리킨다.
     */
    @Test
    void 같은_경로를_동시에_지정하고_지워도_현재_경로는_남은_경로다() throws Exception {
        long 먼저 = 경로를_저장한다(READER_ID, BOOK_ID);
        long 나중 = 경로를_저장한다(READER_ID, BOOK_ID);

        동시에_실행한다(
                () -> currentFacade.changeCurrent(READER_ID, BOOK_ID, 나중),
                () -> deleteFacade.deleteRoute(READER_ID, 나중),
                List.of(AiRouteNotFoundException.class));

        assertAll(
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(먼저, 현재_경로_식별자(READER_ID, BOOK_ID)));
    }

    @Test
    void 저장과_삭제를_교차해도_현재_경로는_최대_하나다() throws Exception {
        long 기존 = 경로를_저장한다(READER_ID, BOOK_ID);
        UUID 새_생성 = 완료된_생성을_만든다(READER_ID, BOOK_ID);

        동시에_실행한다(
                () -> saveFacade.saveRoute(READER_ID, 새_생성),
                () -> deleteFacade.deleteRoute(READER_ID, 기존));

        assertAll(
                () -> assertEquals(1, 경로_수(READER_ID, BOOK_ID)),
                () -> assertEquals(1, 현재_경로_수(READER_ID, BOOK_ID)),
                () -> assertNotEquals(기존, 현재_경로_식별자(READER_ID, BOOK_ID)));
    }

    // --- 요청 --------------------------------------------------------------

    private ResultActions 현재로_지정한다(long readerId, long bookId, long routeId) throws Exception {
        return mockMvc.perform(
                put("/api/books/{bookId}/ai-routes/current", bookId)
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeId\": %d}".formatted(routeId)));
    }

    private ResultActions 삭제를_요청한다(long readerId, long routeId) throws Exception {
        return mockMvc.perform(
                delete("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf()));
    }

    private ResultActions 저장을_요청한다(long readerId, UUID generationId) throws Exception {
        return mockMvc.perform(
                post("/api/ai-route-generations/{generationId}/routes", generationId)
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf()));
    }

    /**
     * 두 작업을 같은 순간에 시작시킨다. 어느 쪽이 먼저 끝나도 되지만 <b>둘 다 성공</b>해야 한다. 이
     * 작업의 산출물이 교착도 중복 키 충돌도 없이 양쪽이 끝나는 것이기 때문이다.
     *
     * <p>예외를 삼키지 않는다. 삼키면 남는 상태만으로는 실패를 구분할 수 없다. 예를 들어 두 지정이 모두
     * 터져도 직전 저장이 남긴 현재 경로 한 건이 그대로라 단언이 통과한다. {@code future.get()} 이 그대로
     * 던지게 두어 어느 쪽이 실패해도 테스트가 깨지게 한다.
     */
    private void 동시에_실행한다(Runnable 첫째, Runnable 둘째) throws Exception {
        동시에_실행한다(첫째, 둘째, List.of());
    }

    /**
     * 허용 목록에 있는 예외만 삼킨다. 같은 경로를 노린 지정·삭제처럼 <b>한쪽이 지는 것이 계약</b>인 조합에만
     * 목록을 채운다. 교착과 중복 키는 어느 조합에서도 허용하지 않는다.
     */
    private void 동시에_실행한다(
            Runnable 첫째, Runnable 둘째, List<Class<? extends RuntimeException>> 허용_예외)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch 출발 = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable 작업 : List.of(첫째, 둘째)) {
                futures.add(
                        executor.submit(
                                () -> {
                                    출발.await();
                                    try {
                                        작업.run();
                                    } catch (RuntimeException 예외) {
                                        if (허용_예외.stream()
                                                .noneMatch(허용 -> 허용.isInstance(예외))) {
                                            throw 예외;
                                        }
                                    }
                                    return null;
                                }));
            }
            출발.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "동시 요청이 끝나지 않았다");
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // --- 픽스처 ------------------------------------------------------------

    /**
     * 생성부터 저장까지 한 번에 끝내고 저장된 경로 식별자를 돌려준다. 부를 때마다 시계를 1분씩 옮겨
     * 저장 순서가 {@code createdAt} 만으로도 정해지게 한다.
     */
    private long 경로를_저장한다(long readerId, long bookId) {
        UUID generationId = 완료된_생성을_만든다(readerId, bookId);
        clock.set(clock.instant().plus(SAVE_INTERVAL));
        return saveFacade.saveRoute(readerId, generationId).route().routeId();
    }

    /**
     * 다른 독자가 같은 도서에 자기 경로와 현재 경로를 갖게 한다. 두 페이지를 먼저 대여하는 이유는 추가 잉크
     * 예산이 0 이라 권한이 없으면 저장 자체가 거부되기 때문이다.
     */
    private long 다른_독자의_경로를_저장한다() {
        페이지를_대여한다(OTHER_READER_ID, OTHER_READER_FIRST_RENTAL_ID, FIRST_PAGE_ID);
        페이지를_대여한다(OTHER_READER_ID, OTHER_READER_SECOND_RENTAL_ID, SECOND_PAGE_ID);

        return 경로를_저장한다(OTHER_READER_ID, BOOK_ID);
    }

    private UUID 완료된_생성을_만든다(long readerId, long bookId) {
        UUID generationId = UUID.randomUUID();
        AiRouteGenerationCommand command =
                AiRouteGenerationCommand.forInkBudget(
                        bookId, CONTENT_VERSION, PURPOSE, BUDGET, INK_BALANCE);
        Instant createdAt = clock.instant();
        transactionTemplate.executeWithoutResult(
                status ->
                        generationRepository.save(
                                AiRouteGeneration.start(
                                        generationId,
                                        readerId,
                                        UUID.randomUUID(),
                                        AiRouteRequestFingerprint.of(command),
                                        command,
                                        createdAt)));
        transactionTemplate.executeWithoutResult(
                status -> lifecycleService.completeWithRoute(generationId, 항목(bookId)));
        return generationId;
    }

    private static List<AiRouteResultItem> 항목(long bookId) {
        if (bookId == OTHER_BOOK_ID) {
            return List.of(
                    new AiRouteResultItem(
                            OTHER_BOOK_PAGE_ID,
                            1,
                            AiRouteItemRelevance.HIGH,
                            false,
                            AiRouteItemRole.CORE,
                            AiRouteAdditionalCostStatus.ONE_INK));
        }
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

    /** 세 경로의 저장 시각을 같게 만들어 보조 정렬만으로 후속 경로가 정해지게 한다. */
    private void 저장_시각을_맞춘다(long... routeIds) {
        for (long routeId : routeIds) {
            jdbcTemplate.update(
                    "UPDATE ai_reading_route SET created_at = ? WHERE id = ?",
                    UTC_DATETIME.format(STARTED_AT),
                    routeId);
        }
    }

    /** 진행이 삭제로만 사라지는지 보려고 항목 하나를 열람 완료로 만든다. */
    private void 항목을_열람_완료로_표시한다(long routeId) {
        jdbcTemplate.update(
                """
                UPDATE ai_reading_route_item SET opened_at = ?
                WHERE route_id = ? AND position = 1
                """,
                UTC_DATETIME.format(STARTED_AT),
                routeId);
    }

    /**
     * 임시 결과 보관 기간이 지나 멱등 행이 정리된 상태를 만든다. 경로의 {@code generation_id} 는 기한
     * 없이 남으므로 생성 행만 지운다.
     */
    private void 멱등_상태를_지운다(UUID generationId) {
        jdbcTemplate.update(
                "DELETE FROM ai_route_generation WHERE generation_id = ?", generationId.toString());
    }

    private void 서재와_세션을_남긴다() {
        jdbcTemplate.update(
                """
                INSERT INTO library_entry (reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, 1, ?)
                """,
                READER_ID,
                BOOK_ID,
                UTC_DATETIME.format(STARTED_AT));
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (reader_id, book_id, current_page_number, viewer_session_id, updated_at)
                VALUES (?, ?, 1, ?, ?)
                """,
                READER_ID,
                BOOK_ID,
                UUID.randomUUID().toString(),
                UTC_DATETIME.format(STARTED_AT));
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-08-12 00:00:00.000000')
                """,
                readerId,
                "scrum471-" + readerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)",
                readerId,
                INK_BALANCE);
    }

    private void 도서와_페이지를_생성한다() {
        도서를_생성한다(BOOK_ID, "SCRUM-471 테스트 도서");
        도서를_생성한다(OTHER_BOOK_ID, "SCRUM-471 다른 도서");
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
    private void 페이지를_대여한다(long readerId, long rentalId, long bookPageId) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rentalId,
                readerId,
                bookPageId,
                UTC_DATETIME.format(STARTED_AT),
                UTC_DATETIME.format(STARTED_AT.plus(RENTAL_PERIOD)));
    }

    // --- 조회 --------------------------------------------------------------

    private UUID 경로의_생성(long routeId) {
        return UUID.fromString(
                jdbcTemplate.queryForObject(
                        "SELECT generation_id FROM ai_reading_route WHERE id = ?",
                        String.class,
                        routeId));
    }

    private String 생성_상태(UUID generationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ai_route_generation WHERE generation_id = ?",
                String.class,
                generationId.toString());
    }

    private Long 저장_경로_식별자(UUID generationId) {
        return jdbcTemplate.queryForObject(
                "SELECT saved_route_id FROM ai_route_generation WHERE generation_id = ?",
                Long.class,
                generationId.toString());
    }

    /** 현재 경로 지정이 건드리지 않아야 하는 경로 내용. */
    private Map<String, Object> 경로_내용_상태(long routeId) {
        Map<String, Object> 상태 = new LinkedHashMap<>();
        상태.put(
                "route",
                jdbcTemplate.queryForMap(
                        """
                        SELECT normalized_purpose, request_type, max_additional_ink, depth,
                               content_version, completed_at, feedback, feedback_at, created_at
                        FROM ai_reading_route
                        WHERE id = ?
                        """,
                        routeId));
        상태.put(
                "items",
                jdbcTemplate.queryForList(
                        """
                        SELECT book_page_id, position, relevance, prerequisite, role, opened_at
                        FROM ai_reading_route_item
                        WHERE route_id = ?
                        ORDER BY position
                        """,
                        routeId));
        return 상태;
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
        return routeIds.isEmpty() ? null : routeIds.getFirst();
    }

    /** 경로 삭제가 건드리지 않아야 하는 값들. */
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

    /** 삭제가 끝난 직후에 던져 transaction 을 되돌리는 표시. 실제 코드가 던지는 예외와 겹치지 않는다. */
    static class InjectedFailure extends RuntimeException {

        InjectedFailure() {
            super("주입한 실패");
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
     * 저장 시각을 요청마다 옮겨 후속 현재 경로의 정렬을 만들 수 있어야 해서 {@link Clock#fixed} 대신
     * 옮길 수 있는 시계를 쓴다.
     *
     * <p>G05·G06·S01 통합 테스트에도 같은 시계가 있다. 공용 fixture 로 뽑는 것이 맞지만 그 파일들은 이
     * 작업의 수정 허용 범위 밖이라 여기서는 복사해 둔다.
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
