package com.example.ilgeobolkka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 가입부터 서재 확인까지 핵심 사용자 여정을 실제 HTTP 세션·CSRF·DB 상태로 한 번에 검증한다
 * (SCRUM-420). 실제 브라우저 대신 MockMvc로 same-origin 화면·API 요청을 순서대로 재현하며,
 * PortOne만 결정적 fake로 대체한다.
 *
 * <p>클래스 단위 {@code @Transactional}로 묶지 않는다. 그러면 여정 전체가 하나의 영속성
 * 컨텍스트를 공유해, 뷰어 세션처럼 네이티브 upsert로 갱신하는 행을 이후 JPA 조회가 갱신 전
 * 캐시된 엔티티로 돌려줘 실제로는 없는 충돌(409)이 발생한다. 각 요청이 실제 운영처럼 독립적으로
 * 커밋되어야 서로 다른 요청 사이의 상태 변화가 정확히 보인다.
 */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "portone.payment.enabled=true",
    "portone.payment.store-id=store-test",
    "portone.payment.channel-key=channel-test",
    "portone.payment.api-secret=test-api-secret",
    "portone.payment.webhook-secret=whsec_dGVzdC13ZWJob29rLXNlY3JldA=="
})
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(CoreUserJourneyMySqlIntegrationTest.PaymentGatewayTestConfiguration.class)
class CoreUserJourneyMySqlIntegrationTest {

    private static final String EMAIL = "scrum420.journey@example.com";
    private static final String PASSWORD = "Journey-pass1!";

    private static final long SCIENCE_BOOK_ID = 420_101L;
    private static final long RENTAL_BOOK_ID = 420_102L;
    private static final long OWNED_BOOK_ID = 420_103L;
    private static final int RENTAL_BOOK_PRICE = 15_000;
    private static final int OWNED_BOOK_PRICE = 20_000;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FakePortOnePaymentGateway paymentGateway;

