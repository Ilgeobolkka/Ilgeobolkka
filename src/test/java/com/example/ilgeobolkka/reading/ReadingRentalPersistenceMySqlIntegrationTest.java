package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.facade.OpenPageViewer;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * SCRUM-435: {@code ReadingFacade.provideRentedPage}의 정책 5~7단계(잉크 잔액 확인, 1잉크 차감,
 * {@code PageRental}/{@code LibraryEntry} 저장)가 원자적으로 함께 커밋되는지 T-RENT-001,
 * T-BAL-002, T-BAL-003과 잉크 무기한 보존(INV-010)을 증명한다. 각 테스트는 클래스 레벨
 * {@code @Transactional}로 감싸여 종료 후 자동 롤백되므로 같은 식별자를 재사용해도 안전하다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingRentalPersistenceMySqlIntegrationTest.FixedClockConfig.class)
@Transactional
class ReadingRentalPersistenceMySqlIntegrationTest {

    private static final long READER_ID = 435_001L;
    private static final long BOOK_ID = 435_001L;
    private static final long BOOK_PAGE_ID = 435_001L;
    private static final long OTHER_RENTAL_ID = 435_101L;
    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00.000000Z");
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final ReadingFacade readingFacade;
    private final InkService inkService;
    private final JdbcTemplate jdbcTemplate;
    private final MutableClock clock;

    @Autowired
    ReadingRentalPersistenceMySqlIntegrationTest(
            ReadingFacade readingFacade,
            InkService inkService,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.readingFacade = readingFacade;
        this.inkService = inkService;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = (MutableClock) clock;
        this.clock.moveTo(NOW);
    }

    @BeforeEach
    void setUp() {
        독자와_잉크_계좌를_생성한다(5);
        소장하지_않은_도서와_페이지를_생성한다();
    }

    /** T-RENT-001: 소장·활성 대여가 없는 페이지를 열면 1잉크 차감·원장·대여·서재 위치가 함께 커밋된다. */
    @Test
    void 소장도_활성_대여도_없는_페이지를_열면_차감과_저장이_함께_이루어진다() throws Exception {
        OpenPageResponse response =
                readingFacade.openPage(READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1);

        assertFalse(response.owned());
        assertEquals(1, response.deductedInk());
        assertEquals(response.rentedAt().plus(Duration.ofDays(30)), response.expiresAt());
        assertEquals(4, 잉크_잔액을_조회한다());
        assertEquals(1, 페이지_대여_수를_조회한다());
        assertEquals(1, 잉크_내역_수를_조회한다());
        assertEquals(1, 서재_항목_수를_조회한다());
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "SELECT last_page_number FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID));
    }

    /** T-BAL-002: 잔액 1로 차감하면 정확히 0이 된다. */
    @Test
    void 잔액이_1이면_차감_후_정확히_0이_된다() throws Exception {
        잉크_잔액을_설정한다(1);

        readingFacade.openPage(READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1);

        assertEquals(0, 잉크_잔액을_조회한다());
    }

    /** T-BAL-003: 잔액 0이라도 활성 대여 중인 페이지는 재열람 시 새 행이 생기지 않는다(회귀 확인). */
    @Test
    void 잔액이_0이어도_활성_대여_페이지는_재열람에서_새_행이_생기지_않는다() throws Exception {
        잉크_잔액을_설정한다(0);
        Instant rentedAt = NOW.minusSeconds(60 * 60 * 24);
        Instant expiresAt = NOW.plusSeconds(60L * 60 * 24 * 29);
        활성_대여를_생성한다(rentedAt, expiresAt);

        OpenPageResponse response =
                readingFacade.openPage(READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1);

        assertEquals(0, response.deductedInk());
        assertEquals(0, 잉크_잔액을_조회한다());
        assertEquals(0, 잉크_내역_수를_조회한다());
        assertEquals(1, 페이지_대여_수를_조회한다());
    }

    // 잉크 부족 시 아무 행도 남지 않음(T-BAL-001의 이번 범위분)은 이 클래스에서 검증할 수 없다.
    // 클래스 레벨 @Transactional 때문에 openPage 가 테스트 트랜잭션에 합류하고, 롤백은 테스트가
    // 끝날 때 일어나므로 먼저 저장된 page_rental 행이 조회에 그대로 보인다.
    // → ReadingRentalInsufficientInkMySqlIntegrationTest (비-@Transactional)에서 검증한다.

    /** 잉크는 만료되지 않는다(INV-010): 차감 뒤 시간이 수년 흘러도 잔액·내역은 그대로다. */
    @Test
    void 차감_이후_시간이_수년_흘러도_잔액과_내역은_그대로_유지된다() throws Exception {
        readingFacade.openPage(READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1);
        int balanceAfterDeduction = 잉크_잔액을_조회한다();
        int ledgerCountAfterDeduction = 잉크_내역_수를_조회한다();

        clock.moveTo(NOW.plusSeconds(60L * 60 * 24 * 365 * 5));

        assertEquals(balanceAfterDeduction, inkService.getBalance(READER_ID));
        assertEquals(ledgerCountAfterDeduction, 잉크_내역_수를_조회한다());
    }

    private void 독자와_잉크_계좌를_생성한다(int balance) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-01 00:00:00.000000')
                """,
                READER_ID,
                "scrum435-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)", READER_ID, balance);
    }

    private void 소장하지_않은_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 435', 'Author 435', 'Description 435',
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
                OTHER_RENTAL_ID,
                READER_ID,
                BOOK_PAGE_ID,
                DATETIME_FORMATTER.format(rentedAt),
                DATETIME_FORMATTER.format(expiresAt));
    }

    private void 잉크_잔액을_설정한다(int balance) {
        jdbcTemplate.update(
                "UPDATE ink_account SET balance = ? WHERE reader_id = ?", balance, READER_ID);
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

    private int 서재_항목_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    /** 잉크 무기한 보존 테스트가 시간을 앞으로 이동시킬 수 있도록 애플리케이션 시계를 교체한다. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock clock() {
            return new MutableClock(NOW);
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        void moveTo(Instant newInstant) {
            instant.set(newInstant);
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
