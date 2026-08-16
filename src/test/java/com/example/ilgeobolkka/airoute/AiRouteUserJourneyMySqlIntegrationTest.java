package com.example.ilgeobolkka.airoute;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** SCRUM-478 AI 경로의 생성부터 삭제까지 실제 MySQL과 HTTP 경계를 한 사용자 여정으로 검증한다. */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum478-test",
            "openai.api-key=not-a-real-key-scrum478-test",
            "openai.data-policy-version=policy-test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteUserJourneyMySqlIntegrationTest.TestBeans.class)
class AiRouteUserJourneyMySqlIntegrationTest {

    private static final long READER_ID = 478_001L;
    private static final long OTHER_READER_ID = 478_002L;
    private static final long SUPPORTED_BOOK_ID = 478_101L;
    private static final long UNSUPPORTED_BOOK_ID = 478_102L;
    private static final long SUPPORTED_PAGE_ID = 478_201L;
    private static final long UNSUPPORTED_PAGE_ID = 478_202L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FakeEmbeddingGateway embeddingGateway;
    private final FakeRouteGateway routeGateway;

    @Autowired
    AiRouteUserJourneyMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            FakeEmbeddingGateway embeddingGateway,
            FakeRouteGateway routeGateway) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingGateway = embeddingGateway;
        this.routeGateway = routeGateway;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        embeddingGateway.reset();
        routeGateway.reset();
        독자를_생성한다(READER_ID, "scrum-478@example.com");
        독자를_생성한다(OTHER_READER_ID, "scrum-478-other@example.com");
        도서와_페이지를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void 지원_도서의_생성부터_저장_읽기_완료_피드백_삭제까지_이어진다() throws Exception {
        mockMvc.perform(get("/api/books/{bookId}", SUPPORTED_BOOK_ID)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiRouteSupported").value(true));

        MvcResult generation = mockMvc.perform(post(
                                "/api/books/{bookId}/ai-route-generations", SUPPORTED_BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"핵심 개념\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ROUTE"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].pageNumber").value(1))
                .andReturn();
        String generationId = 응답(generation).get("generationId").asText();

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", generationId)
                        .with(authentication(인증된_독자(OTHER_READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        MvcResult saved = mockMvc.perform(post(
                                "/api/ai-route-generations/{generationId}/routes", generationId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.completedAt").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        long routeId = 응답(saved).get("routeId").asLong();

        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].bookId").value(SUPPORTED_BOOK_ID))
                .andExpect(jsonPath("$.entries[0].routes[0].routeId").value(routeId))
                .andExpect(jsonPath("$.entries[0].currentRouteId").value(routeId));

        mockMvc.perform(get("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(OTHER_READER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        MvcResult opened = mockMvc.perform(post(
                                "/api/books/{bookId}/reading-sessions", SUPPORTED_BOOK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deductedInk").value(1))
                .andExpect(jsonPath("$.inkBalance").value(4))
                .andReturn();
        String viewerSessionId = 응답(opened).get("viewerSessionId").asText();

        mockMvc.perform(post(
                                "/api/ai-routes/{routeId}/pages/{pageNumber}/content", routeId, 1)
                        .header("X-Viewer-Session-Id", viewerSessionId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(content().string("통합 여정 본문"));

        mockMvc.perform(get("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.rating").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value("HELPFUL"));

        mockMvc.perform(delete("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ai-routes/{routeId}", routeId)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].lastPageNumber").value(1))
                .andExpect(jsonPath("$.entries[0].activeRental").value(true))
                .andExpect(jsonPath("$.entries[0].routes.length()").value(0))
                .andExpect(jsonPath("$.entries[0].currentRouteId").doesNotExist());

        assertAll(
                () -> assertEquals(4, 정수("SELECT balance FROM ink_account WHERE reader_id = ?", READER_ID)),
                () -> assertEquals(1, 개수("ink_ledger", READER_ID)),
                () -> assertEquals(1, 개수("page_rental", READER_ID)),
                () -> assertEquals(1, 개수("library_entry", READER_ID)),
                () -> assertEquals(0, 개수("ai_reading_route", READER_ID)),
                () -> assertEquals(0, 개수("ai_route_current", READER_ID)),
                () -> assertEquals(
                        "CONSUMED",
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM ai_route_generation WHERE generation_id = ?",
                                String.class,
                                generationId)),
                () -> assertEquals(1, embeddingGateway.invocationCount()),
                () -> assertEquals(1, routeGateway.invocationCount()));
    }

    @Test
    void 미지원_도서는_생성과_외부_Gateway와_DB_사용량을_남기지_않는다() throws Exception {
        mockMvc.perform(get("/api/books/{bookId}", UNSUPPORTED_BOOK_ID)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiRouteSupported").value(false));

        mockMvc.perform(post("/api/books/{bookId}/ai-route-generations", UNSUPPORTED_BOOK_ID)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"미지원 도서\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_NOT_SUPPORTED"));

        assertAll(
                () -> assertEquals(0, embeddingGateway.invocationCount()),
                () -> assertEquals(0, routeGateway.invocationCount()),
                () -> assertEquals(0, 개수("ai_route_generation", READER_ID)),
                () -> assertEquals(0, 개수("ai_route_daily_usage", READER_ID)));
    }

    private JsonNode 응답(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }

    private int 개수(String table, long readerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE reader_id = ?", Integer.class, readerId);
    }

    private int 정수(String sql, long readerId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, readerId);
    }

    private void 독자를_생성한다(long readerId, String email) {
        jdbcTemplate.update(
                "INSERT INTO reader (id, email, password_hash, created_at) VALUES (?, ?, 'hash', ?)",
                readerId,
                email,
                NOW);
        jdbcTemplate.update("INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)", readerId);
    }

    private void 도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won,
                     content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, '인문', 'SCRUM-478 지원 도서', '테스트 저자', 1, 10000,
                        ?, TRUE, TRUE, 'policy-test')
                """,
                SUPPORTED_BOOK_ID,
                CONTENT_VERSION);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_analysis_text, ai_public_guide_topic, estimated_reading_seconds,
                     embedding_model, embedding_dimensions, embedding_json,
                     duplicate_group_keys, ai_route_candidate)
                VALUES (?, ?, 1, 'TEXT', '통합 여정 본문', '통합 분석 텍스트', '통합 핵심 흐름', 60,
                        'embedding-v1', 2, '[1.0, 0.0]', JSON_ARRAY(), TRUE)
                """,
                SUPPORTED_PAGE_ID,
                SUPPORTED_BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won,
                     content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, '소설', 'SCRUM-478 미지원 도서', '테스트 저자', 1, 10000,
                        ?, FALSE, FALSE, NULL)
                """,
                UNSUPPORTED_BOOK_ID,
                CONTENT_VERSION);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, ai_route_candidate)
                VALUES (?, ?, 1, 'TEXT', '미지원 도서 본문', FALSE)
                """,
                UNSUPPORTED_PAGE_ID,
                UNSUPPORTED_BOOK_ID);
    }

    private void 테스트_데이터를_정리한다() {
        for (long readerId : List.of(READER_ID, OTHER_READER_ID)) {
            jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ai_route_current WHERE reader_id = ?", readerId);
            jdbcTemplate.update(
                    """
                    DELETE FROM ai_reading_route_item
                    WHERE route_id IN (SELECT id FROM ai_reading_route WHERE reader_id = ?)
                    """,
                    readerId);
            jdbcTemplate.update("DELETE FROM ai_reading_route WHERE reader_id = ?", readerId);
            jdbcTemplate.update(
                    """
                    DELETE FROM ai_route_generation_item
                    WHERE generation_id IN (
                        SELECT generation_id FROM ai_route_generation WHERE reader_id = ?
                    )
                    """,
                    readerId);
            jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", readerId);
        }
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id IN (?, ?)",
                SUPPORTED_BOOK_ID,
                UNSUPPORTED_BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM book WHERE id IN (?, ?)", SUPPORTED_BOOK_ID, UNSUPPORTED_BOOK_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id IN (?, ?)", READER_ID, OTHER_READER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        FakeEmbeddingGateway fakeEmbeddingGateway() {
            return new FakeEmbeddingGateway();
        }

        @Bean
        @Primary
        FakeRouteGateway fakeRouteGateway() {
            return new FakeRouteGateway();
        }
    }

    static final class FakeEmbeddingGateway implements OpenAiEmbeddingGateway {

        private int invocationCount;

        void reset() {
            invocationCount = 0;
        }

        int invocationCount() {
            return invocationCount;
        }

        @Override
        public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
            invocationCount++;
            return new Embedding(List.of(1.0, 0.0), model, dimensions);
        }

        @Override
        public Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeRouteGateway implements OpenAiRouteGateway {

        private int invocationCount;

        void reset() {
            invocationCount = 0;
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
            return new RouteGatewayResult(
                    new ModelRouteProposal(List.of(
                            new ModelRouteItem(1, Relevance.HIGH, false, Role.CORE))),
                    "prompt-v1",
                    "schema-v1");
        }
    }
}
