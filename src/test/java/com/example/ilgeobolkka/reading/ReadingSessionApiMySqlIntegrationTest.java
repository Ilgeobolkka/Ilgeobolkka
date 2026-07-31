package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ReadingSessionApiMySqlIntegrationTest {

    private static final long READER_ID = 411_002L;
    private static final long BOOK_ID = 411_103L;
    private static final long TEXT_PAGE_ID = 411_204L;
    private static final long IMAGE_PAGE_ID = 411_205L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingSessionApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다(10);
        도서를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void POST는_새_세션을_발급하고_1잉크를_차감한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.viewerSessionId").isString())
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.owned").value(false))
                .andExpect(jsonPath("$.deductedInk").value(1))
                .andExpect(jsonPath("$.inkBalance").value(9))
                .andExpect(jsonPath("$.contentType").value("TEXT"));
    }

    @Test
    void POST는_0_이하_페이지_번호를_거부한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void POST는_존재하지_않는_페이지를_404로_거부한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void POST는_잉크가_부족하면_422로_거부한다() throws Exception {
        jdbcTemplate.update(
                "UPDATE ink_account SET balance = 0 WHERE reader_id = ?", READER_ID);

        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INK"));
    }

    @Test
    void 인증하지_않은_POST는_401이다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void PATCH는_현재_세션의_같은_책_안에서_페이지만_이동한다() throws Exception {
        String viewerSessionId = 새_세션을_연다(1);

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", viewerSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerSessionId").value(viewerSessionId))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.contentType").value("IMAGE"));
    }

    @Test
    void PATCH는_뷰어_세션_헤더가_없으면_400이다() throws Exception {
        새_세션을_연다(1);

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void PATCH는_뷰어_세션_헤더가_UUID_형식이_아니면_400이다() throws Exception {
        새_세션을_연다(1);

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void PATCH는_현재_세션이_없으면_404다() throws Exception {
        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void T_VIEW_002_다른_탭에서_새_세션을_열면_이전_뷰어의_PATCH는_409다() throws Exception {
        String firstViewerSessionId = 새_세션을_연다(1);
        새_세션을_연다(1);

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", firstViewerSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIEWER_SESSION_REPLACED"));
    }

    private String 새_세션을_연다(int pageNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(pageNumber)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"viewerSessionId\":\"([^\"]+)\".*", "$1");
    }

    private String pageNumberJson(int pageNumber) {
        return "{\"pageNumber\":" + pageNumber + "}";
    }

    private TestingAuthenticationToken 인증된_독자() {
        return new TestingAuthenticationToken(new AuthenticatedReader(READER_ID), null, "ROLE_USER");
    }

    private void 독자와_잉크_계좌를_생성한다(int balance) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum411-api@example.com', '{noop}password',
                        '2026-07-30 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)", READER_ID, balance);
    }

    private void 도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-411 API 도서', '읽어볼까', 2, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지')
                """,
                TEXT_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, image_path)
                VALUES (?, ?, 2, 'IMAGE', '/covers/scrum-411-api/2.jpg')
                """,
                IMAGE_PAGE_ID,
                BOOK_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }
}
