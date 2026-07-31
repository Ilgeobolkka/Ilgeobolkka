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
import com.example.ilgeobolkka.ownership.facade.OwnershipPaymentFacade;
import com.example.ilgeobolkka.webhook.facade.PortOneWebhookFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.util.Base64;
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

/**
 * 웹훅 이벤트는 잉크 이용권·소장 결제 어느 paymentId인지 구분하는 필드가 없다.
 * {@link PortOneWebhookFacade}가 잉크 구매에 없으면 소장 결제로 넘기는 분기를 소장 결제
 * paymentId 기준으로 검증한다.
 */
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
@Import(OwnershipPaymentWebhookMySqlIntegrationTest.PaymentGatewayTestConfiguration.class)
class OwnershipPaymentWebhookMySqlIntegrationTest {

    private static final byte[] WEBHOOK_SECRET = "test-webhook-secret".getBytes(UTF_8);
    private static final long READER_ID = 415_004L;
    private static final long BOOK_ID = 415_104L;
    private static final int BOOK_PRICE_WON = 16_000;
    private static final Instant PAID_AT = Instant.parse("2026-08-01T04:00:00.123456Z");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OwnershipPaymentFacade ownershipPaymentFacade;
    private final PortOneWebhookFacade portOneWebhookFacade;
    private final FakePortOnePaymentGateway paymentGateway;

    @Autowired
    OwnershipPaymentWebhookMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            OwnershipPaymentFacade ownershipPaymentFacade,
            PortOneWebhookFacade portOneWebhookFacade,
            FakePortOnePaymentGateway paymentGateway) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.ownershipPaymentFacade = ownershipPaymentFacade;
        this.portOneWebhookFacade = portOneWebhookFacade;
        this.paymentGateway = paymentGateway;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_도서를_생성한다();
        paymentGateway.reset();
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void T_PAY_003_PAID_웹훅_뒤_브라우저_완료가_와도_소장은_한_번만_반영한다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));

        웹훅을_전송한다("Transaction.Paid", paymentId)
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        브라우저에서_완료한다(paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.owned").value(true));

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(1, 소장_수를_조회한다()),
                () -> assertEquals(1, paymentGateway.callCount()));
    }

    @Test
    void T_PAY_003_브라우저_완료_뒤_PAID_웹훅이_와도_소장은_한_번만_반영한다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));

        브라우저에서_완료한다(paymentId)
                .andExpect(status().isOk());
        웹훅을_전송한다("Transaction.Paid", paymentId)
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(1, 소장_수를_조회한다()),
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
                () -> assertEquals(0, 소장_수를_조회한다()),
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
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertEquals(0, paymentGateway.callCount()));
    }

    @Test
    void T_PAY_008_조회_장애는_503이고_미완료_조회는_200과_PENDING이다() throws Exception {
        UUID unavailablePaymentId = 결제를_준비한다();
        paymentGateway.failWithProviderError();

        웹훅을_전송한다("Transaction.Paid", unavailablePaymentId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(unavailablePaymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()));
    }

    @Test
    void 잉크_구매에도_소장_결제에도_없는_결제의_지원_웹훅은_404이다() throws Exception {
        UUID unknownPaymentId = UUID.randomUUID();
        paymentGateway.respondWith(결제(unknownPaymentId, PortOnePaymentStatus.PAID));

        웹훅을_전송한다("Transaction.Paid", unknownPaymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertAll(
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertEquals(0, paymentGateway.callCount()));
    }

    @Test
    void 완료와_웹훅을_동시에_10회_처리해도_소장_반영은_한_번이다() throws Exception {
        UUID paymentId = 결제를_준비한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID));
        SignedWebhook signed = 서명한다(웹훅_본문("Transaction.Paid", paymentId));

        동시에_완료한다(paymentId, signed);

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(1, 소장_수를_조회한다()));
    }

    private UUID 결제를_준비한다() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/books/{bookId}/ownership-payments", BOOK_ID)
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
        return mockMvc.perform(post("/api/ownership-payments/{paymentId}/complete", paymentId)
                .with(authentication(인증된_독자()))
                .with(csrf()));
    }

    private ResultActions 웹훅을_전송한다(String type, UUID paymentId) throws Exception {
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
            java.util.List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < concurrentRequestCount; index++) {
                int requestIndex = index;
                Callable<Void> action = () -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    if (requestIndex % 2 == 0) {
                        ownershipPaymentFacade.complete(READER_ID, paymentId);
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
        return """
                {
                  "type": "%s",
                  "timestamp": "2026-08-01T04:00:00Z",
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
                BOOK_PRICE_WON,
                "KRW",
                "store-test",
                "channel-test",
                "읽어볼까 도서 소장",
                "V2",
                status == PortOnePaymentStatus.PAID ? PAID_AT : null);
    }

    private TestingAuthenticationToken 인증된_독자() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(READER_ID),
                null,
                "ROLE_USER");
    }

    private void 독자와_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum415-webhook@example.com', '{noop}password',
                        '2026-08-01 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '과학', '웹훅으로 오는 소장', '읽어볼까', 1, ?)
                """,
                BOOK_ID,
                BOOK_PRICE_WON);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
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

    private int 소장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book_ownership WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
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
