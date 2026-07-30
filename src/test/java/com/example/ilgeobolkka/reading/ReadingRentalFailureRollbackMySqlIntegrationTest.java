package com.example.ilgeobolkka.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.ilgeobolkka.library.service.LibraryEntryService;
import com.example.ilgeobolkka.reading.facade.OpenPageViewer;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
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
import org.springframework.test.util.AopTestUtils;

/**
 * SCRUM-435: {@code ReadingFacade.provideRentedPage}의 저장 순서 마지막 단계({@code
 * LibraryEntryService.recordLastPosition})에서 예외가 나면 앞서 저장한 {@code PageRental}과
 * 1잉크 차감까지 전부 롤백되는지 증명한다({@code @Transactional} 경계 자체를 검증하는 목적이므로
 * 클래스 레벨 {@code @Transactional}을 두지 않고 실제 커밋 상태를 확인한다).
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ReadingRentalFailureRollbackMySqlIntegrationTest.FailingLibraryEntryServiceConfig.class)
class ReadingRentalFailureRollbackMySqlIntegrationTest {

    private static final long READER_ID = 435_201L;
    private static final long BOOK_ID = 435_201L;
    private static final long BOOK_PAGE_ID = 435_201L;

    private final ReadingFacade readingFacade;
    private final JdbcTemplate jdbcTemplate;
    private final LibraryEntryService libraryEntryService;

    @Autowired
    ReadingRentalFailureRollbackMySqlIntegrationTest(
            ReadingFacade readingFacade,
            JdbcTemplate jdbcTemplate,
            LibraryEntryService libraryEntryService) {
        this.readingFacade = readingFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.libraryEntryService = libraryEntryService;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        소장하지_않은_도서와_페이지를_생성한다();
        // 주입된 빈은 트랜잭션 프록시가 감싼 목이다. LibraryEntryService.recordLastPosition 이
        // @Transactional(MANDATORY) 이므로 프록시를 통해 스터빙하면 트랜잭션이 없는 이 시점에
        // IllegalTransactionStateException 이 먼저 터진다. 프록시를 벗겨 목 자체에 스터빙한다.
        doThrow(new IllegalStateException("강제 서재 위치 저장 실패"))
                .when(AopTestUtils.<LibraryEntryService>getTargetObject(libraryEntryService))
                .recordLastPosition(anyLong(), any(), any());
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void 서재_위치_저장에_실패하면_대여와_차감도_함께_롤백된다() {
        assertThrows(
                IllegalStateException.class,
                () -> readingFacade.openPage(READER_ID, new OpenPageViewer.NewViewer(BOOK_ID), 1));

        assertEquals(5, 잉크_잔액을_조회한다());
        assertEquals(0, 페이지_대여_수를_조회한다());
        assertEquals(0, 잉크_내역_수를_조회한다());
        assertEquals(0, 서재_항목_수를_조회한다());
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-01 00:00:00.000000')
                """,
                READER_ID,
                "scrum435-rollback-" + READER_ID + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)", READER_ID);
    }

    private void 소장하지_않은_도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, 'A', 'Book 435 Rollback', 'Author 435', 'Description 435',
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

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingLibraryEntryServiceConfig {

        @Bean
        @Primary
        LibraryEntryService failingLibraryEntryService() {
            return mock(LibraryEntryService.class);
        }
    }
}
