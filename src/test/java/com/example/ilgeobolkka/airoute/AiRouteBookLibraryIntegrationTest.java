package com.example.ilgeobolkka.airoute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=proj-scrum477-test",
            "openai.api-key=not-a-real-key-scrum477-test",
            "openai.data-policy-version=policy-test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class AiRouteBookLibraryIntegrationTest {

    private static final long READER_ID = 477_001L;
    private static final long OTHER_READER_ID = 477_002L;
    private static final long SUPPORTED_BOOK_ID = 477_101L;
    private static final long SINGLE_ROUTE_BOOK_ID = 477_102L;
    private static final long MULTIPLE_ROUTE_BOOK_ID = 477_103L;
    private static final long ROUTE_ONLY_BOOK_ID = 477_104L;
    private static final long SINGLE_ROUTE_ID = 477_201L;
    private static final long OLDER_ROUTE_ID = 477_202L;
    private static final long CURRENT_ROUTE_ID = 477_203L;
    private static final long ROUTE_ONLY_ROUTE_ID = 477_204L;
    private static final long OTHER_READER_ROUTE_ID = 477_299L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AiRouteBookLibraryIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(OTHER_READER_ID);
        도서와_페이지를_생성한다(SUPPORTED_BOOK_ID, "지원 소설", "소설", true);
        도서와_페이지를_생성한다(SINGLE_ROUTE_BOOK_ID, "미지원 경제", "경제", false);
        도서와_페이지를_생성한다(MULTIPLE_ROUTE_BOOK_ID, "경로가 많은 책", "과학", true);
        도서와_페이지를_생성한다(ROUTE_ONLY_BOOK_ID, "경로만 저장한 책", "인문", true);

        서재_항목과_대여를_생성한다(
                READER_ID, SUPPORTED_BOOK_ID, "2026-08-15 12:00:00.000000");
        서재_항목과_대여를_생성한다(
                READER_ID, SINGLE_ROUTE_BOOK_ID, "2026-08-15 11:00:00.000000");
        서재_항목과_대여를_생성한다(
                READER_ID, MULTIPLE_ROUTE_BOOK_ID, "2026-08-15 10:00:00.000000");

        경로를_생성한다(
                READER_ID,
                SINGLE_ROUTE_ID,
                SINGLE_ROUTE_BOOK_ID,
                "<img src=x onerror=alert('single')>",
                "2026-08-15 09:00:00.000000");
        경로를_생성한다(
                READER_ID,
                OLDER_ROUTE_ID,
                MULTIPLE_ROUTE_BOOK_ID,
                "먼저 만든 경로",
                "2026-08-15 08:00:00.000000");
        경로를_생성한다(
                READER_ID,
                CURRENT_ROUTE_ID,
                MULTIPLE_ROUTE_BOOK_ID,
                "현재 읽는 경로",
                "2026-08-15 09:00:00.000000");
        경로를_생성한다(
                READER_ID,
                ROUTE_ONLY_ROUTE_ID,
                ROUTE_ONLY_BOOK_ID,
                "서재 진입을 검증할 경로",
                "2026-08-15 13:00:00.000000");
        경로를_생성한다(
                OTHER_READER_ID,
                OTHER_READER_ROUTE_ID,
                MULTIPLE_ROUTE_BOOK_ID,
                "다른 독자의 비밀 경로",
                "2026-08-15 10:00:00.000000");
        현재_경로로_지정한다(READER_ID, MULTIPLE_ROUTE_BOOK_ID, CURRENT_ROUTE_ID);
        현재_경로로_지정한다(READER_ID, ROUTE_ONLY_BOOK_ID, ROUTE_ONLY_ROUTE_ID);
    }

    @Test
    void 도서_상세는_DB_지원값과_로그인_상태에_맞는_AI_진입점_계약을_제공한다()
            throws Exception {
        mockMvc.perform(get("/api/books/{bookId}", SUPPORTED_BOOK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("소설"))
                .andExpect(jsonPath("$.aiRouteSupported").value(true))
                .andExpect(jsonPath("$.owned").value(nullValue()));

        mockMvc.perform(get("/api/books/{bookId}", SINGLE_ROUTE_BOOK_ID)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("경제"))
                .andExpect(jsonPath("$.aiRouteSupported").value(false))
                .andExpect(jsonPath("$.owned").value(false));

        MvcResult anonymousPage = mockMvc.perform(get("/books/{bookId}", SUPPORTED_BOOK_ID))
                .andExpect(status().isOk())
                .andReturn();
        String anonymousHtml = anonymousPage.getResponse().getContentAsString();
        assertThat(anonymousHtml)
                .contains("data-authenticated=\"false\"")
                .contains("data-ai-route-entry")
                .contains("data-ai-route-link")
                .contains("AI 독서 경로");

        mockMvc.perform(get("/books/{bookId}", SUPPORTED_BOOK_ID)
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-authenticated=\"true\"")))
                .andExpect(content().string(containsString("data-ai-route-entry")));

        mockMvc.perform(get("/js/ownership/book-detail-page.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/books/${book.bookId}/ai-route")))
                .andExpect(content().string(containsString("/login?returnTo=")))
                .andExpect(content().string(not(containsString("/api/ai-route-generations"))));
    }

    @Test
    void 내_서재는_책을_중복하지_않고_소유자의_경로와_현재_경로만_연결한다()
            throws Exception {
        mockMvc.perform(get("/api/library")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].bookId").value(ROUTE_ONLY_BOOK_ID))
                .andExpect(jsonPath("$.entries[0].lastPageNumber").value(1))
                .andExpect(jsonPath("$.entries[0].rentedAt").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].expiresAt").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].activeRental").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].owned").value(false))
                .andExpect(jsonPath("$.entries[0].routes.length()").value(1))
                .andExpect(jsonPath("$.entries[0].routes[0].routeId")
                        .value(ROUTE_ONLY_ROUTE_ID))
                .andExpect(jsonPath("$.entries[0].currentRouteId")
                        .value(ROUTE_ONLY_ROUTE_ID))
                .andExpect(jsonPath("$.entries[1].bookId").value(SUPPORTED_BOOK_ID))
                .andExpect(jsonPath("$.entries[1].lastPageNumber").value(1))
                .andExpect(jsonPath("$.entries[1].routes.length()").value(0))
                .andExpect(jsonPath("$.entries[1].currentRouteId").doesNotExist())
                .andExpect(jsonPath("$.entries[2].bookId").value(SINGLE_ROUTE_BOOK_ID))
                .andExpect(jsonPath("$.entries[2].routes.length()").value(1))
                .andExpect(jsonPath("$.entries[2].routes[0].routeId").value(SINGLE_ROUTE_ID))
                .andExpect(jsonPath("$.entries[2].routes[0].purpose")
                        .value("<img src=x onerror=alert('single')>"))
                .andExpect(jsonPath("$.entries[2].currentRouteId").doesNotExist())
                .andExpect(jsonPath("$.entries[3].bookId").value(MULTIPLE_ROUTE_BOOK_ID))
                .andExpect(jsonPath("$.entries[3].routes.length()").value(2))
                .andExpect(jsonPath("$.entries[3].routes[0].routeId").value(CURRENT_ROUTE_ID))
                .andExpect(jsonPath("$.entries[3].routes[1].routeId").value(OLDER_ROUTE_ID))
                .andExpect(jsonPath("$.entries[3].currentRouteId").value(CURRENT_ROUTE_ID))
                .andExpect(content().string(not(containsString("다른 독자의 비밀 경로"))));

        mockMvc.perform(get("/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-library-ai-routes")))
                .andExpect(content().string(containsString("data-library-resume")));

        MvcResult libraryScript = mockMvc.perform(get("/js/library/library-page.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/ai-routes/${route.routeId}")))
                .andExpect(content().string(containsString("textContent = route.purpose")))
                .andReturn();
        assertThat(libraryScript.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("현재 경로");
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-08-15 00:00:00.000000')
                """,
                readerId,
                "scrum477-" + readerId + "@example.com");
    }

    private void 도서와_페이지를_생성한다(
            long bookId, String title, String category, boolean aiRouteSupported) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, total_page_count, price_won,
                     content_version, ai_route_supported, ai_external_transfer_allowed,
                     ai_data_policy_version)
                VALUES (?, ?, ?, '테스트 저자', '테스트 설명', 1, 10000,
                        'ai-route-v2', ?, TRUE, 'policy-test')
                """,
                bookId,
                category,
                title,
                aiRouteSupported);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '테스트 본문')
                """,
                페이지_ID(bookId),
                bookId);
    }

    private void 서재_항목과_대여를_생성한다(long readerId, long bookId, String updatedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (id, reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, ?, 1, ?)
                """,
                bookId,
                readerId,
                bookId,
                updatedAt);
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2026-08-01 00:00:00.000000', '2099-08-01 00:00:00.000000')
                """,
                bookId,
                readerId,
                페이지_ID(bookId));
    }

    private void 경로를_생성한다(
            long readerId, long routeId, long bookId, String purpose, String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version, normalized_purpose,
                     request_type, max_additional_ink, depth, completed_at, feedback, feedback_at,
                     created_at)
                VALUES (?, ?, ?, ?, 'ai-route-v2', ?, 'INK_BUDGET', 3,
                        NULL, NULL, NULL, NULL, ?)
                """,
                routeId,
                new UUID(0L, routeId).toString(),
                readerId,
                bookId,
                purpose,
                createdAt);
    }

    private void 현재_경로로_지정한다(long readerId, long bookId, long routeId) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_current (reader_id, book_id, route_id, updated_at)
                VALUES (?, ?, ?, '2026-08-15 10:00:00.000000')
                """,
                readerId,
                bookId,
                routeId);
    }

    private long 페이지_ID(long bookId) {
        return bookId * 10;
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId), null, "ROLE_USER");
    }
}
