package com.example.ilgeobolkka.ownership;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "portone.payment.enabled=true",
    "portone.payment.store-id=store-test",
    "portone.payment.channel-key=channel-test",
    "portone.payment.api-secret=test-api-secret",
    "portone.payment.webhook-secret=whsec_dGVzdC13ZWJob29rLXNlY3JldA=="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class OwnershipPaymentApiMySqlIntegrationTest {

    private static final long READER_ID = 414_002L;
    private static final long BOOK_ID = 414_102L;
    private static final int BOOK_PRICE_WON = 18_000;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OwnershipPaymentApiMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        도서와_전체_페이지를_생성한다();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_PAY_001_소장_결제를_준비하면_정적_주문명과_원가를_반환하고_잉크를_바꾸지_않는다()
            throws Exception {
        모든_페이지를_대여한다();
        MvcResult inkPurchaseResult = mockMvc.perform(post("/api/ink/purchases")
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID inkPaymentId = UUID.fromString(objectMapper
                .readTree(inkPurchaseResult.getResponse().getContentAsString())
                .get("paymentId")
                .asText());

        MvcResult result = mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value("store-test"))
                .andExpect(jsonPath("$.channelKey").value("channel-test"))
                .andExpect(jsonPath("$.orderName").value("읽어볼까 도서 소장"))
                .andExpect(jsonPath("$.totalAmount").value(BOOK_PRICE_WON))
                .andExpect(jsonPath("$.currency").value("CURRENCY_KRW"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(
                objectMapper.readTree(responseBody).get("paymentId").asText());

        assertAll(
                () -> assertNotEquals(inkPaymentId, paymentId),
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(BOOK_PRICE_WON, 결제_금액을_조회한다(paymentId)),
                () -> assertEquals(70, 잉크_잔액을_조회한다()),
                () -> assertEquals(4, 대여_수를_조회한다()),
                () -> assertEquals(4, 잉크_내역_수를_조회한다()),
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertTrue(!responseBody.contains("test-api-secret")),
                () -> assertTrue(!responseBody.contains("whsec_")));
    }

    @Test
    void 기존_PENDING은_같은_결제_정보를_200으로_재사용한다() throws Exception {
        String firstResponse = 결제를_준비한다(status().isCreated());
        String reusedResponse = 결제를_준비한다(status().isOk());

        assertAll(
                () -> assertEquals(firstResponse, reusedResponse),
                () -> assertEquals(1, 결제_수를_조회한다()));
    }

    @Test
    void 이미_소장한_도서는_409로_거부한다() throws Exception {
        소장_기록을_생성한다();

        mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_ALREADY_OWNED"));

        assertEquals(1, 결제_수를_조회한다());
    }

    @Test
    void 존재하지_않는_도서는_404로_거부한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID + 999)
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertEquals(0, 결제_수를_조회한다());
    }

    @Test
    void 유효하지_않은_도서_ID는_400_INVALID_INPUT으로_거부한다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/ownership-payments", 0)
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/books/{bookId}/ownership-payments", -1)
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertEquals(0, 결제_수를_조회한다());
    }

    @Test
    void 소장_결제_준비는_인증과_CSRF_토큰이_필요하다() throws Exception {
        mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID)
                        .with(authentication(인증된_독자())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        assertEquals(0, 결제_수를_조회한다());
    }

    private String 결제를_준비한다(
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID)
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(expectedStatus)
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private TestingAuthenticationToken 인증된_독자() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(READER_ID),
                null,
                "ROLE_USER");
    }

    private void 독자와_잉크_계좌를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum414-api@example.com', '{noop}password',
                        '2026-07-31 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 70)",
                READER_ID);
    }

    private void 도서와_전체_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '기술', '사라지지 않는 페이지', '읽어볼까', 4, ?)
                """,
                BOOK_ID,
                BOOK_PRICE_WON);
        for (int pageNumber = 1; pageNumber <= 4; pageNumber++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO book_page
                        (book_id, page_number, content_type, text_content)
                    VALUES (?, ?, 'TEXT', ?)
                    """,
                    BOOK_ID,
                    pageNumber,
                    pageNumber + "쪽");
        }
    }

    private void 모든_페이지를_대여한다() {
        int balanceAfter = 74;
        for (Long bookPageId : jdbcTemplate.queryForList(
                "SELECT id FROM book_page WHERE book_id = ? ORDER BY page_number",
                Long.class,
                BOOK_ID)) {
            balanceAfter--;
            jdbcTemplate.update(
                    """
                    INSERT INTO page_rental
                        (reader_id, book_page_id, rented_at, expires_at)
                    VALUES (?, ?, '2026-07-01 00:00:00.000000',
                            '2026-07-31 00:00:00.000000')
                    """,
                    READER_ID,
                    bookPageId);
            long pageRentalId = jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM page_rental
                    WHERE reader_id = ? AND book_page_id = ?
                    """,
                    Long.class,
                    READER_ID,
                    bookPageId);
            jdbcTemplate.update(
                    """
                    INSERT INTO ink_ledger
                        (reader_id, type, amount, balance_after, page_rental_id, occurred_at)
                    VALUES (?, 'DEDUCTION', 1, ?, ?, '2026-07-01 00:00:00.000000')
                    """,
                    READER_ID,
                    balanceAfter,
                    pageRentalId);
        }
    }

    private void 소장_기록을_생성한다() {
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', ?, '2026-07-31 00:00:00.000000',
                        '2026-07-31 00:01:00.000000')
                """,
                READER_ID,
                BOOK_ID,
                paymentId.toString(),
                BOOK_PRICE_WON);
        long ownershipPaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM ownership_payment WHERE payment_id = ?",
                Long.class,
                paymentId.toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-07-31 00:01:00.000000')
                """,
                READER_ID,
                BOOK_ID,
                ownershipPaymentId);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }

    private String 결제_상태를_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ownership_payment WHERE payment_id = ?",
                String.class,
                paymentId.toString());
    }

    private int 결제_금액을_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT amount_won FROM ownership_payment WHERE payment_id = ?",
                Integer.class,
                paymentId.toString());
    }

    private int 결제_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ownership_payment WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 잉크_잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 대여_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 잉크_내역_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 소장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book_ownership WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }
}