    @Autowired
    CoreUserJourneyMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            FakePortOnePaymentGateway paymentGateway) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.paymentGateway = paymentGateway;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        도서_3권을_생성한다();
        paymentGateway.reset();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void 가입부터_서재_확인까지_핵심_여정을_한_흐름으로_검증한다() throws Exception {
        MockHttpSession anonymousSession = new MockHttpSession();
        CsrfToken anonymousCsrf = CSRF_토큰을_읽는다(anonymousSession, "/signup");

        // 1. 가입 — 계정만 생성되고 로그인 세션은 만들지 않는다 (AUTH-001).
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .session(anonymousSession)
                        .header(anonymousCsrf.getHeaderName(), anonymousCsrf.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andReturn();
        long readerId = 응답_필드_LONG을_읽는다(signupResult, "readerId");

        mockMvc.perform(get("/api/library").session(anonymousSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        // 2. 별도 로그인 — 세션 ID와 CSRF 토큰이 모두 교체된다 (AUTH-004, T-AUTH-008 회귀).
        String sessionIdBeforeLogin = anonymousSession.getId();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(anonymousSession)
                        .header(anonymousCsrf.getHeaderName(), anonymousCsrf.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readerId").value(readerId))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        assertNotEquals(sessionIdBeforeLogin, session.getId());

        CsrfToken csrf = CSRF_토큰을_읽는다(session, "/ink");
        assertNotEquals(anonymousCsrf.getToken(), csrf.getToken());

        // CSRF 토큰 없는 상태 변경 요청은 거부된다.
        mockMvc.perform(post("/api/ink/purchases").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        // 3. 목록/검색 — 카테고리 우선 정렬과 키워드 검색 (CAT-001, T-CAT-001).
        mockMvc.perform(get("/api/books").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].bookId").value(SCIENCE_BOOK_ID))
                .andExpect(jsonPath("$.books[1].bookId").value(RENTAL_BOOK_ID))
                .andExpect(jsonPath("$.books[2].bookId").value(OWNED_BOOK_ID))
                .andExpect(jsonPath("$.totalCount").value(3));
        mockMvc.perform(get("/api/books")
                        .param("page", "1")
                        .param("keyword", "소설의 첫")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.books[0].bookId").value(RENTAL_BOOK_ID));

        // 4. 잉크 충전 — PENDING → PAID 순서로 100잉크를 지급받는다 (INK-002, T-INK-002).
        MvcResult inkPrepareResult = mockMvc.perform(post("/api/ink/purchases")
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID inkPaymentId = 응답_필드_UUID를_읽는다(inkPrepareResult, "paymentId");

        paymentGateway.respondWith(portOnePayment(
                inkPaymentId, PortOnePaymentStatus.PENDING, 1_000, "읽어볼까 100잉크", null));
        mockMvc.perform(post("/api/ink/purchases/{paymentId}/complete", inkPaymentId)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals("PENDING", 결제_상태를_조회한다("ink_purchase", inkPaymentId));

        paymentGateway.respondWith(portOnePayment(
                inkPaymentId, PortOnePaymentStatus.PAID, 1_000, "읽어볼까 100잉크", Instant.now()));
        mockMvc.perform(post("/api/ink/purchases/{paymentId}/complete", inkPaymentId)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.grantedInk").value(100))
                .andExpect(jsonPath("$.inkBalance").value(100));
        assertEquals("PAID", 결제_상태를_조회한다("ink_purchase", inkPaymentId));

        mockMvc.perform(get("/api/ink/balance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
        mockMvc.perform(get("/api/ink/ledger").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].type").value("GRANT"))
                .andExpect(jsonPath("$.entries[0].amount").value(100));

        // 5. 페이지 대여 — 새 대여는 1잉크를 차감한다 (RENT-001, T-RENT-001).
        MvcResult rentalOpenResult = mockMvc.perform(post(
                        "/api/books/{bookId}/reading-sessions", RENTAL_BOOK_ID)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owned").value(false))
                .andExpect(jsonPath("$.deductedInk").value(1))
                .andExpect(jsonPath("$.inkBalance").value(99))
                .andExpect(jsonPath("$.contentType").value("TEXT"))
                .andReturn();
        UUID viewerSessionId = 응답_필드_UUID를_읽는다(rentalOpenResult, "viewerSessionId");

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 1)
                        .session(session)
                        .header("X-Viewer-Session-Id", viewerSessionId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("소설 1쪽"));

        // 6. 활성 재열람 — 새 페이지는 차감하고 같은 페이지 재열람은 차감하지 않는다 (RENT-002).
        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken())
                        .header("X-Viewer-Session-Id", viewerSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deductedInk").value(1))
                .andExpect(jsonPath("$.inkBalance").value(98));

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken())
                        .header("X-Viewer-Session-Id", viewerSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deductedInk").value(0))
                .andExpect(jsonPath("$.inkBalance").value(98));

        // 7. 도서 소장 — 검증 실패(FAILED) 뒤 재시도한 결제만 소장을 만든다 (OWN-002·003, T-OWN-002·003).
        MvcResult ownPrepareFirst = mockMvc.perform(post(
                        "/api/books/{bookId}/ownership-payments", OWNED_BOOK_ID)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(OWNED_BOOK_PRICE))
                .andReturn();
        UUID firstOwnPaymentId = 응답_필드_UUID를_읽는다(ownPrepareFirst, "paymentId");

        paymentGateway.respondWith(portOnePayment(
                firstOwnPaymentId,
                PortOnePaymentStatus.PAID,
                OWNED_BOOK_PRICE - 1,
                "읽어볼까 도서 소장",
                Instant.now()));
        mockMvc.perform(post("/api/ownership-payments/{paymentId}/complete", firstOwnPaymentId)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_AMOUNT"));
        assertEquals("FAILED", 결제_상태를_조회한다("ownership_payment", firstOwnPaymentId));

        MvcResult ownPrepareRetry = mockMvc.perform(post(
                        "/api/books/{bookId}/ownership-payments", OWNED_BOOK_ID)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID retriedOwnPaymentId = 응답_필드_UUID를_읽는다(ownPrepareRetry, "paymentId");
        assertNotEquals(firstOwnPaymentId, retriedOwnPaymentId);

        paymentGateway.respondWith(portOnePayment(
                retriedOwnPaymentId,
                PortOnePaymentStatus.PAID,
                OWNED_BOOK_PRICE,
                "읽어볼까 도서 소장",
                Instant.now()));
        mockMvc.perform(post("/api/ownership-payments/{paymentId}/complete", retriedOwnPaymentId)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.owned").value(true));

        mockMvc.perform(get("/api/books/{bookId}", OWNED_BOOK_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(true));

        // 소장 결제는 잉크 잔액·내역을 바꾸지 않는다 (OWN-003, INV-011).
        mockMvc.perform(get("/api/ink/balance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(98));

        // 8. 전 페이지 접근 — 소장 도서는 잉크 차감 없이 모든 페이지를 연다 (OWN-004, T-OWN-007).
        MvcResult ownedOpenResult = mockMvc.perform(post(
                        "/api/books/{bookId}/reading-sessions", OWNED_BOOK_ID)
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.deductedInk").value(0))
                .andReturn();
        UUID ownedViewerSessionId = 응답_필드_UUID를_읽는다(ownedOpenResult, "viewerSessionId");

        mockMvc.perform(patch("/api/reading-sessions/current/page")
                        .session(session)
                        .header(csrf.getHeaderName(), csrf.getToken())
                        .header("X-Viewer-Session-Id", ownedViewerSessionId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageNumberJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.deductedInk").value(0));

        mockMvc.perform(get("/api/reading-sessions/current/pages/{pageNumber}/content", 2)
                        .session(session)
                        .header("X-Viewer-Session-Id", ownedViewerSessionId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("역사 2쪽"));

        mockMvc.perform(get("/api/ink/balance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(98));

        // 9. 서재 확인 — 대여 도서와 소장 도서가 각자 마지막 위치로 표시된다 (LIB-001).
        MvcResult libraryResult = mockMvc.perform(get("/api/library").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andReturn();
        JsonNode entries = 응답_JSON을_읽는다(libraryResult).get("entries");
        JsonNode rentalEntry = 서재_항목을_찾는다(entries, RENTAL_BOOK_ID);
        assertEquals(1, rentalEntry.get("lastPageNumber").asInt());
        assertTrue(rentalEntry.get("activeRental").asBoolean());
        assertFalse(rentalEntry.get("owned").asBoolean());
        JsonNode ownedEntry = 서재_항목을_찾는다(entries, OWNED_BOOK_ID);
        assertEquals(2, ownedEntry.get("lastPageNumber").asInt());
        assertTrue(ownedEntry.get("owned").asBoolean());
        assertTrue(ownedEntry.get("rentedAt").isNull());

        // 10. 내역 확인 — 잉크 내역과 소장 결제 내역이 분리되어 제공된다 (INK-005, OWN-005).
        mockMvc.perform(get("/api/ink/ledger").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));
        mockMvc.perform(get("/api/ownership-payments").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].paymentId").value(retriedOwnPaymentId.toString()))
                .andExpect(jsonPath("$.payments[0].bookId").value(OWNED_BOOK_ID))
                .andExpect(jsonPath("$.payments[0].owned").value(true));

        assertEquals(2, 정수를_조회한다(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ?", readerId));
        assertEquals(1, 정수를_조회한다(
                "SELECT COUNT(*) FROM book_ownership WHERE reader_id = ? AND book_id = ?",
                readerId,
                OWNED_BOOK_ID));
        assertEquals(2, 정수를_조회한다(
                "SELECT COUNT(*) FROM page_rental WHERE reader_id = ? AND book_page_id IN "
                        + "(SELECT id FROM book_page WHERE book_id = ?)",
                readerId,
                RENTAL_BOOK_ID));
        assertEquals(2, 정수를_조회한다(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ? AND type = 'DEDUCTION'",
                readerId));
    }

    private CsrfToken CSRF_토큰을_읽는다(MockHttpSession session, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
    }

    private long 응답_필드_LONG을_읽는다(MvcResult result, String field) throws Exception {
        return 응답_JSON을_읽는다(result).get(field).asLong();
    }

    private UUID 응답_필드_UUID를_읽는다(MvcResult result, String field) throws Exception {
        return UUID.fromString(응답_JSON을_읽는다(result).get(field).asText());
    }

    private JsonNode 응답_JSON을_읽는다(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode 서재_항목을_찾는다(JsonNode entries, long bookId) {
        for (JsonNode entry : entries) {
            if (entry.get("bookId").asLong() == bookId) {
                return entry;
            }
        }
        throw new AssertionError("서재에서 bookId=" + bookId + " 항목을 찾지 못했습니다: " + entries);
    }

    private String authJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String pageNumberJson(int pageNumber) {
        return "{\"pageNumber\":" + pageNumber + "}";
    }

    private PortOnePayment portOnePayment(
            UUID paymentId,
            PortOnePaymentStatus status,
            long totalAmount,
            String orderName,
            Instant paidAt) {
        return new PortOnePayment(
                paymentId.toString(),
                status,
                totalAmount,
                "KRW",
                "store-test",
                "channel-test",
                orderName,
                "V2",
                paidAt);
    }

    private String 결제_상태를_조회한다(String table, UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM " + table + " WHERE payment_id = ?",
                String.class,
                paymentId.toString());
    }

    private int 정수를_조회한다(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update(
                "DELETE FROM library_entry WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM reading_session WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM ink_ledger WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM page_rental WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM book_ownership WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM ownership_payment WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM ink_purchase WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update(
                "DELETE FROM ink_account WHERE reader_id = (SELECT id FROM reader WHERE email = ?)",
                EMAIL);
        jdbcTemplate.update("DELETE FROM reader WHERE email = ?", EMAIL);
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id IN (?, ?, ?)",
                SCIENCE_BOOK_ID,
                RENTAL_BOOK_ID,
                OWNED_BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM book WHERE id IN (?, ?, ?)",
                SCIENCE_BOOK_ID,
                RENTAL_BOOK_ID,
                OWNED_BOOK_ID);
    }

    /** 카테고리는 "A"·"B"·"C"로 고정해 목록 정렬(category ASC)을 확정적으로 만든다. */
    private void 도서_3권을_생성한다() {
        도서를_생성한다(SCIENCE_BOOK_ID, "A", "탐험 안내서", 12_000);
        도서를_생성한다(RENTAL_BOOK_ID, "B", "소설의 첫 페이지", RENTAL_BOOK_PRICE);
        페이지를_생성한다(RENTAL_BOOK_ID, 1, "소설 1쪽");
        페이지를_생성한다(RENTAL_BOOK_ID, 2, "소설 2쪽");
        도서를_생성한다(OWNED_BOOK_ID, "C", "역사 여행기", OWNED_BOOK_PRICE);
        페이지를_생성한다(OWNED_BOOK_ID, 1, "역사 1쪽");
        페이지를_생성한다(OWNED_BOOK_ID, 2, "역사 2쪽");
    }

    private void 도서를_생성한다(long bookId, String category, String title, int priceWon) {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, ?, ?, '읽어볼까', 2, ?)
                """,
                bookId,
                category,
                title,
                priceWon);
    }

    private void 페이지를_생성한다(long bookId, int pageNumber, String text) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page (book_id, page_number, content_type, text_content)
                VALUES (?, ?, 'TEXT', ?)
                """,
                bookId,
                pageNumber,
                text);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentGatewayTestConfiguration {

        @Bean
        @Primary
        FakePortOnePaymentGateway fakePortOnePaymentGateway() {
            return new FakePortOnePaymentGateway();
        }
    }

    static final class FakePortOnePaymentGateway implements PortOnePaymentGateway {

        private final AtomicReference<Function<String, PortOnePayment>> response =
                new AtomicReference<>();

        @Override
        public PortOnePayment getPayment(String paymentId) {
            return response.get().apply(paymentId);
        }

        void reset() {
            response.set(PortOnePayment::notFound);
        }

        void respondWith(PortOnePayment payment) {
            response.set(ignored -> payment);
        }
    }
}
