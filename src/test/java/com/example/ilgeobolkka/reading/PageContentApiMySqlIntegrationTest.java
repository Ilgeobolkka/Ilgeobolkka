package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@SpringBootTest(properties = "content-storage.root=build/test-content/scrum-405-pages")
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class PageContentApiMySqlIntegrationTest {

    private static final long READER_ID = 405_002L;
    private static final long OTHER_READER_ID = 405_003L;
    private static final long BOOK_ID = 405_102L;
    private static final long TEXT_PAGE_ID = 405_201L;
    private static final long IMAGE_PAGE_ID = 405_202L;
    private static final long OWNERSHIP_PAYMENT_ID = 405_301L;
    private static final int BOOK_PRICE_WON = 10_000;
    private static final String TEXT_CONTENT = "첫 문단\n둘째 문단";
    private static final byte[] IMAGE_CONTENT = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00
    };
    private static final Path IMAGE_DIRECTORY =
            Path.of("build/test-content/scrum-405-pages/scrum-405-test/book-405102");
    private static final Path IMAGE_PATH = IMAGE_DIRECTORY.resolve("page-002.jpg");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PageContentApiMySqlIntegrationTest(
            MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() throws IOException {
        테스트_데이터를_정리한다();
        이미지_파일을_생성한다();
        독자와_잉크_계좌를_생성한다();
        도서와_페이지를_생성한다();
    }

    @AfterEach
    void tearDown() throws IOException {
        테스트_데이터를_정리한다();
        이미지_파일을_정리한다();
    }

    @Test
    void T_VIEW_001_TEXT는_현재_페이지_본문과_캐시_금지_헤더만_반환한다() throws Exception {
        String viewerSessionId = 새_세션을_연다(1);
        DomainState before = 도메인_상태를_조회한다();

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().bytes(TEXT_CONTENT.getBytes(StandardCharsets.UTF_8)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        assertEquals(before, 도메인_상태를_조회한다());
    }

    @Test
    void T_VIEW_001_IMAGE는_사용자_지정_콘텐츠_루트의_실제_JPEG_바이트를_반환한다() throws Exception {
        String viewerSessionId = 새_세션을_연다(2);

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 2)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(IMAGE_CONTENT))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
    }

    @Test
    void T_OWN_007_소장한_도서는_대여와_잉크_차감_없이_콘텐츠를_반환한다() throws Exception {
        소장_기록을_생성한다();
        String viewerSessionId = 새_세션을_연다(1);

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isOk())
                .andExpect(content().string(TEXT_CONTENT));

        assertEquals(0, 수를_조회한다("page_rental"));
        assertEquals(0, 수를_조회한다("ink_ledger"));
        assertEquals(10, 잉크_잔액을_조회한다());
    }

    @Test
    void T_OWN_008_기존_대여가_만료돼도_소장_권한으로_콘텐츠를_반환한다() throws Exception {
        String viewerSessionId = 새_세션을_연다(1);
        대여를_만료시킨다();
        소장_기록을_생성한다();
        DomainState before = 도메인_상태를_조회한다();

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isOk())
                .andExpect(content().string(TEXT_CONTENT));

        assertEquals(before, 도메인_상태를_조회한다());
    }

    @Test
    void T_VIEW_003_인증과_뷰어_세션_헤더를_검증한다() throws Exception {
        String viewerSessionId = 새_세션을_연다(1);

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자(OTHER_READER_ID)))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 현재_세션이_없으면_404이고_교체된_뷰어면_409다() throws Exception {
        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        String replacedViewerSessionId = 새_세션을_연다(1);
        새_세션을_연다(1);

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", replacedViewerSessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIEWER_SESSION_REPLACED"));
    }

    @Test
    void T_VIEW_004_현재_세션과_다른_페이지는_403으로_거부한다() throws Exception {
        String viewerSessionId = 새_세션을_연다(1);

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 2)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 페이지_열기_뒤_대여가_만료되면_콘텐츠_GET은_403이다() throws Exception {
        String viewerSessionId = 새_세션을_연다(1);
        대여를_만료시킨다();

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .with(authentication(인증된_독자()))
                        .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void IMAGE_파일이_없으면_내부_경로를_노출하지_않고_404다() throws Exception {
        String viewerSessionId = 새_세션을_연다(2);
        Files.delete(IMAGE_PATH);

        MvcResult result = mockMvc.perform(
                        get("/api/reading-sessions/current/pages/{pageNumber}/content", 2)
                                .with(authentication(인증된_독자()))
                                .header("X-Viewer-Session-Id", viewerSessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(IMAGE_PATH.toString()));
    }

    private String 새_세션을_연다(int pageNumber) throws Exception {
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

    private TestingAuthenticationToken 인증된_독자() {
        return 인증된_독자(READER_ID);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(new AuthenticatedReader(readerId), null, "ROLE_USER");
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum405-api@example.com', '{noop}password',
                        '2026-07-31 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 10)", READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum405-other@example.com', '{noop}password',
                        '2026-07-31 00:00:00.000000')
                """,
                OTHER_READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 10)", OTHER_READER_ID);
    }

    private void 도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-405 API 도서', '읽어볼까', 2, ?)
                """,
                BOOK_ID,
                BOOK_PRICE_WON);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', ?)
                """,
                TEXT_PAGE_ID,
                BOOK_ID,
                TEXT_CONTENT);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, image_path)
                VALUES (?, ?, 2, 'IMAGE', ?)
                """,
                IMAGE_PAGE_ID,
                BOOK_ID,
                IMAGE_PATH.toString());
    }

    private void 소장_기록을_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', ?,
                        '2026-07-31 00:00:00.000000', '2026-07-31 00:00:00.000000')
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                BOOK_ID,
                UUID.randomUUID().toString(),
                BOOK_PRICE_WON);
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-07-31 00:00:00.000000')
                """,
                READER_ID,
                BOOK_ID,
                OWNERSHIP_PAYMENT_ID);
    }

    private DomainState 도메인_상태를_조회한다() {
        return new DomainState(
                잉크_잔액을_조회한다(),
                수를_조회한다("page_rental"),
                수를_조회한다("ink_ledger"),
                수를_조회한다("reading_session"),
                수를_조회한다("library_entry"),
                jdbcTemplate.queryForObject(
                        "SELECT CAST(updated_at AS CHAR) FROM reading_session WHERE reader_id = ?",
                        String.class,
                        READER_ID),
                jdbcTemplate.queryForObject(
                        "SELECT CAST(updated_at AS CHAR) FROM library_entry WHERE reader_id = ?",
                        String.class,
                        READER_ID));
    }

    private void 대여를_만료시킨다() {
        jdbcTemplate.update(
                """
                UPDATE page_rental
                SET rented_at = '1999-12-01 00:00:00.000000',
                    expires_at = '2000-01-01 00:00:00.000000'
                WHERE reader_id = ?
                """,
                READER_ID);
    }

    private int 잉크_잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 수를_조회한다(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private void 이미지_파일을_생성한다() throws IOException {
        Files.createDirectories(IMAGE_DIRECTORY);
        Files.write(IMAGE_PATH, IMAGE_CONTENT);
    }

    private void 이미지_파일을_정리한다() throws IOException {
        Files.deleteIfExists(IMAGE_PATH);
        Files.deleteIfExists(IMAGE_DIRECTORY);
        Files.deleteIfExists(IMAGE_DIRECTORY.getParent());
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }

    private record DomainState(
            int inkBalance,
            int rentalCount,
            int ledgerCount,
            int readingSessionCount,
            int libraryEntryCount,
            String readingSessionUpdatedAt,
            String libraryEntryUpdatedAt) {}
}
