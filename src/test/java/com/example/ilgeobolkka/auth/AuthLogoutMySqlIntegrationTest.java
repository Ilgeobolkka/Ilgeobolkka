package com.example.ilgeobolkka.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.service.ReaderService;
import com.example.ilgeobolkka.reading.repository.ReadingSessionRepository;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class AuthLogoutMySqlIntegrationTest {

    private static final String EMAIL = "logout-reader@example.com";
    private static final String RAW_PASSWORD = "Valid-password1!";

    private final MockMvc mockMvc;
    private final ReaderService readerService;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ReadingSessionRepository readingSessionRepository;

    @Autowired
    AuthLogoutMySqlIntegrationTest(
            MockMvc mockMvc,
            ReaderService readerService,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.readerService = readerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 로그아웃은_응답용_readerId를_보존하고_인증_세션_쿠키와_열람_세션을_무효화한다() throws Exception {
        Reader reader = readerService.createReader(EMAIL, RAW_PASSWORD);
        createReadingSession(reader.getId());
        MockHttpSession authenticatedSession = login();

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readerId").value(reader.getId()))
                .andReturn();

        assertTrue(authenticatedSession.isInvalid());
        assertTrue(logoutResult.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(cookie -> cookie.contains("JSESSIONID=")
                        && cookie.contains("Max-Age=0")
                        && cookie.contains("Path=/")));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reading_session WHERE reader_id = ?",
                        Integer.class,
                        reader.getId()));

        mockMvc.perform(get("/api/test/current-reader"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 현재_열람_세션이_없어도_로그아웃은_성공한다() throws Exception {
        Reader reader = readerService.createReader(EMAIL, RAW_PASSWORD);
        MockHttpSession authenticatedSession = login();

        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readerId").value(reader.getId()));

        assertTrue(authenticatedSession.isInvalid());
    }

    @Test
    void 열람_세션_삭제가_실패해도_로그인_세션을_무효화하고_새_익명_화면을_사용한다() throws Exception {
        Reader reader = readerService.createReader(EMAIL, RAW_PASSWORD);
        MockHttpSession authenticatedSession = login();
        MvcResult authenticatedPage = mockMvc.perform(get("/books").session(authenticatedSession))
                .andExpect(status().isOk())
                .andReturn();
        CsrfToken csrfTokenBeforeLogout = csrfToken(authenticatedPage);
        doThrow(new DataAccessResourceFailureException("강제 저장소 오류"))
                .when(readingSessionRepository)
                .deleteByReaderId(reader.getId());

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .header(
                                csrfTokenBeforeLogout.getHeaderName(),
                                csrfTokenBeforeLogout.getToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andReturn();

        assertTrue(authenticatedSession.isInvalid());
        assertTrue(logoutResult.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(cookie -> cookie.contains("JSESSIONID=")
                        && cookie.contains("Max-Age=0")
                        && cookie.contains("Path=/")));
        assertAnonymousPageUsesFreshCsrfToken(csrfTokenBeforeLogout);
    }

    @Test
    void 로그아웃_뒤_새로_렌더링한_페이지는_새_CSRF_토큰을_사용한다() throws Exception {
        readerService.createReader(EMAIL, RAW_PASSWORD);
        MockHttpSession authenticatedSession = login();
        MvcResult authenticatedPage = mockMvc.perform(get("/books").session(authenticatedSession))
                .andExpect(status().isOk())
                .andReturn();
        CsrfToken csrfTokenBeforeLogout = csrfToken(authenticatedPage);

        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticatedSession)
                        .header(
                                csrfTokenBeforeLogout.getHeaderName(),
                                csrfTokenBeforeLogout.getToken()))
                .andExpect(status().isOk());

        assertAnonymousPageUsesFreshCsrfToken(csrfTokenBeforeLogout);
    }

    private void assertAnonymousPageUsesFreshCsrfToken(CsrfToken csrfTokenBeforeLogout)
            throws Exception {
        MvcResult anonymousPage = mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andReturn();
        CsrfToken csrfTokenAfterLogout = csrfToken(anonymousPage);
        String html = anonymousPage.getResponse().getContentAsString();

        assertNotEquals(csrfTokenBeforeLogout.getToken(), csrfTokenAfterLogout.getToken());
        assertNotNull(anonymousPage.getRequest().getSession(false));
        assertTrue(html.contains("href=\"/signup\""));
        assertTrue(html.contains("href=\"/login\""));
        assertFalse(html.contains("href=\"/ink\""));
        assertFalse(html.contains("data-logout-form"));
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession loginSession = new MockHttpSession();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(loginSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CsrfToken csrfToken(MvcResult result) {
        return (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
    }

    private void createReadingSession(long readerId) {
        String title = "logout-book-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (category, title, author, total_page_count, price_won)
                VALUES
                    ('테스트', ?, '테스트 저자', 1, 1000)
                """,
                title);
        long bookId = jdbcTemplate.queryForObject(
                "SELECT id FROM book WHERE title = ?",
                Long.class,
                title);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES
                    (?, 1, 'TEXT', '테스트 본문', NULL)
                """,
                bookId);
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (reader_id, book_id, current_page_number, viewer_session_id, updated_at)
                VALUES
                    (?, ?, 1, ?, ?)
                """,
                readerId,
                bookId,
                UUID.randomUUID().toString(),
                Timestamp.from(Instant.now()));
    }
}
