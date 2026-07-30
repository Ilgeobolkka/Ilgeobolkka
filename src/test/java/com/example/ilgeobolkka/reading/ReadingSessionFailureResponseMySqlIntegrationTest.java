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
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-411 5/5(SCRUM-436): docs/api-spec.md 415~417행이 규정한 페이지 열기 오류 계약 중
 * {@link ReadingSessionApiMySqlIntegrationTest}가 아직 다루지 않는 실패 케이스를 HTTP 레벨로
 * 증명한다. 0 이하 페이지(400)·없는 페이지(404)·헤더 누락(400)은 이미 그 클래스가 증명하므로
 * 여기서 중복 작성하지 않는다.
 *
 * <p>모든 테스트가 상태 코드와 {@code code} 필드만 확인한다. 잉크 차감·대여·서재 갱신이 실제로
 * 커밋되지 않았다는 부수효과 증명은 {@code ReadingRentalInsufficientInkMySqlIntegrationTest}(커밋형)가
 * 이미 SCRUM-435에서 수행했으므로 이 클래스는 이중 증명을 생략하고 {@code @Transactional} 롤백형으로
 * 작성한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class ReadingSessionFailureResponseMySqlIntegrationTest {

    private static final long READER_ID = 436_001L;
    private static final long BOOK_ID = 436_001L;
    private static final long NO_INK_READER_ID = 436_002L;
    private static final long NO_SESSION_READER_ID = 436_003L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingSessionFailureResponseMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자와_잉크_계좌를_생성한다(READER_ID);
        독자와_잉크_계좌를_생성한다(NO_INK_READER_ID);
        독자와_잉크_계좌를_생성한다(NO_SESSION_READER_ID);
        소장하지_않은_도서와_페이지를_생성한다();
    }

    @Test
    void X_Viewer_Session_Id_헤더가_UUID_형식이_아니면_400_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 존재하지_않는_도서로_새_뷰어_세션을_열면_404_RESOURCE_NOT_FOUND를_반환한다() throws Exception {
        long missingBookId = 999_999_436L;

        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", missingBookId)
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 세션을_연_적_없는_독자가_페이지를_이동하면_404_RESOURCE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자(NO_SESSION_READER_ID)))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 잔액이_0인_독자가_새_페이지를_열면_422_INSUFFICIENT_INK를_반환한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자(NO_INK_READER_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INK"));
    }

    private void 독자와_잉크_계좌를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                readerId,
                "scrum436-" + readerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)", readerId);
    }

    private void 소장하지_않은_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 436', 'Author 436', 'Description 436',
                        '/assets/covers/demo/category-01.svg', 1, 9001)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (book_id, page_number, content_type, text_content)
                VALUES (?, 1, 'TEXT', 'Page 1 content')
                """,
                BOOK_ID);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }
}
