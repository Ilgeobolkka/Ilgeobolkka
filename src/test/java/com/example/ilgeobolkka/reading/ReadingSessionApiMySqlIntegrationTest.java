package com.example.ilgeobolkka.reading;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class ReadingSessionApiMySqlIntegrationTest {

    private static final long READER_ID = 432_001L;
    private static final long BOOK_ID = 432_001L;
    private static final long OWNERSHIP_PAYMENT_ID = 432_001L;
    private static final long BOOK_OWNERSHIP_ID = 432_001L;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingSessionApiMySqlIntegrationTest(
            MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자와_잉크_계좌를_생성한다();
        소장한_도서와_페이지_2개를_생성한다();
    }

    @Test
    void T_OWN_007_소장한_도서의_새_뷰어_세션을_열면_201과_계약된_필드를_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.viewerSessionId").isNotEmpty())
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.deductedInk").value(0))
                .andExpect(jsonPath("$.inkBalance").value(0))
                .andExpect(jsonPath("$.rentedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.expiresAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.contentType").value("TEXT"));
    }

    /**
     * docs/test-strategy.md의 T-* 시나리오에 대응하지 않는다. PATCH 계약(같은 뷰어 세션 유지,
     * 현재 페이지 이동)을 확인하는 테스트여서 T-OWN 번호를 붙이지 않는다.
     */
    @Test
    void 기존_뷰어_세션에서_페이지를_이동하면_200과_이동한_페이지_콘텐츠타입을_반환한다() throws Exception {
        String viewerSessionId = 새_뷰어_세션을_연다(1);

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", viewerSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerSessionId").value(viewerSessionId))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.deductedInk").value(0))
                .andExpect(jsonPath("$.contentType").value("IMAGE"));
    }

    @Test
    void 페이지_번호가_0_이하이면_400_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 존재하지_않는_페이지를_열면_404_RESOURCE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void X_Viewer_Session_Id_헤더가_없으면_400_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 인증되지_않은_새_뷰어_세션_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isUnauthorized());
    }

    private String 새_뷰어_세션을_연다(int pageNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":" + pageNumber + "}"))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("viewerSessionId")
                .asText();
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                READER_ID,
                "scrum432-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)", READER_ID);
    }

    private void 소장한_도서와_페이지_2개를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 001', 'Author 001', 'Description 001',
                        '/assets/covers/demo/category-01.svg', 2, 9001)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (book_id, page_number, content_type, text_content)
                VALUES (?, 1, 'TEXT', 'Page 1 content')
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (book_id, page_number, content_type, image_path)
                VALUES (?, 2, 'IMAGE', '/private/books/demo/pages/2.jpg')
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 9001, ?, ?)
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                BOOK_ID,
                UUID.fromString("00000000-0000-0000-0000-000000432001").toString(),
                "2026-07-28 00:00:00.000000",
                "2026-07-28 00:00:00.000000");
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                BOOK_OWNERSHIP_ID,
                READER_ID,
                BOOK_ID,
                OWNERSHIP_PAYMENT_ID,
                "2026-07-28 00:00:00.000000");
    }

    private TestingAuthenticationToken 인증된_독자() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(READER_ID), null, "ROLE_USER");
    }
}
