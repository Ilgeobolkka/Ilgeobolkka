package com.example.ilgeobolkka.library;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(LibraryApiMySqlIntegrationTest.FixedClockConfiguration.class)
@Transactional
class LibraryApiMySqlIntegrationTest {

    private static final long READER_ID = 418_001L;
    private static final long OTHER_READER_ID = 418_002L;
    private static final long RENTED_BOOK_ID = 418_101L;
    private static final long OWNED_BOOK_ID = 418_102L;
    private static final long SECOND_BOOK_ID = 418_103L;
    private static final long THIRD_BOOK_ID = 418_104L;
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    LibraryApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(OTHER_READER_ID);
        도서와_페이지를_생성한다(RENTED_BOOK_ID, "대여 중인 도서", "소설");
        도서와_페이지를_생성한다(OWNED_BOOK_ID, "소장한 도서", "에세이");
        도서와_페이지를_생성한다(SECOND_BOOK_ID, "두 번째 도서", "과학");
        도서와_페이지를_생성한다(THIRD_BOOK_ID, "세 번째 도서", "역사");
    }

    @Test
    void T_LIB_001_마지막_페이지의_최신_대여_한_건만_반환한다() throws Exception {
        서재_항목을_생성한다(418_201L, READER_ID, RENTED_BOOK_ID, 12, "2026-08-01 10:00:00.000000");
        대여를_생성한다(418_301L, READER_ID, RENTED_BOOK_ID, 12,
                "2026-06-01 10:00:00.000000", "2026-07-01 10:00:00.000000");
        대여를_생성한다(418_302L, READER_ID, RENTED_BOOK_ID, 1,
                "2026-07-31 11:00:00.000000", "2026-08-30 11:00:00.000000");
        대여를_생성한다(418_303L, READER_ID, RENTED_BOOK_ID, 12,
                "2026-07-27 10:00:00.123456", "2026-08-26 10:00:00.123456");

        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].bookId").value(RENTED_BOOK_ID))
                .andExpect(jsonPath("$.entries[0].coverImagePath")
                        .value("/assets/covers/book-418101.jpg"))
                .andExpect(jsonPath("$.entries[0].title").value("대여 중인 도서"))
                .andExpect(jsonPath("$.entries[0].category").value("소설"))
                .andExpect(jsonPath("$.entries[0].lastPageNumber").value(12))
                .andExpect(jsonPath("$.entries[0].rentedAt")
                        .value("2026-07-27T10:00:00.123456Z"))
                .andExpect(jsonPath("$.entries[0].expiresAt")
                        .value("2026-08-26T10:00:00.123456Z"))
                .andExpect(jsonPath("$.entries[0].activeRental").value(true))
                .andExpect(jsonPath("$.entries[0].owned").value(false))
                .andExpect(jsonPath("$.entries[0].rentals").doesNotExist());
    }

    @Test
    void T_LIB_002_소장_항목은_마지막_페이지를_유지하고_대여_필드가_null이다() throws Exception {
        서재_항목을_생성한다(418_202L, READER_ID, OWNED_BOOK_ID, 12, "2026-08-01 11:00:00.000000");
        대여를_생성한다(418_304L, READER_ID, OWNED_BOOK_ID, 12,
                "2026-06-01 10:00:00.000000", "2026-07-01 10:00:00.000000");
        소장을_생성한다(READER_ID, OWNED_BOOK_ID, 418_401L);

        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].bookId").value(OWNED_BOOK_ID))
                .andExpect(jsonPath("$.entries[0].lastPageNumber").value(12))
                .andExpect(jsonPath("$.entries[0].rentedAt").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].expiresAt").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].activeRental").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].owned").value(true));
    }

    @Test
    void 서재는_updatedAt과_id_내림차순이며_다른_독자_항목을_제외한다() throws Exception {
        서재_항목을_생성한다(418_210L, READER_ID, RENTED_BOOK_ID, 1, "2026-08-01 09:00:00.000000");
        서재_항목을_생성한다(418_211L, READER_ID, SECOND_BOOK_ID, 1, "2026-08-01 10:00:00.000000");
        서재_항목을_생성한다(418_212L, READER_ID, THIRD_BOOK_ID, 1, "2026-08-01 10:00:00.000000");
        서재_항목을_생성한다(418_299L, OTHER_READER_ID, OWNED_BOOK_ID, 1, "2026-08-01 12:00:00.000000");
        대여를_생성한다(418_310L, READER_ID, RENTED_BOOK_ID, 1,
                "2026-07-01 09:00:00.000000", "2026-07-31 09:00:00.000000");
        대여를_생성한다(418_311L, READER_ID, SECOND_BOOK_ID, 1,
                "2026-07-01 10:00:00.000000", "2026-07-31 10:00:00.000000");
        대여를_생성한다(418_312L, READER_ID, THIRD_BOOK_ID, 1,
                "2026-07-01 11:00:00.000000", "2026-07-31 11:00:00.000000");
        대여를_생성한다(418_399L, OTHER_READER_ID, OWNED_BOOK_ID, 1,
                "2026-07-01 12:00:00.000000", "2026-07-31 12:00:00.000000");

        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[*].bookId")
                        .value(contains(418104, 418103, 418101)));
    }

    @Test
    void 대여는_서버_시각이_만료_시각과_같으면_비활성이고_시작_시각과_같으면_활성이다()
            throws Exception {
        서재_항목을_생성한다(418_220L, READER_ID, RENTED_BOOK_ID, 1, "2026-08-01 10:00:00.000000");
        서재_항목을_생성한다(418_221L, READER_ID, SECOND_BOOK_ID, 1, "2026-08-01 11:00:00.000000");
        대여를_생성한다(418_320L, READER_ID, RENTED_BOOK_ID, 1,
                "2026-07-02 12:00:00.000000", "2026-08-01 12:00:00.000000");
        대여를_생성한다(418_321L, READER_ID, SECOND_BOOK_ID, 1,
                "2026-08-01 12:00:00.000000", "2026-08-31 12:00:00.000000");

        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[*].activeRental").value(contains(true, false)));
    }

    @Test
    void 빈_서재는_빈_목록이고_서재_조회는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/library").with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));

        mockMvc.perform(get("/api/library"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-08-01 00:00:00.000000')
                """,
                readerId,
                "scrum418-" + readerId + "@example.com");
    }

    private void 도서와_페이지를_생성한다(long bookId, String title, String category) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, cover_image_path, total_page_count, price_won)
                VALUES (?, ?, ?, '읽어볼까', ?, 12, 12000)
                """,
                bookId,
                category,
                title,
                "/assets/covers/book-" + bookId + ".jpg");
        for (int pageNumber : new int[] {1, 12}) {
            jdbcTemplate.update(
                    """
                    INSERT INTO book_page
                        (id, book_id, page_number, content_type, text_content)
                    VALUES (?, ?, ?, 'TEXT', ?)
                    """,
                    페이지_ID(bookId, pageNumber),
                    bookId,
                    pageNumber,
                    pageNumber + "쪽");
        }
    }

    private void 서재_항목을_생성한다(
            long entryId,
            long readerId,
            long bookId,
            int lastPageNumber,
            String updatedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (id, reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                entryId,
                readerId,
                bookId,
                lastPageNumber,
                updatedAt);
    }

    private void 대여를_생성한다(
            long rentalId,
            long readerId,
            long bookId,
            int pageNumber,
            String rentedAt,
            String expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rentalId,
                readerId,
                페이지_ID(bookId, pageNumber),
                rentedAt,
                expiresAt);
    }

    private void 소장을_생성한다(long readerId, long bookId, long paymentId) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 12000,
                        '2026-07-01 00:00:00.000000', '2026-07-01 00:01:00.000000')
                """,
                paymentId,
                readerId,
                bookId,
                UUID.randomUUID().toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-07-01 00:01:00.000000')
                """,
                readerId,
                bookId,
                paymentId);
    }

    private long 페이지_ID(long bookId, int pageNumber) {
        return bookId * 100 + pageNumber;
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId),
                null,
                "ROLE_USER");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
