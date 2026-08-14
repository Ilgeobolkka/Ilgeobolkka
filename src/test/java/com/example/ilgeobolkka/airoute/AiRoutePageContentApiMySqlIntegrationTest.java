package com.example.ilgeobolkka.airoute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.airoute.facade.AiRouteContentFacade;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.reading.dto.PageContent;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.ilgeobolkka.reading.service.PageContentService;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 저장 경로 콘텐츠 제공의 권한·진행·동시성 계약을 실제 MySQL로 검증한다. T-AIR-006·016의 S04
 * 범위다.
 *
 * <p>클래스에 {@code @Transactional}을 붙이지 않는다. 각 HTTP 요청과 Facade 호출이 독립 transaction으로
 * 커밋되어야 반복·동시 요청과 롤백을 실제 운영 경계와 같이 확인할 수 있다.
 */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum472-test",
            "openai.api-key=not-a-real-key-scrum472-test",
            "openai.data-policy-version=policy-test",
            "content-storage.root=build/test-content/scrum-472-pages"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRoutePageContentApiMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRoutePageContentApiMySqlIntegrationTest {

    private static final long READER_ID = 472_001L;
    private static final long OTHER_READER_ID = 472_002L;
    private static final long BOOK_ID = 472_101L;
    private static final long OTHER_BOOK_ID = 472_102L;
    private static final long FIRST_PAGE_ID = 472_201L;
    private static final long SECOND_PAGE_ID = 472_202L;
    private static final long MISSING_IMAGE_PAGE_ID = 472_203L;
    private static final long OTHER_BOOK_PAGE_ID = 472_204L;
    private static final long ROUTE_ID = 472_301L;
    private static final long IMAGE_ROUTE_ID = 472_302L;
    private static final long OTHER_BOOK_ROUTE_ID = 472_303L;
    private static final long OWNERSHIP_PAYMENT_ID = 472_401L;
    private static final long OTHER_BOOK_OWNERSHIP_PAYMENT_ID = 472_402L;

    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String FIRST_CONTENT = "첫 번째 경로 페이지";
    private static final String SECOND_CONTENT = "두 번째 경로 페이지";
    private static final String OTHER_BOOK_CONTENT = "다른 도서의 경로 페이지";
    private static final Instant STARTED_AT = Instant.parse("2026-08-14T01:00:00.123456Z");
    private static final Duration RENTAL_PERIOD = Duration.ofDays(30);
    private static final DateTimeFormatter UTC_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AiRouteContentFacade contentFacade;
    private final ReadingFacade readingFacade;
    private final MutableClock clock;

    @MockitoSpyBean
    private PageContentService pageContentService;

    @MockitoSpyBean
    private AiReadingRouteRepository aiReadingRouteRepository;

    @Autowired
    AiRoutePageContentApiMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            AiRouteContentFacade contentFacade,
            ReadingFacade readingFacade,
            MutableClock clock) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.contentFacade = contentFacade;
        this.readingFacade = readingFacade;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자를_생성한다(READER_ID);
        독자를_생성한다(OTHER_READER_ID);
        도서와_페이지를_생성한다();
        경로를_생성한다();
        clock.set(STARTED_AT);
    }

    @AfterEach
    void tearDown() {
        reset(pageContentService, aiReadingRouteRepository);
        테스트_데이터를_정리한다();
    }

    @Test
    void T_AIR_006_페이지_열기_성공_뒤_콘텐츠와_최초_진행을_함께_제공한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string(FIRST_CONTENT))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        assertAll(
                () -> assertEquals(UTC_DATETIME.format(STARTED_AT), 항목_열람_시각(ROUTE_ID, 1)),
                () -> assertNull(경로_완료_시각(ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    @Test
    void T_AIR_006_페이지_이동_성공_뒤_이동한_페이지의_콘텐츠와_진행을_제공한다()
            throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        페이지를_이동한다(READER_ID, viewerSessionId, 2).andExpect(status().isOk());
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 2, viewerSessionId)
                .andExpect(status().isOk())
                .andExpect(content().string(SECOND_CONTENT));

        assertAll(
                () -> assertNull(항목_열람_시각(ROUTE_ID, 1)),
                () -> assertEquals(UTC_DATETIME.format(STARTED_AT), 항목_열람_시각(ROUTE_ID, 2)),
                () -> assertNull(경로_완료_시각(ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    @Test
    void T_AIR_006_페이지_열기_실패와_위치_불일치는_콘텐츠와_진행을_거부한다()
            throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        jdbcTemplate.update("UPDATE ink_account SET balance = 0 WHERE reader_id = ?", READER_ID);

        페이지를_이동한다(READER_ID, viewerSessionId, 2)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INK"));
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 2, viewerSessionId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertAll(
                () -> assertNull(항목_열람_시각(ROUTE_ID, 2)),
                () -> assertNull(경로_완료_시각(ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    /**
     * 거부 근거를 열람 중인 도서 하나로 좁힌다. 두 도서의 페이지 번호를 1로 맞춰 위치 불일치를 없애고,
     * 다른 도서를 소장까지 시켜 권한 부족도 없앤다. 도서 비교를 빼면 이 요청이 200과 진행을 남긴다.
     */
    @Test
    void T_AIR_006_다른_도서를_열람_중이면_콘텐츠와_진행을_거부한다() throws Exception {
        소장_기록을_생성한다(OTHER_BOOK_ID, OTHER_BOOK_OWNERSHIP_PAYMENT_ID);
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, OTHER_BOOK_ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertAll(
                () -> assertNull(항목_열람_시각(OTHER_BOOK_ROUTE_ID, 1)),
                () -> assertNull(경로_완료_시각(OTHER_BOOK_ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    @Test
    void T_AIR_006_열기_뒤_대여가_만료되면_콘텐츠와_진행을_거부한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 2);
        clock.set(STARTED_AT.plus(RENTAL_PERIOD));
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 2, viewerSessionId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertAll(
                () -> assertNull(항목_열람_시각(ROUTE_ID, 2)),
                () -> assertNull(경로_완료_시각(ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    @Test
    void T_AIR_006_소장_도서는_대여와_추가_과금_없이_콘텐츠를_제공한다() throws Exception {
        소장_기록을_생성한다();
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isOk())
                .andExpect(content().string(FIRST_CONTENT));

        assertAll(
                () -> assertEquals(0, 개수("page_rental", READER_ID)),
                () -> assertEquals(0, 개수("ink_ledger", READER_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    /**
     * 두 404의 근거를 각각 하나로 좁힌다. 다른 독자도 같은 도서 1페이지를 직접 열어 유효한 세션과 대여를
     * 갖게 하므로, 세션·권한 검증은 통과하고 경로 조회의 소유자 조건만 거부 근거로 남는다. 세션 없이
     * 요청하면 소유자 조건이 빠져도 세션 조회가 같은 404를 내 회귀가 가려진다.
     *
     * <p>비포함 페이지는 다른 경로가 실제로 가진 페이지로 고른다. {@code IMAGE_ROUTE_ID}에 페이지 1은
     * 없지만 같은 도서·같은 현재 세션 위치라 세션·권한 검증은 모두 통과한다. 소유자 조건이 빠지면 앞
     * 요청이, 경로 소속 조건이 빠지면 뒤 요청이 404가 아니라 200과 {@code FIRST_CONTENT}를 내고 진행까지
     * 남긴다.
     */
    @Test
    void T_AIR_016_다른_독자와_경로에_없는_페이지는_같은_404다() throws Exception {
        UUID otherViewerSessionId = 새_세션을_연다(OTHER_READER_ID, 1);
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);

        경로_콘텐츠를_요청한다(OTHER_READER_ID, ROUTE_ID, 1, otherViewerSessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        경로_콘텐츠를_요청한다(READER_ID, IMAGE_ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertAll(
                () -> assertNull(항목_열람_시각(ROUTE_ID, 1)),
                () -> assertNull(경로_완료_시각(ROUTE_ID)),
                () -> assertEquals(0, 열린_항목_수(IMAGE_ROUTE_ID)),
                () -> assertNull(경로_완료_시각(IMAGE_ROUTE_ID)));
    }

    @Test
    void 콘텐츠_POST는_인증_CSRF와_뷰어_헤더를_검증한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);

        mockMvc.perform(
                        post(
                                        "/api/ai-routes/{routeId}/pages/{pageNumber}/content",
                                        ROUTE_ID,
                                        1)
                                .with(authentication(인증된_독자(READER_ID)))
                                .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mockMvc.perform(
                        post(
                                        "/api/ai-routes/{routeId}/pages/{pageNumber}/content",
                                        ROUTE_ID,
                                        1)
                                .with(csrf())
                                .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(
                        post(
                                        "/api/ai-routes/{routeId}/pages/{pageNumber}/content",
                                        ROUTE_ID,
                                        1)
                                .with(authentication(인증된_독자(READER_ID)))
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(
                        post(
                                        "/api/ai-routes/{routeId}/pages/{pageNumber}/content",
                                        ROUTE_ID,
                                        1)
                                .with(authentication(인증된_독자(READER_ID)))
                                .with(csrf())
                                .header("X-Viewer-Session-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 같은_항목을_반복해도_최초_openedAt을_보존한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isOk());

        clock.set(STARTED_AT.plusSeconds(60));
        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isOk());

        assertAll(
                () -> assertEquals(UTC_DATETIME.format(STARTED_AT), 항목_열람_시각(ROUTE_ID, 1)),
                () -> assertNull(경로_완료_시각(ROUTE_ID)));
    }

    @Test
    void 완료한_경로를_반복해도_최초_openedAt과_completedAt을_보존한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 1, viewerSessionId)
                .andExpect(status().isOk());

        Instant completedAt = STARTED_AT.plusSeconds(60);
        clock.set(completedAt);
        페이지를_이동한다(READER_ID, viewerSessionId, 2).andExpect(status().isOk());
        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 2, viewerSessionId)
                .andExpect(status().isOk());
        ReaderState before = 독자_상태();

        clock.set(STARTED_AT.plusSeconds(120));
        경로_콘텐츠를_요청한다(READER_ID, ROUTE_ID, 2, viewerSessionId)
                .andExpect(status().isOk());

        assertAll(
                () -> assertEquals(UTC_DATETIME.format(completedAt), 항목_열람_시각(ROUTE_ID, 2)),
                () -> assertEquals(UTC_DATETIME.format(completedAt), 경로_완료_시각(ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    /**
     * 두 요청의 시각을 갈라 놓아야 항목 잠금이 사라진 것이 값으로 드러난다. 같은 시각을 쓰면 두 요청이
     * 모두 기록해도 결과가 같아 직렬화 제거를 식별하지 못한다.
     *
     * <p>첫 요청을 콘텐츠 읽기에서 붙잡아 아직 {@code openedAt}을 쓰지 않은 상태로 두고, 시계를 옮긴 뒤
     * 두 번째 요청을 경로 잠금까지 보낸다. 잠금이 있으면 두 번째는 첫 요청이 커밋한 뒤에야 항목을 읽어
     * {@code STARTED_AT}을 그대로 두고, 잠금이 없으면 비어 있는 항목을 먼저 읽어 두었다가 옮긴 시각으로
     * 덮어쓴다. 그래서 두 번째 요청은 첫 요청이 끝난 뒤에 풀어 준다.
     */
    @Test
    void 같은_항목의_동시_요청도_최초_openedAt을_한_번만_기록한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        CountDownLatch firstContentRead = new CountDownLatch(1);
        CountDownLatch allowFirstContent = new CountDownLatch(1);
        CountDownLatch allowSecondContent = new CountDownLatch(1);
        AtomicInteger contentReads = new AtomicInteger();
        doAnswer(invocation -> {
                    if (contentReads.incrementAndGet() == 1) {
                        firstContentRead.countDown();
                        assertTrue(
                                allowFirstContent.await(10, TimeUnit.SECONDS),
                                "첫 콘텐츠 제공 대기가 끝나지 않았다");
                    } else {
                        assertTrue(
                                allowSecondContent.await(10, TimeUnit.SECONDS),
                                "두 번째 콘텐츠 제공 대기가 끝나지 않았다");
                    }
                    return invocation.callRealMethod();
                })
                .when(pageContentService)
                .read(any(BookPage.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PageContent> first = executor.submit(
                    () -> contentFacade.provideContent(READER_ID, ROUTE_ID, 1, viewerSessionId));
            assertTrue(firstContentRead.await(10, TimeUnit.SECONDS), "첫 콘텐츠 읽기가 시작되지 않았다");

            clock.set(STARTED_AT.plusSeconds(60));
            Future<PageContent> second = executor.submit(
                    () -> contentFacade.provideContent(READER_ID, ROUTE_ID, 1, viewerSessionId));
            두_요청이_경로_잠금에_들어올_때까지_기다린다();

            allowFirstContent.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).body())
                    .containsExactly(FIRST_CONTENT.getBytes(StandardCharsets.UTF_8));
            allowSecondContent.countDown();
            assertThat(second.get(30, TimeUnit.SECONDS).body())
                    .containsExactly(FIRST_CONTENT.getBytes(StandardCharsets.UTF_8));

            assertAll(
                    () -> assertEquals(1, 열린_항목_수(ROUTE_ID)),
                    () -> assertEquals(UTC_DATETIME.format(STARTED_AT), 항목_열람_시각(ROUTE_ID, 1)),
                    () -> assertNull(경로_완료_시각(ROUTE_ID)));
        } finally {
            allowFirstContent.countDown();
            allowSecondContent.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 마지막_두_항목을_겹쳐_제공해도_completedAt을_한_번만_기록한다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 1);
        CountDownLatch firstContentRead = new CountDownLatch(1);
        CountDownLatch allowFirstContent = new CountDownLatch(1);
        doAnswer(invocation -> {
                    BookPage page = invocation.getArgument(0);
                    if (page.getPageNumber() == 1) {
                        firstContentRead.countDown();
                        assertTrue(
                                allowFirstContent.await(10, TimeUnit.SECONDS),
                                "첫 콘텐츠 제공 대기가 끝나지 않았다");
                    }
                    return invocation.callRealMethod();
                })
                .when(pageContentService)
                .read(any(BookPage.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PageContent> first = executor.submit(
                    () -> contentFacade.provideContent(READER_ID, ROUTE_ID, 1, viewerSessionId));
            assertTrue(firstContentRead.await(10, TimeUnit.SECONDS), "첫 콘텐츠 읽기가 시작되지 않았다");

            // 첫 요청은 이미 STARTED_AT을 읽어 두었다. 여기서 시계를 옮겨야 completedAt이 마지막 항목을
            // 연 뒤 요청의 시각인지, 먼저 시작한 요청의 시각인지가 값으로 갈린다.
            Instant lastOpenedAt = STARTED_AT.plusSeconds(30);
            clock.set(lastOpenedAt);
            readingFacade.movePage(READER_ID, viewerSessionId, 2);
            ReaderState before = 독자_상태();
            Future<PageContent> second = executor.submit(
                    () -> contentFacade.provideContent(READER_ID, ROUTE_ID, 2, viewerSessionId));
            두_요청이_경로_잠금에_들어올_때까지_기다린다();

            allowFirstContent.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).body())
                    .containsExactly(FIRST_CONTENT.getBytes(StandardCharsets.UTF_8));
            assertThat(second.get(30, TimeUnit.SECONDS).body())
                    .containsExactly(SECOND_CONTENT.getBytes(StandardCharsets.UTF_8));

            assertAll(
                    () -> assertEquals(2, 열린_항목_수(ROUTE_ID)),
                    () -> assertEquals(UTC_DATETIME.format(STARTED_AT), 항목_열람_시각(ROUTE_ID, 1)),
                    () -> assertEquals(
                            UTC_DATETIME.format(lastOpenedAt), 항목_열람_시각(ROUTE_ID, 2)),
                    () -> assertEquals(
                            UTC_DATETIME.format(lastOpenedAt), 경로_완료_시각(ROUTE_ID)),
                    () -> assertEquals(before, 독자_상태()));
        } finally {
            allowFirstContent.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 콘텐츠_저장소_실패는_진행을_남기지_않는다() throws Exception {
        UUID viewerSessionId = 새_세션을_연다(READER_ID, 3);
        ReaderState before = 독자_상태();

        경로_콘텐츠를_요청한다(READER_ID, IMAGE_ROUTE_ID, 3, viewerSessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertAll(
                () -> assertNull(항목_열람_시각(IMAGE_ROUTE_ID, 3)),
                () -> assertNull(경로_완료_시각(IMAGE_ROUTE_ID)),
                () -> assertEquals(before, 독자_상태()));
    }

    private UUID 새_세션을_연다(long readerId, int pageNumber) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                                .with(authentication(인증된_독자(readerId)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pageNumber\":" + pageNumber + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("viewerSessionId")
                        .asText());
    }

    private ResultActions 페이지를_이동한다(long readerId, UUID viewerSessionId, int pageNumber)
            throws Exception {
        return mockMvc.perform(
                patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", viewerSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":" + pageNumber + "}"));
    }

    private ResultActions 경로_콘텐츠를_요청한다(
            long readerId, long routeId, int pageNumber, UUID viewerSessionId) throws Exception {
        return mockMvc.perform(
                post(
                                "/api/ai-routes/{routeId}/pages/{pageNumber}/content",
                                routeId,
                                pageNumber)
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", viewerSessionId));
    }

    /**
     * 두 요청이 모두 경로 잠금 조회에 들어올 때까지 기다린다. 두 번째 요청이 잠금 지점에 닿기 전에 첫
     * 요청을 풀면 두 요청이 순차로 끝나, 직렬화가 사라져도 겹침이 만들어지지 않아 테스트가 통과한다.
     *
     * <p>spy 를 스텁하지 않고 기록만 읽는다. Mockito 는 호출을 실제 실행 전에 기록하므로 두 번째 호출이
     * 보이면 그 요청은 잠금 조회에 들어갔고, 직렬화가 살아 있으면 그 자리에서 첫 요청을 기다린다.
     * 스텁으로 가로채는 방법은 쓸 수 없다. repository 질의는 추상 메서드라
     * {@code invocation.callRealMethod()} 가 실패하고, {@code MockitoSpyBean} 은 원본 bean 을
     * {@code spiedInstance} 로 남기지 않아 직접 위임할 대상도 없다.
     */
    private void 두_요청이_경로_잠금에_들어올_때까지_기다린다() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (경로_잠금_조회_수() >= 2) {
                return;
            }
            Thread.sleep(50);
        }
        fail("두 번째 요청이 경로 잠금까지 오지 않았다");
    }

    private long 경로_잠금_조회_수() {
        return mockingDetails(aiReadingRouteRepository).getInvocations().stream()
                .filter(invocation ->
                        invocation.getMethod().getName().equals("findOwnedByIdForUpdate"))
                .count();
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-08-14 00:00:00.000000')
                """,
                readerId,
                "scrum472-" + readerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 10)", readerId);
    }

    private void 도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won, content_version)
                VALUES (?, '인문', 'SCRUM-472 테스트 도서', '테스트 저자', 3, 10000, ?)
                """,
                BOOK_ID,
                CONTENT_VERSION);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, 1, 'TEXT', ?, '첫 주제', 60)
                """,
                FIRST_PAGE_ID,
                BOOK_ID,
                FIRST_CONTENT);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, 2, 'TEXT', ?, '둘째 주제', 60)
                """,
                SECOND_PAGE_ID,
                BOOK_ID,
                SECOND_CONTENT);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, image_path,
                     ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, 3, 'IMAGE', 'build/test-content/scrum-472-pages/missing.png',
                        '셋째 주제', 60)
                """,
                MISSING_IMAGE_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won, content_version)
                VALUES (?, '인문', 'SCRUM-472 다른 도서', '테스트 저자', 1, 10000, ?)
                """,
                OTHER_BOOK_ID,
                CONTENT_VERSION);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, 1, 'TEXT', ?, '다른 도서 주제', 60)
                """,
                OTHER_BOOK_PAGE_ID,
                OTHER_BOOK_ID,
                OTHER_BOOK_CONTENT);
    }

    private void 경로를_생성한다() {
        경로를_생성한다(ROUTE_ID, BOOK_ID, UUID.randomUUID(), "두 페이지 경로");
        경로_항목을_생성한다(ROUTE_ID, BOOK_ID, FIRST_PAGE_ID, 1);
        경로_항목을_생성한다(ROUTE_ID, BOOK_ID, SECOND_PAGE_ID, 2);
        경로를_생성한다(IMAGE_ROUTE_ID, BOOK_ID, UUID.randomUUID(), "이미지 경로");
        경로_항목을_생성한다(IMAGE_ROUTE_ID, BOOK_ID, MISSING_IMAGE_PAGE_ID, 1);
        경로를_생성한다(OTHER_BOOK_ROUTE_ID, OTHER_BOOK_ID, UUID.randomUUID(), "다른 도서 경로");
        경로_항목을_생성한다(OTHER_BOOK_ROUTE_ID, OTHER_BOOK_ID, OTHER_BOOK_PAGE_ID, 1);
    }

    private void 경로를_생성한다(long routeId, long bookId, UUID generationId, String purpose) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version,
                     normalized_purpose, request_type, max_additional_ink, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'INK_BUDGET', 10, ?)
                """,
                routeId,
                generationId.toString(),
                READER_ID,
                bookId,
                CONTENT_VERSION,
                purpose,
                UTC_DATETIME.format(STARTED_AT.minusSeconds(60)));
    }

    private void 경로_항목을_생성한다(long routeId, long bookId, long pageId, int position) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route_item
                    (route_id, book_id, book_page_id, position, relevance, prerequisite, role)
                VALUES (?, ?, ?, ?, 'HIGH', FALSE, 'CORE')
                """,
                routeId,
                bookId,
                pageId,
                position);
    }

    private void 소장_기록을_생성한다() {
        소장_기록을_생성한다(BOOK_ID, OWNERSHIP_PAYMENT_ID);
    }

    private void 소장_기록을_생성한다(long bookId, long ownershipPaymentId) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 10000, ?, ?)
                """,
                ownershipPaymentId,
                READER_ID,
                bookId,
                UUID.randomUUID().toString(),
                UTC_DATETIME.format(STARTED_AT.minusSeconds(60)),
                UTC_DATETIME.format(STARTED_AT.minusSeconds(30)));
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                bookId,
                ownershipPaymentId,
                UTC_DATETIME.format(STARTED_AT.minusSeconds(30)));
    }

    private String 항목_열람_시각(long routeId, int pageNumber) {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(item.opened_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM ai_reading_route_item item
                JOIN book_page page ON page.id = item.book_page_id
                WHERE item.route_id = ? AND page.page_number = ?
                """,
                String.class,
                routeId,
                pageNumber);
    }

    private String 경로_완료_시각(long routeId) {
        return jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s.%f') FROM ai_reading_route WHERE id = ?",
                String.class,
                routeId);
    }

    private int 열린_항목_수(long routeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_reading_route_item WHERE route_id = ? AND opened_at IS NOT NULL",
                Integer.class,
                routeId);
    }

    private ReaderState 독자_상태() {
        return new ReaderState(
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID),
                jdbcTemplate.queryForList(
                        "SELECT page_rental_id, amount, balance_after, occurred_at FROM ink_ledger WHERE reader_id = ? ORDER BY id",
                        READER_ID),
                jdbcTemplate.queryForList(
                        "SELECT book_page_id, rented_at, expires_at FROM page_rental WHERE reader_id = ? ORDER BY id",
                        READER_ID),
                jdbcTemplate.queryForList(
                        "SELECT book_id, current_page_number, viewer_session_id, updated_at FROM reading_session WHERE reader_id = ?",
                        READER_ID),
                jdbcTemplate.queryForList(
                        "SELECT book_id, last_page_number, updated_at FROM library_entry WHERE reader_id = ?",
                        READER_ID));
    }

    private int 개수(String table, long readerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE reader_id = ?", Integer.class, readerId);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update(
                "DELETE FROM ai_route_current WHERE book_id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM ai_reading_route_item WHERE route_id IN (?, ?, ?)",
                ROUTE_ID,
                IMAGE_ROUTE_ID,
                OTHER_BOOK_ROUTE_ID);
        jdbcTemplate.update(
                "DELETE FROM ai_reading_route WHERE id IN (?, ?, ?)",
                ROUTE_ID,
                IMAGE_ROUTE_ID,
                OTHER_BOOK_ROUTE_ID);
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
            jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", readerId);
        }
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?, ?)", BOOK_ID, OTHER_BOOK_ID);
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
            jdbcTemplate.update("DELETE FROM reader WHERE id = ?", readerId);
        }
    }

    private record ReaderState(
            int balance,
            List<Map<String, Object>> ledger,
            List<Map<String, Object>> rentals,
            List<Map<String, Object>> sessions,
            List<Map<String, Object>> library) {}

    @TestConfiguration
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(STARTED_AT);
        }
    }

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
