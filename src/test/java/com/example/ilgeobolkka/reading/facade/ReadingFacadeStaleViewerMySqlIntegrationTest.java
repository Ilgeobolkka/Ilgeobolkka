package com.example.ilgeobolkka.reading.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import com.example.ilgeobolkka.reading.repository.ReadingSessionRepository;
import com.example.ilgeobolkka.reading.service.ReadingSessionService;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/**
 * T-VIEW-002의 "이전 열람 세션의 지연 요청". 뷰어 확인을 통과한 직후 새 뷰어가 세션을 교체한
 * 상황을 재현한다. 확인을 건너뛰는 {@link ReadingSessionService}로 그 창을 결정적으로 만들고,
 * 갱신 문장이 뷰어를 조건으로 갖는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingFacadeStaleViewerMySqlIntegrationTest.SkipViewerCheckConfig.class)
class ReadingFacadeStaleViewerMySqlIntegrationTest {

    private static final long READER_ID = 411_801L;
    private static final long BOOK_ID = 411_802L;
    private static final long FIRST_PAGE_ID = 411_803L;
    private static final long SECOND_PAGE_ID = 411_804L;
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00.123456Z");
    private static final UUID REPLACED_VIEWER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID CURRENT_VIEWER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadingFacadeStaleViewerMySqlIntegrationTest(
            ReadingFacade readingFacade, JdbcTemplate jdbcTemplate) {
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_도서를_생성한다();
        현재_세션을_새_뷰어로_둔다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_VIEW_002_교체된_뷰어의_지연_요청은_현재_세션을_되돌리지_못한다() {
        assertThrows(
                ViewerSessionReplacedException.class,
                () -> readingFacade.movePage(READER_ID, REPLACED_VIEWER, 2));

        assertAll(
                () -> assertEquals(CURRENT_VIEWER.toString(), 현재_뷰어를_조회한다()),
                () -> assertEquals(1, 현재_페이지를_조회한다()),
                () -> assertEquals(5, 잔액을_조회한다()),
                () -> assertEquals(0, 대여_수를_조회한다()));
    }

    private void 현재_세션을_새_뷰어로_둔다() {
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (reader_id, book_id, current_page_number, viewer_session_id, updated_at)
                VALUES (?, ?, 1, ?, '2026-07-30 10:00:00.123456')
                """,
                READER_ID,
                BOOK_ID,
                CURRENT_VIEWER.toString());
    }

    private void 독자와_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum411-stale@example.com', '{noop}password',
                        '2026-07-30 00:00:00.000000')
                AS incoming
                ON DUPLICATE KEY UPDATE password_hash = incoming.password_hash
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)
                AS incoming
                ON DUPLICATE KEY UPDATE balance = incoming.balance
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', 'SCRUM-411 지연 요청 도서', '읽어볼까', 2, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지')
                """,
                FIRST_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 2, 'TEXT', '둘째 페이지')
                """,
                SECOND_PAGE_ID,
                BOOK_ID);
    }

    private String 현재_뷰어를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT viewer_session_id FROM reading_session WHERE reader_id = ?",
                String.class,
                READER_ID);
    }

    private int 현재_페이지를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT current_page_number FROM reading_session WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 대여_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SkipViewerCheckConfig {

        @Bean
        @Primary
        ReadingSessionService skipViewerCheckReadingSessionService(
                ReadingSessionRepository readingSessionRepository) {
            return new SkipViewerCheckReadingSessionService(readingSessionRepository);
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    /**
     * 뷰어 확인을 건너뛰어 "확인은 통과했는데 그 사이 교체된" 창을 재현한다. 실제 경합을 스레드로
     * 재현하면 타이밍에 기대게 되므로, 확인만 무력화해 갱신 문장의 조건을 결정적으로 검증한다.
     */
    static class SkipViewerCheckReadingSessionService extends ReadingSessionService {

        private final ReadingSessionRepository readingSessionRepository;

        SkipViewerCheckReadingSessionService(ReadingSessionRepository readingSessionRepository) {
            super(readingSessionRepository);
            this.readingSessionRepository = readingSessionRepository;
        }

        @Override
        public ReadingSession getCurrentSession(long readerId, UUID viewerSessionId) {
            return readingSessionRepository
                    .findByReaderId(readerId)
                    .orElseThrow(() -> new ReadingSessionNotFoundException(readerId));
        }
    }
}
