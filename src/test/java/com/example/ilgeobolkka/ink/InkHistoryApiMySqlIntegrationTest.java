package com.example.ilgeobolkka.ink;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@Transactional
class InkHistoryApiMySqlIntegrationTest {

    private static final long READER_ID = 431_001L;
    private static final long OTHER_READER_ID = 431_002L;
    private static final long BOOK_ID = 431_001L;
    private static final long BOOK_PAGE_ID = 431_001L;
    private static final long PURCHASE_ID_BASE = 431_100L;
    private static final long RENTAL_ID = 431_001L;
    private static final long LEDGER_ID_BASE = 431_100L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InkHistoryApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자와_잉크_계좌를_생성한다(READER_ID);
        독자와_잉크_계좌를_생성한다(OTHER_READER_ID);
        도서와_페이지를_생성한다();
    }

    @Test
    void T_INK_HIST_001_차감_원장은_도서와_대여_필드를_반환한다() throws Exception {
        잔액을_변경한다(READER_ID, 99);
        지급_원장을_생성한다(
                READER_ID,
                PURCHASE_ID_BASE,
                LEDGER_ID_BASE,
                100,
                "2026-07-27 09:00:00.123456");
        차감_원장을_생성한다(
                READER_ID,
                RENTAL_ID,
                LEDGER_ID_BASE + 1,
                99,
                "2026-07-27 10:00:00.123456",
                "2026-08-26 10:00:00.123456");

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].type").value("DEDUCTION"))
                .andExpect(jsonPath("$.entries[0].amount").value(1))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(99))
                .andExpect(jsonPath("$.entries[0].bookTitle").value("샘플 도서"))
                .andExpect(jsonPath("$.entries[0].pageNumber").value(12))
                .andExpect(
                        jsonPath("$.entries[0].rentedAt")
                                .value("2026-07-27T10:00:00.123456Z"))
                .andExpect(
                        jsonPath("$.entries[0].expiresAt")
                                .value("2026-08-26T10:00:00.123456Z"))
                .andExpect(
                        jsonPath("$.entries[0].occurredAt")
                                .value("2026-07-27T10:00:00.123456Z"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void T_INK_HIST_002_지급_원장과_현재_잔액은_일치한다() throws Exception {
        잔액을_변경한다(READER_ID, 100);
        지급_원장을_생성한다(
                READER_ID,
                PURCHASE_ID_BASE,
                LEDGER_ID_BASE,
                100,
                "2026-07-27 09:00:00.123456");

        mockMvc.perform(get("/api/ink/balance")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].type").value("GRANT"))
                .andExpect(jsonPath("$.entries[0].amount").value(100))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(100))
                .andExpect(jsonPath("$.entries[0].bookTitle").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].pageNumber").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].rentedAt").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].expiresAt").value(nullValue()))
                .andExpect(
                        jsonPath("$.entries[0].occurredAt")
                                .value("2026-07-27T09:00:00.123456Z"));
    }

    @Test
    void 잉크가_없는_독자는_0잔액을_반환한다() throws Exception {
        mockMvc.perform(get("/api/ink/balance")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void T_INK_HIST_003_같은_시각의_11개_원장은_ID_내림차순으로_나뉜다() throws Exception {
        잔액을_변경한다(READER_ID, 1_100);
        IntStream.rangeClosed(1, 11)
                .forEach(index -> 지급_원장을_생성한다(
                        READER_ID,
                        PURCHASE_ID_BASE + index,
                        LEDGER_ID_BASE + index,
                        index * 100,
                        "2026-07-27 09:00:00.123456"));

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(10))
                .andExpect(
                        jsonPath("$.entries[*].balanceAfter")
                                .value(contains(1100, 1000, 900, 800, 700, 600, 500, 400, 300, 200)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11));

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "2")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(100))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11));
    }

    @Test
    void T_INK_HIST_004_잘못된_페이지는_400이고_범위_초과_양수는_빈_목록이다()
            throws Exception {
        잔액을_변경한다(READER_ID, 100);
        지급_원장을_생성한다(
                READER_ID,
                PURCHASE_ID_BASE,
                LEDGER_ID_BASE,
                100,
                "2026-07-27 09:00:00.123456");

        mockMvc.perform(get("/api/ink/ledger")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "not-a-number")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "0")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "-1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "2")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1));

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "214748366")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page").value(214748366))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void 잔액과_원장_조회는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/ink/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/ink/ledger").param("page", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 현재_독자의_잔액과_원장만_반환한다() throws Exception {
        잔액을_변경한다(READER_ID, 100);
        지급_원장을_생성한다(
                READER_ID,
                PURCHASE_ID_BASE,
                LEDGER_ID_BASE,
                100,
                "2026-07-27 09:00:00.123456");

        잔액을_변경한다(OTHER_READER_ID, 200);
        지급_원장을_생성한다(
                OTHER_READER_ID,
                PURCHASE_ID_BASE + 1,
                LEDGER_ID_BASE + 1,
                100,
                "2026-07-27 10:00:00.123456");
        지급_원장을_생성한다(
                OTHER_READER_ID,
                PURCHASE_ID_BASE + 2,
                LEDGER_ID_BASE + 2,
                200,
                "2026-07-27 11:00:00.123456");

        mockMvc.perform(get("/api/ink/balance")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));

        mockMvc.perform(get("/api/ink/ledger")
                        .param("page", "1")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(100))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    private void 독자와_잉크_계좌를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                readerId,
                "scrum431-" + readerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                readerId);
    }

    private void 도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', '샘플 도서', '샘플 저자', 100, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 12, 'TEXT', '샘플 본문', NULL)
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
    }

    private void 잔액을_변경한다(long readerId, int balance) {
        jdbcTemplate.update(
                "UPDATE ink_account SET balance = ? WHERE reader_id = ?",
                balance,
                readerId);
    }

    private void 지급_원장을_생성한다(
            long readerId,
            long purchaseId,
            long ledgerId,
            int balanceAfter,
            String occurredAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', 1000, 100, ?, ?)
                """,
                purchaseId,
                readerId,
                new UUID(0L, purchaseId).toString(),
                occurredAt,
                occurredAt);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, page_rental_id, occurred_at)
                VALUES (?, ?, 'GRANT', 100, ?, ?, NULL, ?)
                """,
                ledgerId,
                readerId,
                balanceAfter,
                purchaseId,
                occurredAt);
    }

    private void 차감_원장을_생성한다(
            long readerId,
            long rentalId,
            long ledgerId,
            int balanceAfter,
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
                BOOK_PAGE_ID,
                rentedAt,
                expiresAt);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, page_rental_id, occurred_at)
                VALUES (?, ?, 'DEDUCTION', 1, ?, NULL, ?, ?)
                """,
                ledgerId,
                readerId,
                balanceAfter,
                rentalId,
                rentedAt);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId),
                null,
                "ROLE_USER");
    }
}
