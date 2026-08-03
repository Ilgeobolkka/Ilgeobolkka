package com.example.ilgeobolkka.reading.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.ilgeobolkka.ink.entity.InkLedger;
import com.example.ilgeobolkka.ink.repository.InkLedgerRepository;
import com.example.ilgeobolkka.library.repository.LibraryEntryRepository;
import com.example.ilgeobolkka.library.service.LibraryService;
import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.ilgeobolkka.rental.repository.PageRentalRepository;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * T-RENT-007. 대여·원장·마지막 위치 저장 단계마다 실패를 주입해 정책이 요구하는
 * "함께 성공하거나 함께 실패"(INV-003)를 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingFacadeRollbackMySqlIntegrationTest.FailingLibraryServiceConfig.class)
class ReadingFacadeRollbackMySqlIntegrationTest {

    private static final long READER_ID = 411_901L;
    private static final long BOOK_ID = 411_902L;
    private static final long PAGE_ID = 411_903L;
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00.123456Z");

    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PageRentalRepository pageRentalRepository;

    @MockitoSpyBean
    private InkLedgerRepository inkLedgerRepository;

    @Autowired
    ReadingFacadeRollbackMySqlIntegrationTest(
            ReadingFacade readingFacade, JdbcTemplate jdbcTemplate) {
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_도서를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_RENT_007_대여_저장_직전_실패하면_아무_변경도_남지_않는다() {
        doThrow(new IllegalStateException("강제 대여 저장 실패"))
                .when(pageRentalRepository)
                .save(any(PageRental.class));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> readingFacade.openNewSession(READER_ID, BOOK_ID, 1));

        assertEquals("강제 대여 저장 실패", failure.getMessage());
        verify(pageRentalRepository).save(any(PageRental.class));
        모든_변경이_롤백됐는지_확인한다();
    }

    @Test
    void T_RENT_007_원장_저장_직전_실패하면_대여와_잔액도_남지_않는다() {
        doThrow(new IllegalStateException("강제 원장 저장 실패"))
                .when(inkLedgerRepository)
                .save(any(InkLedger.class));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> readingFacade.openNewSession(READER_ID, BOOK_ID, 1));

        assertEquals("강제 원장 저장 실패", failure.getMessage());
        verify(inkLedgerRepository).save(any(InkLedger.class));
        모든_변경이_롤백됐는지_확인한다();
    }

    @Test
    void T_RENT_007_마지막_위치_저장에_실패하면_차감과_대여도_남지_않는다() {
        assertThrows(
                IllegalStateException.class,
                () -> readingFacade.openNewSession(READER_ID, BOOK_ID, 1));

        모든_변경이_롤백됐는지_확인한다();
    }

    private void 모든_변경이_롤백됐는지_확인한다() {
        assertAll(
                () -> assertEquals(5, 잔액을_조회한다()),
                () -> assertEquals(0, 개수를_조회한다("page_rental")),
                () -> assertEquals(0, 개수를_조회한다("ink_ledger")),
                () -> assertEquals(0, 개수를_조회한다("reading_session")),
                () -> assertEquals(0, 개수를_조회한다("library_entry")));
    }

    private void 독자와_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum411-rollback@example.com', '{noop}password',
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
                VALUES (?, '소설', 'SCRUM-411 롤백 도서', '읽어볼까', 1, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지')
                """,
                PAGE_ID,
                BOOK_ID);
    }

    private int 잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?", Integer.class, READER_ID);
    }

    private int 개수를_조회한다(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE reader_id = ?", Integer.class, READER_ID);
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
    static class FailingLibraryServiceConfig {

        @Bean
        @Primary
        LibraryService failingLibraryService(LibraryEntryRepository libraryEntryRepository) {
            return new FailingLibraryService(libraryEntryRepository);
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    /**
     * Mockito 목 대신 하위 클래스를 쓴다. {@code recordVisit}은 {@code Propagation.MANDATORY}라
     * 목을 스터빙하는 호출조차 트랜잭션 프록시를 타고 실패하며, 그때 남은 matcher가 뒤따르는 다른
     * 테스트의 Mockito 상태까지 망가뜨린다.
     */
    static class FailingLibraryService extends LibraryService {

        FailingLibraryService(LibraryEntryRepository libraryEntryRepository) {
            super(libraryEntryRepository);
        }

        @Override
        public void recordVisit(long readerId, long bookId, int pageNumber, Instant visitedAt) {
            throw new IllegalStateException("강제 마지막 위치 저장 실패");
        }
    }
}
