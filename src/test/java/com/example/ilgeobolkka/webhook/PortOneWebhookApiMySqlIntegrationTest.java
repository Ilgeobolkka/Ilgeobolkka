package com.example.ilgeobolkka.webhook;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentUnavailableException;
import com.example.ilgeobolkka.ink.facade.InkPurchaseFacade;
import com.example.ilgeobolkka.webhook.facade.PortOneWebhookFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
import org.springframework.test.web.servlet.ResultActions;
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
@Import(PortOneWebhookApiMySqlIntegrationTest.PaymentGatewayTestConfiguration.class)
class PortOneWebhookApiMySqlIntegrationTest {

    private static final byte[] WEBHOOK_SECRET = "test-webhook-secret".getBytes(UTF_8);
    private static final long READER_ID = 409_001L;
    private static final Instant PAID_AT = Instant.parse("2026-07-30T03:00:00.123456Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final InkPurchaseFacade inkPurchaseFacade;
    private final PortOneWebhookFacade portOneWebhookFacade;
    private final FakePortOnePaymentGateway paymentGateway;

    @Autowired
    PortOneWebhookApiMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            InkPurchaseFacade inkPurchaseFacade,
            PortOneWebhookFacade portOneWebhookFacade,
            FakePortOnePaymentGateway paymentGateway) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.inkPurchaseFacade = inkPurchaseFacade;
        this.portOneWebhookFacade = portOneWebhookFacade;
        this.paymentGateway = paymentGateway;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        paymentGateway.reset();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @RepeatedTest(5)
    void T_PAY_003_PAID_웹훅_뒤_브라우저_완료가_와도_잉크는_한_번만_지급한다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));

        웹훅을_전송한다("Transaction.Paid", paymentId)
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        브라우저에서_완료한다(paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.inkBalance").value(100));

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(100, 잔액을_조회한다()),
                () -> assertEquals(1, 지급_원장_수를_조회한다(paymentId)),
                () -> assertEquals(1, paymentGateway.callCount()));
    }

    @RepeatedTest(5)
    void T_PAY_003_브라우저_완료_뒤_PAID_웹훅이_와도_잉크는_한_번만_지급한다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));

        브라우저에서_완료한다(paymentId)
                .andExpect(status().isOk());
        웹훅을_전송한다("Transaction.Paid", paymentId)
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(100, 잔액을_조회한다()),
                () -> assertEquals(1, 지급_원장_수를_조회한다(paymentId)),
                () -> assertEquals(2, paymentGateway.callCount()));
    }

    @Test
    void T_PAY_007_FAILED_웹훅은_재조회로_FAILED를_확정하고_중복에도_200이다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.FAILED));

        웹훅을_전송한다("Transaction.Failed", paymentId)
                .andExpect(status().isOk());
        웹훅을_전송한다("Transaction.Failed", paymentId)
                .andExpect(status().isOk());

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)),
                () -> assertEquals(2, paymentGateway.callCount()));
    }

    @Test
    void T_PAY_007_정상_서명의_미지원_이벤트는_상태를_바꾸지_않고_200이다() throws Exception {
        UUID paymentId = 결제를_준비한다();

        웹훅을_전송한다("Transaction.PayPending", paymentId)
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)),
                () -> assertEquals(0, paymentGateway.callCount()));
    }

    @Test
    void 정상_서명의_비_UUID_paymentId_웹훅은_부수_효과_없이_200이다() throws Exception {
        웹훅을_전송한다("Transaction.Paid", "example-payment-id")
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertAll(
                () -> assertEquals(0, paymentGateway.callCount()),
                () -> assertEquals(0, 잉크_구매_수를_조회한다()),
                () -> assertEquals(0, 소장_결제_수를_조회한다()),
                () -> assertEquals(0, 잉크_원장_수를_조회한다()),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertEquals(0, 서재_항목_수를_조회한다()));
    }

    @Test
    void T_PAY_008_서명_누락과_불일치는_400이고_비밀과_상태를_노출하지_않는다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        String body = 웹훅_본문("Transaction.Paid", paymentId);
        SignedWebhook signed = 서명한다(body);

        MvcResult missing = mockMvc.perform(post("/api/webhooks/portone")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"))
                .andReturn();
        MvcResult mismatched = mockMvc.perform(post("/api/webhooks/portone")
                        .contentType(APPLICATION_JSON)
                        .header("webhook-id", signed.id())
                        .header("webhook-signature", "v1,aW52YWxpZA==")
                        .header("webhook-timestamp", signed.timestamp())
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"))
                .andReturn();

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, paymentGateway.callCount()),
                () -> assertTrue(!missing.getResponse()
                        .getContentAsString()
                        .contains("test-webhook-secret")),
                () -> assertTrue(!mismatched.getResponse()
                        .getContentAsString()
                        .contains("test-webhook-secret")));
    }

    @Test
    void T_PAY_008_조회_장애는_503이고_미완료_조회는_200과_PENDING이다() throws Exception {
        UUID unavailablePaymentId = 결제를_준비한다();
        paymentGateway.failWithProviderError();

        웹훅을_전송한다("Transaction.Paid", unavailablePaymentId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        UUID pendingPaymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(pendingPaymentId, PortOnePaymentStatus.PENDING));
        웹훅을_전송한다("Transaction.Paid", pendingPaymentId)
                .andExpect(status().isOk());

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(unavailablePaymentId)),
                () -> assertEquals("PENDING", 결제_상태를_조회한다(pendingPaymentId)),
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, 지급_원장_수를_조회한다(unavailablePaymentId)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(pendingPaymentId)));
    }

    @Test
    void 준비되지_않은_결제의_지원_웹훅은_404이다() throws Exception {
        UUID unknownPaymentId = UUID.randomUUID();
        paymentGateway.respondWith(결제(unknownPaymentId, PortOnePaymentStatus.PAID));

        웹훅을_전송한다("Transaction.Paid", unknownPaymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 잉크 구매·소장 결제 어디에도 내부 기록이 없으면 존재 확인만으로 404를 판정하고
        // PortOne을 조회하지 않는다 (SCRUM-415에서 소장 결제 라우팅을 추가하며 도입).
        assertAll(
                () -> assertEquals(0, 잔액을_조회한다()),
                () -> assertEquals(0, paymentGateway.callCount()));
    }

    @Test
    void 완료와_웹훅을_동시에_10회_처리해도_지급_효과는_한_번이다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));
        SignedWebhook signed =
                서명한다(웹훅_본문("Transaction.Paid", paymentId));

        동시에_완료한다(paymentId, signed);

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(100, 잔액을_조회한다()),
                () -> assertEquals(1, 지급_원장_수를_조회한다(paymentId)));
    }

    @Test
    void 웹훅_지급이_실패하면_PAID_전이도_함께_롤백한다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));

        웹훅을_전송한다("Transaction.Paid", paymentId)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 지급_원장_수를_조회한다(paymentId)));
    }

    private UUID 결제를_준비한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ink/purchases")
                        .with(authentication(인증된_독자()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("paymentId")
                .asText());
    }

    private ResultActions 브라우저에서_완료한다(UUID paymentId) throws Exception {
        return mockMvc.perform(post("/api/ink/purchases/{paymentId}/complete", paymentId)
                .with(authentication(인증된_독자()))
                .with(csrf()));
    }

    private ResultActions 웹훅을_전송한다(String type, UUID paymentId) throws Exception {
        return 웹훅을_전송한다(type, paymentId.toString());
    }

    private ResultActions 웹훅을_전송한다(String type, String paymentId) throws Exception {
        SignedWebhook signed = 서명한다(웹훅_본문(type, paymentId));
        return mockMvc.perform(post("/api/webhooks/portone")
                .contentType(APPLICATION_JSON)
                .header("webhook-id", signed.id())
                .header("webhook-signature", signed.signature())
                .header("webhook-timestamp", signed.timestamp())
                .content(signed.body()));
    }

    private void 동시에_완료한다(UUID paymentId, SignedWebhook signed) throws Exception {
        int concurrentRequestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequestCount);
        CountDownLatch ready = new CountDownLatch(concurrentRequestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int index = 0; index < concurrentRequestCount; index++) {
                int requestIndex = index;
                Callable<Void> action = () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    if (requestIndex % 2 == 0) {
                        inkPurchaseFacade.complete(READER_ID, paymentId);
                    } else {
                        portOneWebhookFacade.handle(
                                signed.body(),
                                signed.id(),
                                signed.signature(),
                                signed.timestamp());
                    }
                    return null;
                };
                futures.add(executor.submit(action));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private SignedWebhook 서명한다(String body) throws Exception {
        String id = "webhook-test-" + UUID.randomUUID();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET, "HmacSHA256"));
        byte[] signature = mac.doFinal((id + "." + timestamp + "." + body).getBytes(UTF_8));
        return new SignedWebhook(
                body,
                id,
                "v1," + Base64.getEncoder().encodeToString(signature),
                timestamp);
    }

    private String 웹훅_본문(String type, UUID paymentId) {
        return 웹훅_본문(type, paymentId.toString());
    }

    private String 웹훅_본문(String type, String paymentId) {
        return """
                {
                  "type": "%s",
                  "timestamp": "2026-07-30T03:00:00Z",
                  "data": {
                    "storeId": "store-test",
                    "paymentId": "%s",
                    "transactionId": "transaction-test"
                  }
                }
                """.formatted(type, paymentId);
    }

    private PortOnePayment 결제(UUID paymentId, PortOnePaymentStatus status) {
        return new PortOnePayment(
                paymentId.toString(),
                status,
                1_000,
                "KRW",
                "store-test",
                "channel-test",
                "읽어볼까 100잉크",
                "V2",
                status == PortOnePaymentStatus.PAID ? PAID_AT : null);
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
                VALUES (?, ?, '{noop}password', '2026-07-30 00:00:00.000000')
                """,
                READER_ID,
                "scrum409@example.com");
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                READER_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update(
                "DELETE FROM ink_ledger WHERE reader_id = ?",
                READER_ID);
        jdbcTemplate.update(
                "DELETE FROM ink_purchase WHERE reader_id = ?",
                READER_ID);
        jdbcTemplate.update(
                "DELETE FROM ink_account WHERE reader_id = ?",
                READER_ID);
        jdbcTemplate.update(
                "DELETE FROM reader WHERE id = ?",
                READER_ID);
    }

    private int 잉크_구매_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_purchase WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 소장_결제_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ownership_payment WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 잉크_원장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 소장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book_ownership WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 서재_항목_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private String 결제_상태를_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM ink_purchase WHERE payment_id = ?",
                String.class,
                paymentId.toString());
    }

    private int 잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                READER_ID);
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

    private record SignedWebhook(
            String body,
            String id,
            String signature,
            String timestamp) {
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
