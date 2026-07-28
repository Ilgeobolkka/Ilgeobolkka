package com.example.ilgeobolkka.ink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class InkQueryApiMySqlIntegrationTest {

    private static final long READER_ID = 407_000L;
    private static final long INITIAL_READER_ID = 407_100L;
    private static final long MISSING_ACCOUNT_READER_ID = 407_101L;
    private static final long BOOK_ID = 407_000L;
    private static final long BOOK_PAGE_ID = 407_000L;
    private static final long PURCHASE_ID_BASE = 407_000L;
    private static final long RENTAL_ID_BASE = 407_000L;
    private static final long LEDGER_ID_BASE = 407_000L;
    private static final String OLD_OCCURRED_AT = "2020-01-01 00:00:00.000000";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InkQueryApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        독자를_생성한다(READER_ID, "ledger-reader@example.com");
        잉크_계좌를_생성한다(READER_ID, 595);
        도서와_페이지를_생성한다();
        같은_시각의_원장_11건을_생성한다();
    }

    @Test
    void T_INK_HIST_001_차감_내역은_도서와_원본_페이지와_대여_기간을_반환한다() throws Exception {
        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "1")
                                .with(인증된_독자(READER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].type").value("DEDUCTION"))
                .andExpect(jsonPath("$.entries[0].amount").value(1))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(595))
                .andExpect(jsonPath("$.entries[0].bookTitle").value("오래된 대여 도서"))
                .andExpect(jsonPath("$.entries[0].pageNumber").value(12))
                .andExpect(jsonPath("$.entries[0].rentedAt").value("2020-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.entries[0].expiresAt").value("2020-01-31T00:00:00Z"))
                .andExpect(jsonPath("$.entries[0].occurredAt").value("2020-01-01T00:00:00Z"));
    }

    @Test
    void T_INK_HIST_002_지급_내역과_현재_100잉크_잔액이_일치한다() throws Exception {
        독자를_생성한다(INITIAL_READER_ID, "initial-reader@example.com");
        잉크_계좌를_생성한다(INITIAL_READER_ID, 100);
        이용권_결제를_생성한다(PURCHASE_ID_BASE + 100, INITIAL_READER_ID, 100);
        지급_원장을_생성한다(
                LEDGER_ID_BASE + 100,
                INITIAL_READER_ID,
                100,
                PURCHASE_ID_BASE + 100);

        mockMvc.perform(get("/api/ink/balance").with(인증된_독자(INITIAL_READER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));

        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "1")
                                .with(인증된_독자(INITIAL_READER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].type").value("GRANT"))
                .andExpect(jsonPath("$.entries[0].amount").value(100))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(100))
                .andExpect(jsonPath("$.entries[0].bookTitle").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].pageNumber").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].rentedAt").value(nullValue()))
                .andExpect(jsonPath("$.entries[0].expiresAt").value(nullValue()));
    }

    @Test
    void 잉크_계좌를_찾을_수_없으면_공통_리소스_없음_오류로_응답한다() throws Exception {
        독자를_생성한다(MISSING_ACCOUNT_READER_ID, "missing-account-reader@example.com");

        mockMvc.perform(
                        get("/api/ink/balance")
                                .with(인증된_독자(MISSING_ACCOUNT_READER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void T_INK_HIST_003_같은_시각의_원장도_ID_역순으로_10개씩_중복_없이_반환한다()
            throws Exception {
        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "1")
                                .with(인증된_독자(READER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(10))
                .andExpect(
                        jsonPath("$.entries[*].balanceAfter")
                                .value(contains(595, 596, 496, 497, 397, 398, 298, 299, 199, 200)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11));

        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "2")
                                .with(인증된_독자(READER_ID)))
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
        mockMvc.perform(get("/api/ink/ledger").with(인증된_독자(READER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "0")
                                .with(인증된_독자(READER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "not-a-number")
                                .with(인증된_독자(READER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "3")
                                .with(인증된_독자(READER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11));
    }

    @Test
    void T_INK_004_005_수년_전_지급과_대여와_로그인_세션이_만료돼도_잔액과_원장은_유지된다()
            throws Exception {
        MockHttpSession session = 인증된_세션(READER_ID);

        mockMvc.perform(get("/api/ink/balance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(595));

        session.invalidate();

        mockMvc.perform(get("/api/ink/balance"))
                .andExpect(status().isUnauthorized());

        assertEquals(
                595,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));
        assertEquals(
                11,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                        Integer.class,
                        READER_ID));

        mockMvc.perform(
                        get("/api/ink/ledger")
                                .param("page", "1")
                                .with(인증된_독자(READER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(10))
                .andExpect(jsonPath("$.totalCount").value(11));
    }

    private RequestPostProcessor 인증된_독자(long readerId) {
        return authentication(
                new TestingAuthenticationToken(
                        new AuthenticatedReader(readerId), null, "ROLE_USER"));
    }

    private MockHttpSession 인증된_세션(long readerId) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new AuthenticatedReader(readerId), null, "ROLE_USER");
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext);
        return session;
    }

    private void 독자를_생성한다(long readerId, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'encoded-password', '2020-01-01 00:00:00.000000')
                """,
                readerId,
                email);
    }

    private void 잉크_계좌를_생성한다(long readerId, int balance) {
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)",
                readerId,
                balance);
    }

    private void 도서와_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, '기술', '오래된 대여 도서', '테스트 저자', NULL, NULL, 12, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 12, 'TEXT', '열두 번째 페이지', NULL)
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
    }

    private void 같은_시각의_원장_11건을_생성한다() {
        int purchaseSequence = 0;
        int rentalSequence = 0;
        int[] balances = {100, 200, 199, 299, 298, 398, 397, 497, 496, 596, 595};

        for (int sequence = 1; sequence <= balances.length; sequence++) {
            if (sequence % 2 == 1 && sequence > 1) {
                long rentalId = RENTAL_ID_BASE + ++rentalSequence;
                페이지_대여를_생성한다(rentalId, READER_ID);
                차감_원장을_생성한다(
                        LEDGER_ID_BASE + sequence,
                        READER_ID,
                        balances[sequence - 1],
                        rentalId);
            } else {
                long purchaseId = PURCHASE_ID_BASE + ++purchaseSequence;
                이용권_결제를_생성한다(purchaseId, READER_ID, 100);
                지급_원장을_생성한다(
                        LEDGER_ID_BASE + sequence,
                        READER_ID,
                        balances[sequence - 1],
                        purchaseId);
            }
        }
    }

    private void 이용권_결제를_생성한다(long purchaseId, long readerId, int grantedInk) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', 1000, ?, ?, ?)
                """,
                purchaseId,
                readerId,
                UUID.nameUUIDFromBytes(("ink-purchase-" + purchaseId).getBytes()).toString(),
                grantedInk,
                OLD_OCCURRED_AT,
                OLD_OCCURRED_AT);
    }

    private void 페이지_대여를_생성한다(long rentalId, long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2020-01-01 00:00:00.000000',
                        '2020-01-31 00:00:00.000000')
                """,
                rentalId,
                readerId,
                BOOK_PAGE_ID);
    }

    private void 지급_원장을_생성한다(
            long ledgerId,
            long readerId,
            int balanceAfter,
            long purchaseId) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, occurred_at)
                VALUES (?, ?, 'GRANT', 100, ?, ?, ?)
                """,
                ledgerId,
                readerId,
                balanceAfter,
                purchaseId,
                OLD_OCCURRED_AT);
    }

    private void 차감_원장을_생성한다(
            long ledgerId,
            long readerId,
            int balanceAfter,
            long rentalId) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     page_rental_id, occurred_at)
                VALUES (?, ?, 'DEDUCTION', 1, ?, ?, ?)
                """,
                ledgerId,
                readerId,
                balanceAfter,
                rentalId,
                OLD_OCCURRED_AT);
    }
}
