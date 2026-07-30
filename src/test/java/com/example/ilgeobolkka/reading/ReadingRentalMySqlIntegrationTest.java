package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * SCRUM-434(3/5)에서 구현한 {@code ReadingFacade.provideRentedPage}의 3~4단계(활성 대여 확인,
 * 잠금 뒤 재확인)가 INV-002·INV-006을 지키는지 T-RENT-002, T-RENT-003 전반부로 증명한다. 시간 경계
 * 테스트이므로 고정 {@link Clock}을 주입해 결과가 실행 시각에 흔들리지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingRentalMySqlIntegrationTest.FixedClockConfig.class)
@Transactional
class ReadingRentalMySqlIntegrationTest {

    private static final long READER_ID = 434_001L;
    private static final long BOOK_ID = 434_001L;
    private static final long BOOK_PAGE_ID = 434_001L;
    private static final long RENTAL_ID = 434_001L;
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00.000000Z");
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingRentalMySqlIntegrationTest(
            MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자와_잉크_계좌를_생성한다();
        소장하지_않은_도서와_페이지를_생성한다();
    }

    /** T-RENT-002: 활성 대여 중 같은 페이지를 여러 번 열어도 잔액·내역은 바뀌지 않고 만료 시각도 같다. */
    @Test
    void 활성_대여_중_같은_페이지를_다시_열면_잔액과_내역이_바뀌지_않고_같은_만료_시각을_반환한다()
            throws Exception {
        Instant rentedAt = NOW.minusSeconds(60 * 60 * 24);
        Instant expiresAt = NOW.plusSeconds(60L * 60 * 24 * 29);
        활성_대여를_생성한다(rentedAt, expiresAt);

        Instant firstExpiresAt = 응답의_만료_시각을_읽는다(페이지를_연다());
        Instant secondExpiresAt = 응답의_만료_시각을_읽는다(페이지를_연다());

        assertEquals(expiresAt, firstExpiresAt);
        assertEquals(expiresAt, secondExpiresAt);
        assertEquals(5, 잉크_잔액을_조회한다());
        assertEquals(0, 잉크_내역_수를_조회한다());
        assertEquals(1, 페이지_대여_수를_조회한다());
    }

    /** T-RENT-003 전반부: 만료 1밀리초 전에는 무차감으로 재열람한다. */
    @Test
    void 만료_1밀리초_전에_페이지를_열면_무차감으로_재열람한다() throws Exception {
        Instant rentedAt = NOW.minusSeconds(60L * 60 * 24 * 30);
        Instant expiresAt = NOW.plusMillis(1);
        활성_대여를_생성한다(rentedAt, expiresAt);

        MvcResult result = 페이지를_연다();

        assertEquals(expiresAt, 응답의_만료_시각을_읽는다(result));
        assertEquals(5, 잉크_잔액을_조회한다());
        assertEquals(1, 페이지_대여_수를_조회한다());
    }

    private MvcResult 페이지를_연다() throws Exception {
        return mockMvc.perform(post("/api/books/{bookId}/reading-sessions", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNumber\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owned").value(false))
                .andExpect(jsonPath("$.deductedInk").value(0))
                .andReturn();
    }

    private Instant 응답의_만료_시각을_읽는다(MvcResult result) throws Exception {
        return Instant.parse(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("expiresAt")
                .asText());
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-01 00:00:00.000000')
                """,
                READER_ID,
                "scrum434-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)", READER_ID);
    }

    private void 소장하지_않은_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 434', 'Author 434', 'Description 434',
                        '/assets/covers/demo/category-01.svg', 1, 9001)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', 'Page 1 content')
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
    }

    private void 활성_대여를_생성한다(Instant rentedAt, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                RENTAL_ID,
                READER_ID,
                BOOK_PAGE_ID,
                DATETIME_FORMATTER.format(rentedAt),
                DATETIME_FORMATTER.format(expiresAt));
    }

    private int 잉크_잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 잉크_내역_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 페이지_대여_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ? AND book_page_id = ?",
                Integer.class,
                READER_ID,
                BOOK_PAGE_ID);
    }

    private TestingAuthenticationToken 인증된_독자() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(READER_ID), null, "ROLE_USER");
    }

    /** INV-006 경계 테스트가 실행 시각에 흔들리지 않도록 애플리케이션 시계를 고정 시각으로 교체한다. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock clock() {
            return new FixedInstantClock(NOW);
        }
    }

    private static final class FixedInstantClock extends Clock {

        private final AtomicReference<Instant> instant;

        private FixedInstantClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("이 테스트 시계는 시간대를 바꾸지 않는다.");
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
