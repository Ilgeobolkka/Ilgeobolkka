package com.example.ilgeobolkka.ink;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentUnavailableException;
import com.example.ilgeobolkka.ink.dto.CompleteInkPurchaseResponse;
import com.example.ilgeobolkka.ink.facade.InkPurchaseFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
@Import(InkPurchaseApiMySqlIntegrationTest.PaymentGatewayTestConfiguration.class)
class InkPurchaseApiMySqlIntegrationTest {

    private static final long READER_ID = 408_001L;
    private static final long OTHER_READER_ID = 408_002L;
    private static final Instant PAID_AT = Instant.parse("2026-07-30T03:00:00.123456Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final InkPurchaseFacade inkPurchaseFacade;
    private final FakePortOnePaymentGateway paymentGateway;

    @Autowired
    InkPurchaseApiMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            InkPurchaseFacade inkPurchaseFacade,
            FakePortOnePaymentGateway paymentGateway) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.inkPurchaseFacade = inkPurchaseFacade;
        this.paymentGateway = paymentGateway;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다(READER_ID);
        독자와_잉크_계좌를_생성한다(OTHER_READER_ID);
        paymentGateway.reset();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_PAY_001_잉크_구매를_준비하면_서버가_PENDING_시도와_공개_결제값을_만든다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ink/purchases")
                        .with(authentication(인증된_독자(READER_ID)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value("store-test"))
                .andExpect(jsonPath("$.channelKey").value("channel-test"))
                .andExpect(jsonPath("$.orderName").value("읽어볼까 100잉크"))
                .andExpect(jsonPath("$.totalAmount").value(1_000))
                .andExpect(jsonPath("$.currency").value("CURRENCY_KRW"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(
                objectMapper.readTree(responseBody).get("paymentId").asText());

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(1_000, 결제_금액을_조회한다(paymentId)),
                () -> assertEquals(100, 지급_잉크를_조회한다(paymentId)),
                () -> assertTrue(!responseBody.contains("test-api-secret")),
                () -> assertTrue(!responseBody.contains("test-webhook-secret")));
    }

    @Test
    void T_PAY_002_PAID_완료와_재시도는_잉크를_정확히_한_번만_지급한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, 1_000));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.grantedInk").value(100))
                .andExpect(jsonPath("$.inkBalance").value(100));

        paymentGateway.failWithProviderError();
        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.inkBalance").value(100));

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals("2026-07-30 03:00:00.123456", 결제_완료_시각을_조회한다(paymentId)),
                () -> assertEquals(100, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(1, 지급_원장_수를_조회한다(paymentId)),
                () -> assertEquals(1, paymentGateway.callCount()));
    }

    @Test
    void 아직_완료되지_않은_결제는_PENDING과_현재_잔액을_반환한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PENDING, 1_000));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.grantedInk").value(0))
                .andExpect(jsonPath("$.inkBalance").value(0));

        paymentGateway.respondWith(PortOnePayment.notFound(paymentId.toString()));
        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void READY_결제의_채널이_아직_없으면_PENDING을_유지한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(new PortOnePayment(
                paymentId.toString(),
                PortOnePaymentStatus.PENDING,
                1_000,
                "KRW",
                "store-test",
                null,
                "읽어볼까 100잉크",
                "V2",
                null));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void T_PAY_004_금액이_다르면_FAILED로_기록하고_잉크를_지급하지_않는다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, 999));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_AMOUNT"));

        int callCount = paymentGateway.callCount();
        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_CONFLICT"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)),
                () -> assertEquals(callCount, paymentGateway.callCount()));
    }

    @Test
    void 결제_금액이_1원_초과해도_FAILED로_기록한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, 1_001));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_AMOUNT"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void 결제_통화가_KRW가_아니면_FAILED로_기록한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        PortOnePayment payment = 결제(paymentId, PortOnePaymentStatus.PAID, 1_000);
        paymentGateway.respondWith(new PortOnePayment(
                payment.paymentId(),
                payment.status(),
                payment.totalAmount(),
                "USD",
                payment.storeId(),
                payment.channelKey(),
                payment.orderName(),
                payment.version(),
                payment.paidAt()));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void 식별자_불일치와_최종_실패는_검증_실패로_기록한다() throws Exception {
        UUID mismatchedPaymentId = 결제를_준비한다(READER_ID);
        PortOnePayment mismatched = new PortOnePayment(
                UUID.randomUUID().toString(),
                PortOnePaymentStatus.PAID,
                1_000,
                "KRW",
                "store-test",
                "channel-test",
                "읽어볼까 100잉크",
                "V2",
                PAID_AT);
        paymentGateway.respondWith(mismatched);

        결제를_완료한다(READER_ID, mismatchedPaymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        UUID failedPaymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(failedPaymentId, PortOnePaymentStatus.FAILED, 1_000));

        결제를_완료한다(READER_ID, failedPaymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(mismatchedPaymentId)),
                () -> assertEquals("FAILED", 결제_상태를_조회한다(failedPaymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)));
    }

    @Test
    void 실패한_결제_뒤_다시_준비하면_이전_시도를_보존하고_새_결제를_만든다() throws Exception {
        UUID failedPaymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(failedPaymentId, PortOnePaymentStatus.FAILED, 1_000));
        결제를_완료한다(READER_ID, failedPaymentId)
                .andExpect(status().isUnprocessableContent());

        UUID retriedPaymentId = 결제를_준비한다(READER_ID);

        assertAll(
                () -> assertTrue(!failedPaymentId.equals(retriedPaymentId)),
                () -> assertEquals("FAILED", 결제_상태를_조회한다(failedPaymentId)),
                () -> assertEquals("PENDING", 결제_상태를_조회한다(retriedPaymentId)),
                () -> assertEquals(2, 결제_수를_조회한다()));
    }

    @Test
    void PortOne_조회_장애는_503이고_PENDING을_유지한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.failWithProviderError();

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void 다른_독자나_존재하지_않는_결제는_404이다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);

        결제를_완료한다(OTHER_READER_ID, paymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        결제를_완료한다(READER_ID, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertEquals(0, paymentGateway.callCount());
    }

    @Test
    void 잉크_지급이_실패하면_PAID_전이도_함께_롤백한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, 1_000));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void 동시에_완료해도_구매와_지급은_한_번만_반영한다() throws Exception {
        UUID paymentId = 결제를_준비한다(READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, 1_000));

        List<CompleteInkPurchaseResponse> responses = 동시에_완료한다(paymentId);

        assertAll(
                () -> assertTrue(responses.stream()
                        .allMatch(response -> response.status().name().equals("PAID"))),
                () -> assertTrue(responses.stream()
                        .allMatch(response -> response.inkBalance() == 100)),
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(100, 잔액을_조회한다(READER_ID)),
                () -> assertEquals(1, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void 구매_준비는_인증과_CSRF_토큰이_필요하다() throws Exception {
        mockMvc.perform(post("/api/ink/purchases").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/ink/purchases")
                        .with(authentication(인증된_독자(READER_ID))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        assertEquals(0, 결제_수를_조회한다());
    }

    private UUID 결제를_준비한다(long readerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ink/purchases")
                        .with(authentication(인증된_독자(readerId)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("paymentId")
                .asText());
    }

    private org.springframework.test.web.servlet.ResultActions 결제를_완료한다(
            long readerId,
            UUID paymentId) throws Exception {
        return mockMvc.perform(post("/api/ink/purchases/{paymentId}/complete", paymentId)
                .with(authentication(인증된_독자(readerId)))
                .with(csrf()));
    }

    private List<CompleteInkPurchaseResponse> 동시에_완료한다(UUID paymentId) throws Exception {
        int concurrentRequestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequestCount);
        CountDownLatch ready = new CountDownLatch(concurrentRequestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<CompleteInkPurchaseResponse> action = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return inkPurchaseFacade.complete(READER_ID, paymentId);
            };
            List<Future<CompleteInkPurchaseResponse>> futures = new ArrayList<>();
            for (int index = 0; index < concurrentRequestCount; index++) {
                futures.add(executor.submit(action));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<CompleteInkPurchaseResponse> responses = new ArrayList<>();
            for (Future<CompleteInkPurchaseResponse> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private PortOnePayment 결제(
            UUID paymentId,
            PortOnePaymentStatus status,
            long totalAmount) {
        return new PortOnePayment(
                paymentId.toString(),
                status,
                totalAmount,
                "KRW",
                "store-test",
                "channel-test",
                "읽어볼까 100잉크",
                "V2",
                status == PortOnePaymentStatus.PAID ? PAID_AT : null);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId),
                null,
                "ROLE_USER");
    }

    private void 독자와_잉크_계좌를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                readerId,
                "scrum408-" + readerId + "@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                readerId);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update(
                "DELETE FROM ink_ledger WHERE reader_id IN (?, ?)",
                READER_ID,
                OTHER_READER_ID);
        jdbcTemplate.update(
                "DELETE FROM ink_purchase WHERE reader_id IN (?, ?)",
                READER_ID,
                OTHER_READER_ID);
        jdbcTemplate.update(
                "DELETE FROM ink_account WHERE reader_id IN (?, ?)",
                READER_ID,
                OTHER_READER_ID);
        jdbcTemplate.update(
                "DELETE FROM reader WHERE id IN (?, ?)",
                READER_ID,
                OTHER_READER_ID);
    }

    private String 결제_상태를_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ink_purchase WHERE payment_id = ?",
                String.class,
                paymentId.toString());
    }

    private String 결제_완료_시각을_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(paid_at, '%Y-%m-%d %H:%i:%s.%f') FROM ink_purchase WHERE payment_id = ?",
                String.class,
                paymentId.toString());
    }

    private int 결제_금액을_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT amount_won FROM ink_purchase WHERE payment_id = ?",
                Integer.class,
                paymentId.toString());
    }

    private int 지급_잉크를_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT granted_ink FROM ink_purchase WHERE payment_id = ?",
                Integer.class,
                paymentId.toString());
    }

    private int 잔액을_조회한다(long readerId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                readerId);
    }

    private int 지급_원장_수를_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ink_ledger ledger
                JOIN ink_purchase purchase ON purchase.id = ledger.ink_purchase_id
                WHERE purchase.payment_id = ?
                """,
                Integer.class,
                paymentId.toString());
    }

    private int 결제_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_purchase WHERE reader_id IN (?, ?)",
                Integer.class,
                READER_ID,
                OTHER_READER_ID);
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
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public PortOnePayment getPayment(String paymentId) {
            callCount.incrementAndGet();
            return response.get().apply(paymentId);
        }

        void reset() {
            callCount.set(0);
            response.set(PortOnePayment::notFound);
        }

        void respondWith(PortOnePayment payment) {
            response.set(ignored -> payment);
        }

        void failWithProviderError() {
            response.set(ignored -> {
                throw new PortOnePaymentUnavailableException();
            });
        }

        int callCount() {
            return callCount.get();
        }
    }
}
