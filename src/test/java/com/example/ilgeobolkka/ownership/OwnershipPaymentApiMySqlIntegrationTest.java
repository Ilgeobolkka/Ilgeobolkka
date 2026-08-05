package com.example.ilgeobolkka.ownership;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentUnavailableException;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.dto.CompleteOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.facade.OwnershipPaymentFacade;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@Import(OwnershipPaymentApiMySqlIntegrationTest.PaymentGatewayTestConfiguration.class)
class OwnershipPaymentApiMySqlIntegrationTest {

    private static final long READER_ID = 414_002L;
    private static final long OTHER_READER_ID = 415_003L;
    private static final long BOOK_ID = 414_102L;
    private static final long HISTORY_BOOK_ID_START = 415_200L;
    private static final int BOOK_PRICE_WON = 18_000;
    private static final Instant PAID_AT = Instant.parse("2026-08-01T03:00:00.123456Z");
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OwnershipPaymentFacade ownershipPaymentFacade;
    private final ReadingFacade readingFacade;
    private final FakePortOnePaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private InkService inkService;

    @Autowired
    OwnershipPaymentApiMySqlIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            OwnershipPaymentFacade ownershipPaymentFacade,
            ReadingFacade readingFacade,
            FakePortOnePaymentGateway paymentGateway,
            PlatformTransactionManager transactionManager) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.ownershipPaymentFacade = ownershipPaymentFacade;
        this.readingFacade = readingFacade;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        독자와_잉크_계좌를_생성한다();
        도서와_전체_페이지를_생성한다();
        paymentGateway.reset();
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

    @Test
    void T_OWN_002_PAID_완료와_재시도는_소장을_정확히_한_번만_생성한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.bookId").value(BOOK_ID))
                .andExpect(jsonPath("$.owned").value(true));

        paymentGateway.failWithProviderError();
        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertAll(
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals("2026-08-01 03:00:00.123456", 소장_완료_시각을_조회한다(paymentId)),
                () -> assertEquals(1, 소장_수를_조회한다()),
                () -> assertEquals(1, 서재_항목_수를_조회한다()),
                () -> assertEquals(1, 서재_마지막_페이지를_조회한다()),
                () -> assertEquals("2026-08-01 03:00:00.123456", 서재_갱신_시각을_조회한다()),
                () -> assertEquals(70, 잉크_잔액을_조회한다()),
                () -> assertEquals(0, 잉크_내역_수를_조회한다()),
                () -> assertEquals(1, paymentGateway.callCount()));
    }

    @Test
    void 소장_전에_읽은_페이지가_있으면_기존_마지막_위치를_유지한다() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, 3, '2026-07-31 12:00:00.123456')
                """,
                READER_ID,
                BOOK_ID);
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(true));

        assertAll(
                () -> assertEquals(1, 서재_항목_수를_조회한다()),
                () -> assertEquals(3, 서재_마지막_페이지를_조회한다()),
                () -> assertEquals("2026-07-31 12:00:00.123456", 서재_갱신_시각을_조회한다()));
    }

    @Test
    void 서재_항목_생성에_실패하면_결제_완료와_소장도_함께_되돌아간다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        jdbcTemplate.update(
                "DELETE FROM book_page WHERE book_id = ? AND page_number = 1",
                BOOK_ID);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ownershipPaymentFacade.complete(READER_ID, paymentId));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertEquals(0, 서재_항목_수를_조회한다()));
    }

    @Test
    void 아직_완료되지_않은_결제는_PENDING과_owned_false를_반환한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PENDING, BOOK_PRICE_WON));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.owned").value(false));

        paymentGateway.respondWith(PortOnePayment.notFound(paymentId.toString()));
        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertEquals(0, 서재_항목_수를_조회한다()));
    }

    @Test
    void T_OWN_003_금액이_다르면_FAILED로_기록하고_소장을_생성하지_않는다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON - 1));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_AMOUNT"));

        int callCount = paymentGateway.callCount();
        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_CONFLICT"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()),
                () -> assertEquals(0, 서재_항목_수를_조회한다()),
                () -> assertEquals(callCount, paymentGateway.callCount()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("검증_실패_필드_변형")
    void 결제_필드가_준비_기록과_다르면_검증_실패로_기록한다(
            String 설명, Function<PortOnePayment, PortOnePayment> 변형) throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        PortOnePayment payment = 결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON);
        paymentGateway.respondWith(변형.apply(payment));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()));
    }

    private static Stream<Arguments> 검증_실패_필드_변형() {
        return Stream.of(
                Arguments.of(
                        "통화가 KRW가 아니면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                payment.paymentId(),
                                payment.status(),
                                payment.totalAmount(),
                                "USD",
                                payment.storeId(),
                                payment.channelKey(),
                                payment.orderName(),
                                payment.version(),
                                payment.paidAt())),
                Arguments.of(
                        "결제 식별자가 다르면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                UUID.randomUUID().toString(),
                                payment.status(),
                                payment.totalAmount(),
                                payment.currency(),
                                payment.storeId(),
                                payment.channelKey(),
                                payment.orderName(),
                                payment.version(),
                                payment.paidAt())),
                Arguments.of(
                        "storeId가 다르면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                payment.paymentId(),
                                payment.status(),
                                payment.totalAmount(),
                                payment.currency(),
                                "다른-store",
                                payment.channelKey(),
                                payment.orderName(),
                                payment.version(),
                                payment.paidAt())),
                Arguments.of(
                        "orderName이 다르면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                payment.paymentId(),
                                payment.status(),
                                payment.totalAmount(),
                                payment.currency(),
                                payment.storeId(),
                                payment.channelKey(),
                                "다른 주문명",
                                payment.version(),
                                payment.paidAt())),
                Arguments.of(
                        "PortOne 버전이 다르면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                payment.paymentId(),
                                payment.status(),
                                payment.totalAmount(),
                                payment.currency(),
                                payment.storeId(),
                                payment.channelKey(),
                                payment.orderName(),
                                "V1",
                                payment.paidAt())),
                Arguments.of(
                        "PAID인데 채널키가 없으면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                payment.paymentId(),
                                payment.status(),
                                payment.totalAmount(),
                                payment.currency(),
                                payment.storeId(),
                                null,
                                payment.orderName(),
                                payment.version(),
                                payment.paidAt())),
                Arguments.of(
                        "PAID인데 결제완료 시각이 없으면",
                        (Function<PortOnePayment, PortOnePayment>) payment -> new PortOnePayment(
                                payment.paymentId(),
                                payment.status(),
                                payment.totalAmount(),
                                payment.currency(),
                                payment.storeId(),
                                payment.channelKey(),
                                payment.orderName(),
                                payment.version(),
                                null)));
    }

    @Test
    void 채널키가_아직_없는_PENDING_결제는_PENDING을_유지한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        PortOnePayment payment = 결제(paymentId, PortOnePaymentStatus.PENDING, BOOK_PRICE_WON);
        paymentGateway.respondWith(new PortOnePayment(
                payment.paymentId(),
                payment.status(),
                payment.totalAmount(),
                payment.currency(),
                payment.storeId(),
                null,
                payment.orderName(),
                payment.version(),
                payment.paidAt()));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()));
    }

    @Test
    void PortOne_최종_실패는_검증_실패로_기록한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.FAILED, BOOK_PRICE_WON));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        assertAll(
                () -> assertEquals("FAILED", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()));
    }

    @Test
    void PortOne_조회_장애는_503이고_PENDING을_유지한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.failWithProviderError();

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(0, 소장_수를_조회한다()));
    }

    @Test
    void 다른_독자나_존재하지_않는_결제는_404이다() throws Exception {
        다른_독자를_생성한다();
        UUID paymentId = 결제를_준비하고_ID를_반환한다();

        결제를_완료한다(OTHER_READER_ID, paymentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        결제를_완료한다(READER_ID, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertEquals(0, paymentGateway.callCount());
    }

    @Test
    void T_OWN_004_동시에_완료해도_소장과_결제_반영은_한_번만이다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        List<CompleteOwnershipPaymentResponse> responses = 동시에_완료한다(paymentId);

        assertAll(
                () -> assertTrue(responses.stream()
                        .allMatch(response -> response.status().name().equals("PAID"))),
                () -> assertTrue(responses.stream().allMatch(CompleteOwnershipPaymentResponse::owned)),
                () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(1, 소장_수를_조회한다()));
    }

    @RepeatedTest(5)
    void T_OWN_010_소장이_먼저면_페이지를_대여하지_않는다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));
        AccountLockGate lockGate = 첫번째_계정_잠금을_멈춘다();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CompleteOwnershipPaymentResponse> completion = executor.submit(
                    () -> ownershipPaymentFacade.complete(READER_ID, paymentId));
            assertTrue(lockGate.firstLockHeld().await(5, TimeUnit.SECONDS));

            Future<OpenPageResponse> pageOpening = executor.submit(
                    () -> readingFacade.openNewSession(READER_ID, BOOK_ID, 1));
            assertTrue(lockGate.secondLockRequested().await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> pageOpening.get(300, TimeUnit.MILLISECONDS));

            lockGate.releaseFirstLock().countDown();
            CompleteOwnershipPaymentResponse completionResponse = completion.get(5, TimeUnit.SECONDS);
            OpenPageResponse pageResponse = pageOpening.get(5, TimeUnit.SECONDS);

            assertAll(
                    () -> assertTrue(completionResponse.owned()),
                    () -> assertTrue(pageResponse.owned()),
                    () -> assertEquals(0, pageResponse.deductedInk()),
                    () -> assertNull(pageResponse.rentedAt()),
                    () -> assertNull(pageResponse.expiresAt()),
                    () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                    () -> assertEquals(1, 소장_수를_조회한다()),
                    () -> assertEquals(0, 대여_수를_조회한다()),
                    () -> assertEquals(0, 잉크_내역_수를_조회한다()),
                    () -> assertEquals(70, 잉크_잔액을_조회한다()));
        } finally {
            lockGate.releaseFirstLock().countDown();
            executor.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void T_OWN_010_대여가_먼저면_한_번_차감한_뒤_소장하고_환불하지_않는다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));
        AccountLockGate lockGate = 첫번째_계정_잠금을_멈춘다();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OpenPageResponse> pageOpening = executor.submit(
                    () -> readingFacade.openNewSession(READER_ID, BOOK_ID, 1));
            assertTrue(lockGate.firstLockHeld().await(5, TimeUnit.SECONDS));

            Future<CompleteOwnershipPaymentResponse> completion = executor.submit(
                    () -> ownershipPaymentFacade.complete(READER_ID, paymentId));
            assertTrue(lockGate.secondLockRequested().await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> completion.get(300, TimeUnit.MILLISECONDS));

            lockGate.releaseFirstLock().countDown();
            OpenPageResponse pageResponse = pageOpening.get(5, TimeUnit.SECONDS);
            CompleteOwnershipPaymentResponse completionResponse = completion.get(5, TimeUnit.SECONDS);

            assertAll(
                    () -> assertFalse(pageResponse.owned()),
                    () -> assertEquals(1, pageResponse.deductedInk()),
                    () -> assertTrue(completionResponse.owned()),
                    () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                    () -> assertEquals(1, 소장_수를_조회한다()),
                    () -> assertEquals(1, 대여_수를_조회한다()),
                    () -> assertEquals(1, 잉크_내역_수를_조회한다()),
                    () -> assertEquals(69, 잉크_잔액을_조회한다()));
        } finally {
            lockGate.releaseFirstLock().countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 별도 트랜잭션이 {@code ink_account}를 잠근 동안 소장 완료가 계정 잠금 진입 지점에서 멈추고,
     * {@code ownership_payment}는 다른 트랜잭션이 잠글 수 있어야 한다. 결제 행을 먼저 잠그면 두 번째
     * 잠금 요청이 시간 안에 끝나지 않으므로 정본의 계정 선행 잠금 순서를 직접 검증한다.
     */
    @Test
    void 소장_완료는_InkAccount를_ownershipPayment보다_먼저_잠근다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch accountLockRequested = new CountDownLatch(1);
        InkService inkServiceTarget = AopTestUtils.getUltimateTargetObject(inkService);
        doAnswer(invocation -> {
                    accountLockRequested.countDown();
                    return invocation.callRealMethod();
                })
                .when(inkServiceTarget)
                .lockAccount(READER_ID);

        ExecutorService lockHolder = Executors.newSingleThreadExecutor();
        ExecutorService completer = Executors.newSingleThreadExecutor();
        ExecutorService paymentLockProbe = Executors.newSingleThreadExecutor();
        try {
            lockHolder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ? FOR UPDATE",
                        Integer.class,
                        READER_ID);
                lockHeld.countDown();
                try {
                    assertTrue(releaseLock.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(lockHeld.await(5, TimeUnit.SECONDS));

            Future<CompleteOwnershipPaymentResponse> completion = completer.submit(
                    () -> ownershipPaymentFacade.complete(READER_ID, paymentId));

            assertTrue(accountLockRequested.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> completion.get(300, TimeUnit.MILLISECONDS));

            Future<String> paymentStatus = paymentLockProbe.submit(() ->
                    transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                            "SELECT status FROM ownership_payment WHERE payment_id = ? FOR UPDATE",
                            String.class,
                            paymentId.toString())));
            assertEquals("PENDING", paymentStatus.get(5, TimeUnit.SECONDS));

            releaseLock.countDown();
            CompleteOwnershipPaymentResponse response = completion.get(5, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals("PAID", response.status().name()),
                    () -> assertEquals(1, 소장_수를_조회한다()));
        } finally {
            releaseLock.countDown();
            lockHolder.shutdownNow();
            completer.shutdownNow();
            paymentLockProbe.shutdownNow();
        }
    }

    @Test
    void 동시_PAID_웹훅은_계정_잠금_뒤_결제의_최신_상태를_조회한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        CountDownLatch accountLockReady = new CountDownLatch(2);
        CountDownLatch startAccountLock = new CountDownLatch(1);
        InkService inkServiceTarget = AopTestUtils.getUltimateTargetObject(inkService);
        doAnswer(invocation -> {
                    accountLockReady.countDown();
                    assertTrue(startAccountLock.await(5, TimeUnit.SECONDS));
                    return invocation.callRealMethod();
                })
                .when(inkServiceTarget)
                .lockAccount(READER_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> completions = List.of(
                    executor.submit(() -> ownershipPaymentFacade.completeWebhook(paymentId)),
                    executor.submit(() -> ownershipPaymentFacade.completeWebhook(paymentId)));
            assertTrue(accountLockReady.await(5, TimeUnit.SECONDS));
            startAccountLock.countDown();

            for (Future<?> completion : completions) {
                completion.get(5, TimeUnit.SECONDS);
            }

            assertAll(
                    () -> assertEquals("PAID", 결제_상태를_조회한다(paymentId)),
                    () -> assertEquals(1, 소장_수를_조회한다()));
        } finally {
            startAccountLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 소장_생성이_실패하면_PAID_전이도_함께_롤백한다() throws Exception {
        UUID paymentId = 결제를_준비하고_ID를_반환한다();
        long ownershipPaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM ownership_payment WHERE payment_id = ?",
                Long.class,
                paymentId.toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-08-01 00:00:00.000000')
                """,
                READER_ID,
                BOOK_ID,
                ownershipPaymentId);
        paymentGateway.respondWith(결제(paymentId, PortOnePaymentStatus.PAID, BOOK_PRICE_WON));

        결제를_완료한다(READER_ID, paymentId)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertAll(
                () -> assertEquals("PENDING", 결제_상태를_조회한다(paymentId)),
                () -> assertEquals(1, 소장_수를_조회한다()));
    }

    @Test
    void T_OWN_HIST_001_PAID만_최신순으로_도서정보와_함께_제공한다() throws Exception {
        long paidBookId = 히스토리_도서를_준비한다(0, 21_000);
        long pendingBookId = 히스토리_도서를_준비한다(1, 22_000);
        long failedBookId = 히스토리_도서를_준비한다(2, 23_000);
        UUID paidPaymentId = PAID_소장을_생성한다(paidBookId, 21_000, PAID_AT);
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at)
                VALUES (?, ?, ?, 'PENDING', ?, '2026-08-01 00:00:00.000000')
                """,
                READER_ID,
                pendingBookId,
                UUID.randomUUID().toString(),
                22_000);
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at)
                VALUES (?, ?, ?, 'FAILED', ?, '2026-08-01 00:00:00.000000')
                """,
                READER_ID,
                failedBookId,
                UUID.randomUUID().toString(),
                23_000);

        mockMvc.perform(get("/api/ownership-payments")
                        .param("page", "1")
                        .with(authentication(인증된_독자())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].paymentId").value(paidPaymentId.toString()))
                .andExpect(jsonPath("$.payments[0].bookId").value(paidBookId))
                .andExpect(jsonPath("$.payments[0].bookTitle").value("역사책 0"))
                .andExpect(jsonPath("$.payments[0].amountWon").value(21_000))
                .andExpect(jsonPath("$.payments[0].owned").value(true))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void T_OWN_HIST_002_같은_시각을_포함한_11건과_범위_밖_페이지를_처리한다() throws Exception {
        java.util.Set<String> expectedPaymentIds = new java.util.HashSet<>();
        for (int index = 0; index < 11; index++) {
            long bookId = 히스토리_도서를_준비한다(index, 10_000 + index);
            Instant paidAt = index < 2 ? PAID_AT : PAID_AT.minusSeconds(index);
            expectedPaymentIds.add(
                    PAID_소장을_생성한다(bookId, 10_000 + index, paidAt).toString());
        }

        MvcResult firstPage = mockMvc.perform(get("/api/ownership-payments")
                        .param("page", "1")
                        .with(authentication(인증된_독자())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(10))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCount").value(11))
                .andReturn();
        MvcResult secondPage = mockMvc.perform(get("/api/ownership-payments")
                        .param("page", "2")
                        .with(authentication(인증된_독자())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andReturn();

        mockMvc.perform(get("/api/ownership-payments")
                        .param("page", "0")
                        .with(authentication(인증된_독자())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/ownership-payments")
                        .param("page", "3")
                        .with(authentication(인증된_독자())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()").value(0));

        java.util.Set<String> actualPaymentIds = new java.util.HashSet<>();
        actualPaymentIds.addAll(응답_결제_ID_목록(firstPage));
        actualPaymentIds.addAll(응답_결제_ID_목록(secondPage));
        assertEquals(expectedPaymentIds, actualPaymentIds);
    }

    private List<String> 응답_결제_ID_목록(MvcResult result) throws Exception {
        List<String> ids = new ArrayList<>();
        objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("payments")
                .forEach(item -> ids.add(item.get("paymentId").asText()));
        return ids;
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
        return 인증된_독자(READER_ID);
    }

    private TestingAuthenticationToken 인증된_독자(long readerId) {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(readerId),
                null,
                "ROLE_USER");
    }

    private UUID 결제를_준비하고_ID를_반환한다() throws Exception {
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

    private org.springframework.test.web.servlet.ResultActions 결제를_완료한다(
            long readerId, UUID paymentId) throws Exception {
        return mockMvc.perform(post("/api/ownership-payments/{paymentId}/complete", paymentId)
                .with(authentication(인증된_독자(readerId)))
                .with(csrf()));
    }

    private List<CompleteOwnershipPaymentResponse> 동시에_완료한다(UUID paymentId) throws Exception {
        int concurrentRequestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequestCount);
        CountDownLatch ready = new CountDownLatch(concurrentRequestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<CompleteOwnershipPaymentResponse> action = () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return ownershipPaymentFacade.complete(READER_ID, paymentId);
            };
            List<Future<CompleteOwnershipPaymentResponse>> futures = new ArrayList<>();
            for (int index = 0; index < concurrentRequestCount; index++) {
                futures.add(executor.submit(action));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<CompleteOwnershipPaymentResponse> responses = new ArrayList<>();
            for (Future<CompleteOwnershipPaymentResponse> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private AccountLockGate 첫번째_계정_잠금을_멈춘다() {
        CountDownLatch firstLockHeld = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondLockRequested = new CountDownLatch(1);
        AtomicInteger lockOrder = new AtomicInteger();
        InkService inkServiceTarget = AopTestUtils.getUltimateTargetObject(inkService);
        doAnswer(invocation -> {
                    int order = lockOrder.incrementAndGet();
                    if (order == 2) {
                        secondLockRequested.countDown();
                    }
                    Object result = invocation.callRealMethod();
                    if (order == 1) {
                        firstLockHeld.countDown();
                        assertTrue(releaseFirstLock.await(5, TimeUnit.SECONDS));
                    }
                    return result;
                })
                .when(inkServiceTarget)
                .lockAccount(READER_ID);
        return new AccountLockGate(firstLockHeld, releaseFirstLock, secondLockRequested);
    }

    private record AccountLockGate(
            CountDownLatch firstLockHeld,
            CountDownLatch releaseFirstLock,
            CountDownLatch secondLockRequested) {}

    private PortOnePayment 결제(UUID paymentId, PortOnePaymentStatus status, long totalAmount) {
        return new PortOnePayment(
                paymentId.toString(),
                status,
                totalAmount,
                "KRW",
                "store-test",
                "channel-test",
                "읽어볼까 도서 소장",
                "V2",
                status == PortOnePaymentStatus.PAID ? PAID_AT : null);
    }

    private void 다른_독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum415-other@example.com', '{noop}password',
                        '2026-08-01 00:00:00.000000')
                """,
                OTHER_READER_ID);
    }

    private long 히스토리_도서를_준비한다(int index, int priceWon) {
        long bookId = HISTORY_BOOK_ID_START + index;
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '역사', ?, '읽어볼까', 1, ?)
                """,
                bookId,
                "역사책 " + index,
                priceWon);
        return bookId;
    }

    private UUID PAID_소장을_생성한다(long bookId, int amountWon, Instant paidAt) {
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', ?, ?, ?)
                """,
                READER_ID,
                bookId,
                paymentId.toString(),
                amountWon,
                DATETIME_FORMATTER.format(paidAt.minusSeconds(60)),
                DATETIME_FORMATTER.format(paidAt));
        long ownershipPaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM ownership_payment WHERE payment_id = ?",
                Long.class,
                paymentId.toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                bookId,
                ownershipPaymentId,
                DATETIME_FORMATTER.format(paidAt));
        return paymentId;
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
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id IN (?, ?)", READER_ID, OTHER_READER_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update(
                "DELETE FROM book WHERE id BETWEEN ? AND ?",
                HISTORY_BOOK_ID_START,
                HISTORY_BOOK_ID_START + 99);
    }

    private String 소장_완료_시각을_조회한다(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(paid_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM ownership_payment
                WHERE payment_id = ?
                """,
                String.class,
                paymentId.toString());
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

    private int 서재_항목_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    private int 서재_마지막_페이지를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT last_page_number FROM library_entry WHERE reader_id = ? AND book_id = ?",
                Integer.class,
                READER_ID,
                BOOK_ID);
    }

    private String 서재_갱신_시각을_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f')
                FROM library_entry
                WHERE reader_id = ? AND book_id = ?
                """,
                String.class,
                READER_ID,
                BOOK_ID);
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
