package com.example.ilgeobolkka.rental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * INV-006 대여 기간 경계({@code rentedAt <= now < expiresAt})가 {@link PageRentalService}의 조회
 * 결과와 정확히 일치하는지 실제 MySQL로 증명한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class PageRentalServiceMySqlIntegrationTest {

    private static final long READER_ID = 434_101L;
    private static final long BOOK_ID = 434_101L;
    private static final long BOOK_PAGE_ID = 434_101L;
    private static final long OTHER_BOOK_PAGE_ID = 434_102L;
    private static final long RENTAL_ID = 434_101L;
    private static final Instant RENTED_AT = Instant.parse("2026-07-01T00:00:00.000000Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-31T00:00:00.000000Z");

    private final PageRentalService pageRentalService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PageRentalServiceMySqlIntegrationTest(
            PageRentalService pageRentalService, JdbcTemplate jdbcTemplate) {
        this.pageRentalService = pageRentalService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        도서와_페이지_2개를_생성한다();
        독자를_생성한다();
        페이지_대여를_생성한다();
    }

    @Test
    void 대여_기간_안이면_활성_대여를_반환한다() {
        Optional<PageRental> active =
                pageRentalService.findActive(READER_ID, BOOK_PAGE_ID, RENTED_AT.plusSeconds(1));

        assertTrue(active.isPresent());
        assertEquals(EXPIRES_AT, active.get().getExpiresAt());
    }

    @Test
    void 시작_시각_정각이면_활성_대여를_반환한다() {
        Optional<PageRental> active =
                pageRentalService.findActive(READER_ID, BOOK_PAGE_ID, RENTED_AT);

        assertTrue(active.isPresent());
    }

    @Test
    void 만료_1밀리초_전이면_활성_대여를_반환한다() {
        Optional<PageRental> active = pageRentalService.findActive(
                READER_ID, BOOK_PAGE_ID, EXPIRES_AT.minusMillis(1));

        assertTrue(active.isPresent());
    }

    @Test
    void 만료_정각이면_비활성으로_취급한다() {
        Optional<PageRental> active =
                pageRentalService.findActive(READER_ID, BOOK_PAGE_ID, EXPIRES_AT);

        assertTrue(active.isEmpty());
    }

    @Test
    void 다른_페이지에는_대여가_없으면_비활성으로_취급한다() {
        Optional<PageRental> active = pageRentalService.findActive(
                READER_ID, OTHER_BOOK_PAGE_ID, RENTED_AT.plusSeconds(1));

        assertTrue(active.isEmpty());
    }

    private void 도서와_페이지_2개를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-434 테스트 도서', '테스트 저자', 2, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '테스트 본문 1')
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 2, 'TEXT', '테스트 본문 2')
                """,
                OTHER_BOOK_PAGE_ID,
                BOOK_ID);
    }

    private void 독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum-434-reader@example.com', 'hash', '2026-06-01 00:00:00.000000')
                """,
                READER_ID);
    }

    private void 페이지_대여를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2026-07-01 00:00:00.000000', '2026-07-31 00:00:00.000000')
                """,
                RENTAL_ID,
                READER_ID,
                BOOK_PAGE_ID);
    }
}
