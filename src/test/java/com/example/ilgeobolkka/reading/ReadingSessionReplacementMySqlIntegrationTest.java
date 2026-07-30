package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.Map;
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
import tools.jackson.databind.ObjectMapper;

/**
 * SCRUM-411 1/5(SCRUM-432)에서 구현한 뷰어 세션 발급·교체·이동 로직이 실제 커밋된 MySQL 상태로
 * 다음 세 가지를 지키는지 증명한다. {@code @Transactional}을 붙이지 않아 각 호출이 실제로
 * 커밋되며, 이 클래스는 {@link ReadingSessionApiMySqlIntegrationTest}(롤백형)와 별개로 존재한다.
 *
 * <ol>
 *   <li>같은 독자의 새 뷰어 세션 발급은 기존 세션을 교체한다.</li>
 *   <li>페이지 열기가 실패하면 기존 세션은 한 글자도 바뀌지 않는다.</li>
 *   <li>독자당 {@code reading_session} 행은 항상 1개만 유지된다
 *       ({@code ReadingSessionService.openNewSession}의 delete-then-insert 순서 회귀 증명).</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ReadingSessionReplacementMySqlIntegrationTest {

    private static final long READER_ID = 433_001L;
    private static final long BOOK_ID = 433_001L;
    private static final long OWNERSHIP_PAYMENT_ID = 433_001L;
    private static final long BOOK_OWNERSHIP_ID = 433_001L;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingSessionReplacementMySqlIntegrationTest(
            MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        소장한_도서와_페이지_2개를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    /**
     * docs/api-spec.md 414~417행의 계약대로, 무효화된 뷰어 세션으로의 이동은 {@code 409
     * VIEWER_SESSION_REPLACED}로 거부되어야 한다. {@code ReadingSessionService.findCurrentSession}이
     * 세션 레코드는 있지만 {@code viewerSessionId}가 다른 경우를 {@link
     * com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException}으로 구분해 던지고,
     * {@code GlobalExceptionHandler}가 이를 409로 매핑한다(SCRUM-436, 5/5).
     */
    @Test
    void 같은_독자가_새_뷰어_세션을_열면_기존_세션을_교체하고_이전_세션_ID는_무효화된다() throws Exception {
        String firstViewerSessionId = 새_뷰어_세션을_연다(1);
        String secondViewerSessionId = 새_뷰어_세션을_연다(1);

        assertNotEquals(firstViewerSessionId, secondViewerSessionId);
        assertEquals(1, 독자의_세션_행_수를_조회한다());
        assertEquals(secondViewerSessionId, 독자의_현재_뷰어_세션_ID를_조회한다());

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .header("X-Viewer-Session-Id", firstViewerSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIEWER_SESSION_REPLACED"));

        assertEquals(1, 독자의_세션_행_수를_조회한다());
        assertEquals(secondViewerSessionId, 독자의_현재_뷰어_세션_ID를_조회한다());
    }

    @Test
    void 존재하지_않는_페이지를_열려는_요청이_실패해도_기존_세션은_바뀌지_않는다() throws Exception {
        새_뷰어_세션을_연다(1);
        Map<String, Object> beforeSnapshot = 독자의_세션_스냅샷을_조회한다();

        mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        Map<String, Object> afterSnapshot = 독자의_세션_스냅샷을_조회한다();
        assertEquals(beforeSnapshot, afterSnapshot);
    }

    /**
     * {@code ReadingSessionService.openNewSession}의 {@code deleteByReaderId} 호출을 제거하면
     * (일시적으로 주석 처리하면) 두 번째 이후 POST가 {@code uk_reading_session_reader} 위반으로
     * 실패해 이 테스트가 실패한다. 연속된 각 POST는 독립된 HTTP 요청·트랜잭션으로 커밋되므로
     * 이 회귀를 실제 커밋 DB로 증명한다.
     */
    @Test
    void 같은_독자로_연속_요청해도_reading_session_행은_항상_하나만_유지된다() throws Exception {
        새_뷰어_세션을_연다(1);
        assertEquals(1, 독자의_세션_행_수를_조회한다());

        새_뷰어_세션을_연다(2);
        assertEquals(1, 독자의_세션_행_수를_조회한다());

        새_뷰어_세션을_연다(1);
        assertEquals(1, 독자의_세션_행_수를_조회한다());
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

    private int 독자의_세션_행_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reading_session WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private String 독자의_현재_뷰어_세션_ID를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT viewer_session_id FROM reading_session WHERE reader_id = ?",
                String.class,
                READER_ID);
    }

    private Map<String, Object> 독자의_세션_스냅샷을_조회한다() {
        return jdbcTemplate.queryForMap(
                """
                SELECT viewer_session_id, book_id, current_page_number,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated_at
                FROM reading_session
                WHERE reader_id = ?
                """,
                READER_ID);
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                READER_ID,
                "scrum433-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)", READER_ID);
    }

    private void 소장한_도서와_페이지_2개를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 433', 'Author 433', 'Description 433',
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
                UUID.fromString("00000000-0000-0000-0000-000000433001").toString(),
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

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }
}
