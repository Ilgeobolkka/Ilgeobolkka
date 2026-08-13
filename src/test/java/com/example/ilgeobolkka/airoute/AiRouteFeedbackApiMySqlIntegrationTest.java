package com.example.ilgeobolkka.airoute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.junit.jupiter.api.AfterEach;

@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum465-test",
            "openai.api-key=not-a-real-key-scrum465-test",
            "openai.data-policy-version=policy-test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteFeedbackApiMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRouteFeedbackApiMySqlIntegrationTest {

    private static final long READER_ID = 465_001L;
    private static final long OTHER_READER_ID = 465_002L;
    private static final long BOOK_ID = 465_001L;
    private static final long ROUTE_ID_BASE = 465_100L;
    private static final long PAGE_ID_BASE = 465_100L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private static final Instant STARTED_AT = Instant.parse("2026-08-13T00:00:00.123456Z");

    @Autowired
    AiRouteFeedbackApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate, Clock clock) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(OTHER_READER_ID);
        도서를_생성한다(BOOK_ID, "샘플 도서");
        페이지를_생성한다(PAGE_ID_BASE + 1, BOOK_ID, 1);
        if (clock instanceof MutableClock mutableClock) {
            mutableClock.set(STARTED_AT);
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ai_reading_route_item");
        jdbcTemplate.update("DELETE FROM ai_route_current");
        jdbcTemplate.update("DELETE FROM ai_reading_route");
        jdbcTemplate.update("DELETE FROM book_page");
        jdbcTemplate.update("DELETE FROM book");
        jdbcTemplate.update("DELETE FROM reader");
    }

    @Test
    void 완료_owner의_세_rating_생성과_변경과_response_UTC_시각() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기");

        // HELPFUL 생성
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.rating").value("HELPFUL"))
                .andExpect(jsonPath("$.feedbackAt").exists());

        Map<String, Object> stateAfterHelpful = jdbcTemplate.queryForMap(
                "SELECT feedback, feedback_at FROM ai_reading_route WHERE id = ?", routeId);
        assertThat(stateAfterHelpful.get("feedback")).isEqualTo("HELPFUL");
        assertThat(stateAfterHelpful.get("feedback_at").toString()).startsWith("2026-08-13T00:00:00");

        if (clock instanceof MutableClock mutableClock) {
            mutableClock.set(STARTED_AT.plusSeconds(60));
        }

        // NEUTRAL 변경
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"NEUTRAL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.rating").value("NEUTRAL"));

        
        
        Map<String, Object> stateAfterNeutral = jdbcTemplate.queryForMap(
                "SELECT feedback, feedback_at FROM ai_reading_route WHERE id = ?", routeId);
        assertThat(stateAfterNeutral.get("feedback")).isEqualTo("NEUTRAL");
        assertThat(stateAfterNeutral.get("feedback_at").toString()).startsWith("2026-08-13T00:01:00");

        if (clock instanceof MutableClock mutableClock) {
            mutableClock.set(STARTED_AT.plusSeconds(120));
        }

        // NOT_HELPFUL 변경
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"NOT_HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.rating").value("NOT_HELPFUL"));

        Map<String, Object> stateAfterNotHelpful = jdbcTemplate.queryForMap(
                "SELECT feedback, feedback_at FROM ai_reading_route WHERE id = ?", routeId);
        assertThat(stateAfterNotHelpful.get("feedback")).isEqualTo("NOT_HELPFUL");
        assertThat(stateAfterNotHelpful.get("feedback_at").toString()).startsWith("2026-08-13T00:02:00");
    }

    @Test
    void 미완료_route_다른_독자_삭제_route_unknown_rating_거부() throws Exception {
        long incompleteRouteId = ROUTE_ID_BASE + 1;
        경로를_생성한다(READER_ID, incompleteRouteId, BOOK_ID, "재무제표 읽기", "2026-08-01 09:00:00.123456");

        // 미완료 route
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", incompleteRouteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ROUTE_NOT_COMPLETED"));

        long routeId = ROUTE_ID_BASE + 2;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기 2");

        // 다른 독자
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(OTHER_READER_ID)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 삭제 route (없는 경로)
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", ROUTE_ID_BASE + 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // unknown rating 거부
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"UNKNOWN\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // null rating 거부
        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":null}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 같은_rating_순차_요청_결과_일관성() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기");

        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void CSRF_누락_403() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기");

        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isForbidden());
    }

    @Test
    void feedback_변경_전후_completedAt_current_items_불변() throws Exception {
        long routeId = ROUTE_ID_BASE + 1;
        완료한_경로를_생성한다(READER_ID, routeId, "재무제표 읽기");
        
        // current 설정
        jdbcTemplate.update(
                "INSERT INTO ai_route_current (reader_id, book_id, route_id, updated_at) VALUES (?, ?, ?, '2026-08-03 09:00:00.123456')",
                READER_ID, BOOK_ID, routeId);

        // items 추가
        jdbcTemplate.update(
                "INSERT INTO ai_reading_route_item (id, route_id, book_id, book_page_id, position, relevance, prerequisite, role) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ROUTE_ID_BASE + 10, routeId, BOOK_ID, PAGE_ID_BASE + 1, 1, "HIGH", false, "CORE");

        Map<String, Object> before = 상태_스냅샷(routeId);

        mockMvc.perform(put("/api/ai-routes/{routeId}/feedback", routeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"HELPFUL\"}")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isOk());

        
        
        Map<String, Object> after = 상태_스냅샷(routeId);
        
        // feedback 과 feedbackAt 이 바뀌었으므로 이 항목을 제외하고 비교
        assertThat(after.get("completed_at")).isEqualTo(before.get("completed_at"));
        assertThat(after.get("currentRouteId")).isEqualTo(before.get("currentRouteId"));
        assertThat(after.get("itemsCount")).isEqualTo(before.get("itemsCount"));
    }

    private Map<String, Object> 상태_스냅샷(long routeId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT completed_at, 
                    (SELECT route_id FROM ai_route_current WHERE route_id = ?) as currentRouteId,
                    (SELECT COUNT(*) FROM ai_reading_route_item WHERE route_id = ?) as itemsCount
                FROM ai_reading_route
                WHERE id = ?
                """,
                routeId, routeId, routeId);
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                readerId,
                "scrum465-" + readerId + "@example.com");
    }

    private void 도서를_생성한다(long bookId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '경제', ?, '샘플 저자', 100, 10000)
                """,
                bookId,
                title);
    }

    private void 페이지를_생성한다(long pageId, long bookId, int pageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path, ai_public_guide_topic, estimated_reading_seconds)
                VALUES (?, ?, ?, 'TEXT', '샘플 본문', NULL, NULL, NULL)
                """,
                pageId,
                bookId,
                pageNumber);
    }

    private void 경로를_생성한다(
            long readerId, long routeId, long bookId, String purpose, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version, normalized_purpose,
                     request_type, max_additional_ink, depth, completed_at, feedback, feedback_at,
                     created_at)
                VALUES (?, ?, ?, ?, 'ai-route-v2', ?, 'INK_BUDGET', 3, NULL, NULL, NULL, NULL, ?)
                """,
                routeId,
                new UUID(0L, routeId).toString(),
                readerId,
                bookId,
                purpose,
                createdAt);
    }

    private void 완료한_경로를_생성한다(long readerId, long routeId, String purpose) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version, normalized_purpose,
                     request_type, max_additional_ink, depth, completed_at, feedback, feedback_at,
                     created_at)
                VALUES (?, ?, ?, ?, 'ai-route-v2', ?, 'INK_BUDGET', 3, NULL,
                        '2026-08-03 11:00:00.123456', NULL, NULL,
                        '2026-08-01 09:00:00.123456')
                """,
                routeId,
                new UUID(0L, routeId).toString(),
                readerId,
                BOOK_ID,
                purpose);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }

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
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }
    }
}
