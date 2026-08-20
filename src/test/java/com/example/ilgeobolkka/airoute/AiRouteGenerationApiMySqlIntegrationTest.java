package com.example.ilgeobolkka.airoute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteRequestFingerprint;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** SCRUM-470 생성·조회 HTTP 계약을 실제 MySQL과 Spring Security 경계에서 검증한다. */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum470-test",
            "openai.api-key=not-a-real-key-scrum470-test",
            "openai.data-policy-version=policy-v1"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteGenerationApiMySqlIntegrationTest.TestBeans.class)
@ExtendWith(OutputCaptureExtension.class)
class AiRouteGenerationApiMySqlIntegrationTest {

    private static final long READER_ID = 470_001L;
    private static final long OTHER_READER_ID = 470_002L;
    private static final long BOOK_ID = 470_101L;
    private static final long PAGE_ID = 470_201L;
    private static final long SECOND_PAGE_ID = 470_202L;
    private static final long OWNERSHIP_PAYMENT_ID = 470_301L;
    private static final String CONTENT_VERSION = "ai-route-v1";
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final MutableClock clock;
    private final FakeEmbeddingGateway embeddingGateway;
    private final FakeRouteGateway routeGateway;
    private final Statistics statistics;

    @Autowired
    AiRouteGenerationApiMySqlIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            MutableClock clock,
            FakeEmbeddingGateway embeddingGateway,
            FakeRouteGateway routeGateway,
            EntityManagerFactory entityManagerFactory) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.embeddingGateway = embeddingGateway;
        this.routeGateway = routeGateway;
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        clock.set(NOW);
        embeddingGateway.reset();
        routeGateway.reset();
        독자와_잉크를_생성한다(READER_ID, "scrum-470@example.com");
        독자와_잉크를_생성한다(OTHER_READER_ID, "scrum-470-other@example.com");
        지원_도서와_페이지를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void 새_ROUTE는_201과_전체_JSON을_반환하고_같은_요청과_GET은_200이다() throws Exception {
        UUID key = UUID.randomUUID();
        String body = """
                {
                  "purpose": "  핵심   개념  ",
                  "maxAdditionalInk": 1,
                  "depth": null
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generationId").isString())
                .andExpect(jsonPath("$.status").value("ROUTE"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("핵심 개념"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-13T12:15:00Z"))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(9))
                .andExpect(jsonPath("$.noRouteReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.minimumRequiredInk").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].pageNumber").value(1))
                .andExpect(jsonPath("$.items[0].relevance").value("HIGH"))
                .andExpect(jsonPath("$.items[0].prerequisite").value(false))
                .andExpect(jsonPath("$.items[0].role").value("CORE"))
                .andExpect(jsonPath("$.items[0].estimatedMinutes").value(2))
                .andExpect(jsonPath("$.items[0].guide").value("핵심 흐름에 관한 핵심 개념을 다루는 페이지입니다."))
                .andExpect(jsonPath("$.items[0].additionalCostStatus").value("ONE_INK"))
                .andReturn();
        String generationId = created.getResponse()
                .getContentAsString()
                .replaceFirst(".*\"generationId\":\"([^\"]+)\".*", "$1");
        assertEquals(
                "/api/ai-route-generations/" + generationId,
                created.getResponse().getHeader(HttpHeaders.LOCATION));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId));

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId))
                .andExpect(jsonPath("$.items[0].additionalCostStatus").value("ONE_INK"));

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(OTHER_READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/ai-route-generations/{generationId}/routes", generationId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.purpose").value("핵심 개념"))
                .andExpect(jsonPath("$.routeId").isNumber())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("핵심 개념"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-13T12:15:00Z"))
                .andExpect(jsonPath("$.routeId").isNumber())
                .andExpect(jsonPath("$.remainingDailyGenerations").value(9))
                .andExpect(jsonPath("$.noRouteReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.minimumRequiredInk").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 저장_경로를_삭제한_키의_생성_재요청은_CONSUMED를_반환하고_다시_실행하지_않는다() throws Exception {
        UUID key = UUID.randomUUID();
        String purpose = "삭제 뒤 생성 재요청";
        String generationId = 새_ROUTE를_생성한다(key, purpose);

        mockMvc.perform(post("/api/ai-route-generations/{generationId}/routes", generationId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated());
        long routeId = jdbcTemplate.queryForObject(
                "SELECT saved_route_id FROM ai_route_generation WHERE generation_id = ?",
                Long.class,
                generationId);
        int gatewayCalls = routeGateway.invocationCount();
        int generationCount = jdbcTemplate.queryForObject(
                "SELECT generation_count FROM ai_route_daily_usage WHERE reader_id = ?",
                Integer.class,
                READER_ID);

        mockMvc.perform(delete("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"purpose\":\"%s\",\"maxAdditionalInk\":1,\"depth\":null}")
                                .formatted(purpose))
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_GENERATION_CONSUMED"));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"다른 목적\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_IDEMPOTENCY_KEY_REUSED"));

        assertEquals(gatewayCalls, routeGateway.invocationCount());
        assertEquals(
                generationCount,
                jdbcTemplate.queryForObject(
                        "SELECT generation_count FROM ai_route_daily_usage WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
    }

    @Test
    void GENERATING은_202이고_NO_ROUTE는_오류가_아닌_201이다() throws Exception {
        UUID generatingId = UUID.randomUUID();
        UUID generatingKey = UUID.randomUUID();
        GENERATING을_생성한다(generatingId, generatingKey);

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generatingId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.generationId").value(generatingId.toString()))
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("진행 중 목적"))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(10))
                .andExpect(jsonPath("$.noRouteReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.minimumRequiredInk").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", generatingKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"진행 중 목적\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.generationId").value(generatingId.toString()))
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("진행 중 목적"))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(10))
                .andExpect(jsonPath("$.noRouteReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.minimumRequiredInk").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0));

        embeddingGateway.vector(0.0, 1.0);
        String noRouteGenerationId = mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"관련 페이지 없음\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generationId").isString())
                .andExpect(jsonPath("$.status").value("NO_ROUTE"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("관련 페이지 없음"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-13T12:15:00Z"))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(9))
                .andExpect(jsonPath("$.noRouteReason").value("NO_RELEVANT_PAGES"))
                .andExpect(jsonPath("$.minimumRequiredInk").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*\"generationId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", noRouteGenerationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(noRouteGenerationId))
                .andExpect(jsonPath("$.status").value("NO_ROUTE"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("관련 페이지 없음"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-13T12:15:00Z"))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(9))
                .andExpect(jsonPath("$.noRouteReason").value("NO_RELEVANT_PAGES"))
                .andExpect(jsonPath("$.minimumRequiredInk").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 예산_부족_NO_ROUTE는_POST와_GET에서_최소_필요_잉크를_유지한다() throws Exception {
        String generationId = mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"예산 부족\",\"maxAdditionalInk\":0,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generationId").isString())
                .andExpect(jsonPath("$.status").value("NO_ROUTE"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("예산 부족"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-13T12:15:00Z"))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(9))
                .andExpect(jsonPath("$.noRouteReason").value("INSUFFICIENT_BUDGET"))
                .andExpect(jsonPath("$.minimumRequiredInk").value(1))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*\"generationId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId))
                .andExpect(jsonPath("$.status").value("NO_ROUTE"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.purpose").value("예산 부족"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-13T12:15:00Z"))
                .andExpect(jsonPath("$.routeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.remainingDailyGenerations").value(9))
                .andExpect(jsonPath("$.noRouteReason").value("INSUFFICIENT_BUDGET"))
                .andExpect(jsonPath("$.minimumRequiredInk").value(1))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 같은_HTTP_요청은_콘텐츠_버전과_잔액이_바뀌어도_최초_결과를_재생한다() throws Exception {
        UUID key = UUID.randomUUID();
        String generationId = 새_ROUTE를_생성한다(key, "멱등 재생");
        jdbcTemplate.update("UPDATE book SET content_version = 'ai-route-v2' WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("UPDATE ink_account SET balance = 0 WHERE reader_id = ?", READER_ID);
        routeGateway.fail(OpenAiRouteException.Failure.BUDGET_LIMIT);

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"멱등 재생\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId))
                .andExpect(jsonPath("$.contentVersion").value(CONTENT_VERSION))
                .andExpect(jsonPath("$.status").value("ROUTE"));
    }

    @Test
    void 명시_예산과_null_기본_예산은_같은_멱등_요청이_아니다() throws Exception {
        UUID key = UUID.randomUUID();
        새_ROUTE를_생성한다(key, "명시 예산");

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"명시 예산\",\"maxAdditionalInk\":null,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void 기존_멱등_키여도_예산과_깊이를_함께_보내면_400이_우선한다() throws Exception {
        UUID key = UUID.randomUUID();
        새_ROUTE를_생성한다(key, "입력 우선순위");
        int gatewayCalls = routeGateway.invocationCount();

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"입력 우선순위\",\"maxAdditionalInk\":1,\"depth\":\"QUICK\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"입력 우선순위\",\"maxAdditionalInk\":-1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertEquals(gatewayCalls, routeGateway.invocationCount());
    }

    @Test
    void null_기본_예산_재시도는_잔액이_바뀌어도_최초_결과를_재생한다() throws Exception {
        UUID key = UUID.randomUUID();
        String body = "{\"purpose\":\"기본 예산\",\"maxAdditionalInk\":null,\"depth\":null}";

        String generationId = mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceFirst(".*\"generationId\":\"([^\"]+)\".*", "$1");
        jdbcTemplate.update("UPDATE ink_account SET balance = 0 WHERE reader_id = ?", READER_ID);

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId));
    }

    @Test
    void CONSUMED는_기본_예산과_소장_깊이도_원래_입력을_판별한다() throws Exception {
        UUID defaultBudgetKey = UUID.randomUUID();
        String defaultBudgetBody =
                "{\"purpose\":\"기본 예산 소비\",\"maxAdditionalInk\":null,\"depth\":null}";
        String defaultBudgetGenerationId = 생성하고_식별자를_반환한다(
                defaultBudgetKey, defaultBudgetBody);
        저장하고_삭제한다(defaultBudgetGenerationId);

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", defaultBudgetKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defaultBudgetBody)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_GENERATION_CONSUMED"));
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", defaultBudgetKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"기본 예산 소비\",\"maxAdditionalInk\":5,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_IDEMPOTENCY_KEY_REUSED"));

        소장한다();
        UUID ownedDepthKey = UUID.randomUUID();
        String ownedDepthBody =
                "{\"purpose\":\"소장 깊이 소비\",\"maxAdditionalInk\":null,\"depth\":\"QUICK\"}";
        String ownedDepthGenerationId = 생성하고_식별자를_반환한다(ownedDepthKey, ownedDepthBody);
        저장하고_삭제한다(ownedDepthGenerationId);

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", ownedDepthKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownedDepthBody)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_GENERATION_CONSUMED"));
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", ownedDepthKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"소장 깊이 소비\",\"maxAdditionalInk\":null,\"depth\":\"BALANCED\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void 생성_권한_조회_뒤_커밋된_대여가_과거_시각이어도_ONE_INK를_유지한다() throws Exception {
        String generationId = 새_ROUTE를_생성한다(UUID.randomUUID(), "비용 스냅샷");
        // 권한 조회 당시에는 보이지 않았지만 rented_at을 정한 뒤 늦게 커밋된 대여를 모사한다.
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                PAGE_ID,
                "2026-08-13 11:59:59.000000",
                "2026-09-12 11:59:59.000000");

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].additionalCostStatus").value("ONE_INK"));
    }

    @Test
    void 생성_시점의_대여와_소장_상태를_각각_ACTIVE_RENTAL과_OWNED로_고정한다() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                PAGE_ID,
                "2026-08-13 11:59:00.000000",
                "2026-08-13 12:00:30.000000");
        String rentalGenerationId = 새_ROUTE를_생성한다(UUID.randomUUID(), "대여 스냅샷");
        clock.set(NOW.plusSeconds(60));

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", rentalGenerationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].additionalCostStatus").value("ACTIVE_RENTAL"));

        소장한다();
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"소장 스냅샷\",\"maxAdditionalInk\":null,\"depth\":\"QUICK\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].additionalCostStatus").value("OWNED"));
    }

    @Test
    void ROUTE_조회_쿼리_수는_항목_수와_무관하다() throws Exception {
        String generationId = 새_ROUTE를_생성한다(UUID.randomUUID(), "저장 비용 조회");
        statistics.clear();

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        long oneItemQueryCount = statistics.getPrepareStatementCount();

        두_번째_페이지와_생성_항목을_추가한다(generationId);
        statistics.clear();
        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        long twoItemQueryCount = statistics.getPrepareStatementCount();

        assertEquals(
                oneItemQueryCount,
                twoItemQueryCount,
                "생성 결과 조회 쿼리는 항목 수가 늘어도 증가하지 않아야 합니다.");
    }

    @Test
    void 멱등_충돌_한도_미지원_공급자_오류를_공개_계약으로_변환한다() throws Exception {
        UUID key = UUID.randomUUID();
        새_ROUTE를_생성한다(key, "첫 목적");

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"다른 목적\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_IDEMPOTENCY_KEY_REUSED"));

        jdbcTemplate.update(
                "UPDATE ai_route_daily_usage SET generation_count = 10 WHERE reader_id = ?",
                READER_ID);
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"한도\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "43200"))
                .andExpect(jsonPath("$.code").value("AI_ROUTE_DAILY_LIMIT_EXCEEDED"));

        jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("UPDATE book SET ai_route_supported = FALSE WHERE id = ?", BOOK_ID);
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"미지원\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_NOT_SUPPORTED"));

        jdbcTemplate.update("UPDATE book SET ai_route_supported = TRUE WHERE id = ?", BOOK_ID);
        routeGateway.fail(OpenAiRouteException.Failure.BUDGET_LIMIT);
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"공급자 오류\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AI 경로 생성 한도를 확인할 수 없습니다."));
    }

    @Test
    void 공급자_일시_오류_잘못된_출력_시간_초과를_각각_503으로_변환한다() throws Exception {
        routeGateway.fail(OpenAiRouteException.Failure.TEMPORARY);
        생성_실패를_검증한다("일시 오류", "AI_ROUTE_PROVIDER_UNAVAILABLE");

        routeGateway.reset();
        routeGateway.fail(OpenAiRouteException.Failure.MALFORMED_RESPONSE);
        생성_실패를_검증한다("잘못된 출력", "AI_ROUTE_INVALID_OUTPUT");

        routeGateway.reset();
        routeGateway.failAfterDeadline(OpenAiRouteException.Failure.TIMEOUT_OR_INCOMPLETE);
        생성_실패를_검증한다("시간 초과", "AI_ROUTE_GENERATION_TIMEOUT");
    }

    @Test
    void 실패한_키의_재요청은_외부_호출_없이_최초_503을_재현한다() throws Exception {
        UUID key = UUID.randomUUID();
        String body = "{\"purpose\":\"실패 재생\",\"maxAdditionalInk\":1,\"depth\":null}";
        routeGateway.fail(OpenAiRouteException.Failure.TEMPORARY);

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_PROVIDER_UNAVAILABLE"));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_PROVIDER_UNAVAILABLE"));

        assertEquals(1, routeGateway.invocationCount());
    }

    @Test
    void 공급자_원문은_응답과_로그에_노출하지_않는다(CapturedOutput output) throws Exception {
        String providerSecret = "provider-project-secret-detail";
        routeGateway.failUnexpected(providerSecret);

        String response = mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"원문 은닉\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_PROVIDER_UNAVAILABLE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains(providerSecret));
        assertFalse(output.getAll().contains(providerSecret));
    }

    @Test
    void 만료와_다른_독자_조회는_같은_404이고_외부_호출과_DB를_바꾸지_않는다() throws Exception {
        String generationId = 새_ROUTE를_생성한다(UUID.randomUUID(), "소유자 은닉");
        int gatewayCalls = routeGateway.invocationCount();
        int generationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation WHERE reader_id = ?", Integer.class, READER_ID);

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(OTHER_READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        clock.set(NOW.plusSeconds(16 * 60));
        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertEquals(gatewayCalls, routeGateway.invocationCount());
        assertEquals(
                generationCount,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ai_route_generation WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
    }

    @Test
    void 입력_인증_CSRF_경계를_지킨다() throws Exception {
        String validBody = "{\"purpose\":\"목적\",\"maxAdditionalInk\":1,\"depth\":null}";

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"목적\",\"maxAdditionalInk\":1,\"depth\":\"QUICK\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId), null, "ROLE_USER");
    }

    private String 새_ROUTE를_생성한다(UUID key, String purpose) throws Exception {
        String body = "{\"purpose\":\"" + purpose + "\",\"maxAdditionalInk\":1,\"depth\":null}";
        return 생성하고_식별자를_반환한다(key, body);
    }

    private String 생성하고_식별자를_반환한다(UUID key, String body) throws Exception {
        String response = mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.replaceFirst(".*\"generationId\":\"([^\"]+)\".*", "$1");
    }

    private void 저장하고_삭제한다(String generationId) throws Exception {
        mockMvc.perform(post("/api/ai-route-generations/{generationId}/routes", generationId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated());
        long routeId = jdbcTemplate.queryForObject(
                "SELECT saved_route_id FROM ai_route_generation WHERE generation_id = ?",
                Long.class,
                generationId);
        mockMvc.perform(delete("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void 생성_실패를_검증한다(String purpose, String errorCode) throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"purpose\":\"%s\",\"maxAdditionalInk\":1,\"depth\":null}")
                                .formatted(purpose))
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    private void 두_번째_페이지와_생성_항목을_추가한다(String generationId) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_analysis_text, ai_public_guide_topic, estimated_reading_seconds,
                     embedding_model, embedding_dimensions, embedding_json,
                     duplicate_group_keys, ai_route_candidate)
                VALUES (?, ?, 2, 'TEXT', '두 번째 본문', '두 번째 분석', '두 번째 흐름', 60,
                        'embedding-v1', 2, '[0.9, 0.1]', JSON_ARRAY(), TRUE)
                """,
                SECOND_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_generation_item
                    (generation_id, book_id, book_page_id, position, relevance, prerequisite,
                     role, additional_cost_status)
                VALUES (?, ?, ?, 2, 'MEDIUM', FALSE, 'EXAMPLE', 'ONE_INK')
                """,
                generationId,
                BOOK_ID,
                SECOND_PAGE_ID);
    }

    private void GENERATING을_생성한다(UUID generationId, UUID idempotencyKey) {
        String requestFingerprint = AiRouteRequestFingerprint.of(
                AiRouteGenerationCommand.forInkBudget(
                        BOOK_ID, CONTENT_VERSION, "진행 중 목적", 1, 5));
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_generation
                    (generation_id, reader_id, book_id, content_version, idempotency_key,
                     request_fingerprint, normalized_purpose, request_type, max_additional_ink,
                     status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, '진행 중 목적', 'INK_BUDGET', 1,
                        'GENERATING', ?)
                """,
                generationId.toString(),
                READER_ID,
                BOOK_ID,
                CONTENT_VERSION,
                idempotencyKey.toString(),
                requestFingerprint,
                NOW);
    }

    private void 소장한다() {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status,
                     amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 10000, ?, ?)
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                BOOK_ID,
                UUID.randomUUID().toString(),
                "2026-08-13 12:01:00.000000",
                "2026-08-13 12:01:00.000000");
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                BOOK_ID,
                OWNERSHIP_PAYMENT_ID,
                "2026-08-13 12:01:00.000000");
    }

    private void 독자와_잉크를_생성한다(long readerId, String email) {
        jdbcTemplate.update(
                "INSERT INTO reader (id, email, password_hash, created_at) VALUES (?, ?, 'hash', ?)",
                readerId,
                email,
                NOW);
        jdbcTemplate.update("INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)", readerId);
    }

    private void 지원_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won,
                     content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, '인문', 'SCRUM-470 테스트 도서', '테스트 저자', 1, 10000,
                        ?, TRUE, TRUE, 'policy-v1')
                """,
                BOOK_ID,
                CONTENT_VERSION);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_analysis_text, ai_public_guide_topic, estimated_reading_seconds,
                     embedding_model, embedding_dimensions, embedding_json,
                     duplicate_group_keys, ai_route_candidate)
                VALUES (?, ?, 1, 'TEXT', '테스트 본문', '분석 텍스트', '핵심 흐름', 61,
                        'embedding-v1', 2, '[1.0, 0.0]', JSON_ARRAY(), TRUE)
                """,
                PAGE_ID,
                BOOK_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM ai_route_generation_item WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ai_route_current WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ai_reading_route_item WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ai_reading_route WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id IN (?, ?)", READER_ID, OTHER_READER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }

        @Bean
        @Primary
        FakeEmbeddingGateway fakeEmbeddingGateway() {
            return new FakeEmbeddingGateway();
        }

        @Bean
        @Primary
        FakeRouteGateway fakeRouteGateway(MutableClock clock) {
            return new FakeRouteGateway(clock);
        }
    }

    static final class FakeEmbeddingGateway implements OpenAiEmbeddingGateway {

        private volatile List<Double> vector = List.of(1.0, 0.0);

        void reset() {
            vector = List.of(1.0, 0.0);
        }

        void vector(double first, double second) {
            vector = List.of(first, second);
        }

        @Override
        public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
            return new Embedding(vector, model, dimensions);
        }

        @Override
        public Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeRouteGateway implements OpenAiRouteGateway {

        private final MutableClock clock;
        private volatile OpenAiRouteException.Failure failure;
        private volatile RuntimeException unexpectedFailure;
        private volatile boolean expireBeforeFailure;
        private volatile int invocationCount;

        FakeRouteGateway(MutableClock clock) {
            this.clock = clock;
        }

        void reset() {
            failure = null;
            unexpectedFailure = null;
            expireBeforeFailure = false;
            invocationCount = 0;
        }

        void fail(OpenAiRouteException.Failure failure) {
            this.failure = failure;
        }

        void failAfterDeadline(OpenAiRouteException.Failure failure) {
            this.failure = failure;
            expireBeforeFailure = true;
        }

        void failUnexpected(String message) {
            unexpectedFailure = new IllegalStateException(message);
        }

        int invocationCount() {
            return invocationCount;
        }

        @Override
        public RouteContract routeContract() {
            return new RouteContract("gpt-test", "prompt-v1", "schema-v1");
        }

        @Override
        public RouteGatewayResult proposeRoute(RouteInput input) {
            invocationCount++;
            if (expireBeforeFailure) {
                clock.set(NOW.plusSeconds(21));
            }
            if (unexpectedFailure != null) {
                throw unexpectedFailure;
            }
            if (failure != null) {
                throw new OpenAiRouteException(failure);
            }
            return new RouteGatewayResult(
                    new ModelRouteProposal(List.of(new ModelRouteItem(
                            1, Relevance.HIGH, Role.CORE))),
                    "prompt-v1",
                    "schema-v1");
        }
    }

    static final class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
