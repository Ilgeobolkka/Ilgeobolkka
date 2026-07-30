package com.example.ilgeobolkka.infra.portone;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.portone.sdk.server.common.Currency;
import io.portone.sdk.server.common.PortOneVersion;
import io.portone.sdk.server.common.SelectedChannel;
import io.portone.sdk.server.errors.PaymentNotFoundException;
import io.portone.sdk.server.payment.FailedPayment;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentAmount;
import io.portone.sdk.server.payment.PaymentClient;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortOneSdkPaymentGatewayTest {

    private static final String PAYMENT_ID = "14fa879f-d4f9-4d54-b271-963ba897482f";
    private static final Instant PAID_AT = Instant.parse("2026-07-30T02:00:00Z");

    private PaymentClient paymentClient;
    private PortOneSdkPaymentGateway paymentGateway;

    @BeforeEach
    void setUp() {
        paymentClient = mock(PaymentClient.class);
        paymentGateway = new PortOneSdkPaymentGateway(paymentClient);
    }

    @Test
    void PortOne_PAID_결제를_내부_결제_정보로_변환한다() {
        PaidPayment paidPayment = mock(PaidPayment.class);
        PaymentAmount amount = mock(PaymentAmount.class);
        Currency currency = mock(Currency.class);
        SelectedChannel channel = mock(SelectedChannel.class);
        PortOneVersion version = mock(PortOneVersion.class);
        when(paidPayment.getId()).thenReturn(PAYMENT_ID);
        when(paidPayment.getAmount()).thenReturn(amount);
        when(amount.getTotal()).thenReturn(1_000L);
        when(paidPayment.getCurrency()).thenReturn(currency);
        when(currency.getValue()).thenReturn("KRW");
        when(paidPayment.getStoreId()).thenReturn("store-test");
        when(paidPayment.getChannel()).thenReturn(channel);
        when(channel.getKey()).thenReturn("channel-test");
        when(paidPayment.getOrderName()).thenReturn("읽어볼까 100잉크");
        when(paidPayment.getVersion()).thenReturn(version);
        when(version.getValue()).thenReturn("V2");
        when(paidPayment.getPaidAt()).thenReturn(PAID_AT);
        when(paymentClient.getPayment(PAYMENT_ID))
                .thenReturn(CompletableFuture.completedFuture(paidPayment));

        PortOnePayment payment = paymentGateway.getPayment(PAYMENT_ID);

        assertAll(
                () -> assertEquals(PAYMENT_ID, payment.paymentId()),
                () -> assertEquals(PortOnePaymentStatus.PAID, payment.status()),
                () -> assertEquals(1_000L, payment.totalAmount()),
                () -> assertEquals("KRW", payment.currency()),
                () -> assertEquals("store-test", payment.storeId()),
                () -> assertEquals("channel-test", payment.channelKey()),
                () -> assertEquals("읽어볼까 100잉크", payment.orderName()),
                () -> assertEquals("V2", payment.version()),
                () -> assertEquals(PAID_AT, payment.paidAt()));
    }

    @Test
    void PortOne_최종_실패를_FAILED로_변환한다() {
        FailedPayment failedPayment = recognizedFailedPayment();
        when(paymentClient.getPayment(PAYMENT_ID))
                .thenReturn(CompletableFuture.completedFuture(failedPayment));

        PortOnePayment payment = paymentGateway.getPayment(PAYMENT_ID);

        assertEquals(PortOnePaymentStatus.FAILED, payment.status());
    }

    @Test
    void PortOne에_결제가_아직_없으면_NOT_FOUND로_변환한다() {
        CompletableFuture<io.portone.sdk.server.payment.Payment> future = new CompletableFuture<>();
        future.completeExceptionally(mock(PaymentNotFoundException.class));
        when(paymentClient.getPayment(PAYMENT_ID)).thenReturn(future);

        PortOnePayment payment = paymentGateway.getPayment(PAYMENT_ID);

        assertEquals(PortOnePaymentStatus.NOT_FOUND, payment.status());
    }

    @Test
    void PortOne_조회_장애는_내부_장애_예외로_변환한다() {
        CompletableFuture<io.portone.sdk.server.payment.Payment> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("provider unavailable"));
        when(paymentClient.getPayment(PAYMENT_ID)).thenReturn(future);

        assertThrows(
                PortOnePaymentUnavailableException.class,
                () -> paymentGateway.getPayment(PAYMENT_ID));
    }

    private FailedPayment recognizedFailedPayment() {
        FailedPayment failedPayment = mock(FailedPayment.class);
        PaymentAmount amount = mock(PaymentAmount.class);
        Currency currency = mock(Currency.class);
        SelectedChannel channel = mock(SelectedChannel.class);
        PortOneVersion version = mock(PortOneVersion.class);
        when(failedPayment.getId()).thenReturn(PAYMENT_ID);
        when(failedPayment.getAmount()).thenReturn(amount);
        when(amount.getTotal()).thenReturn(1_000L);
        when(failedPayment.getCurrency()).thenReturn(currency);
        when(currency.getValue()).thenReturn("KRW");
        when(failedPayment.getStoreId()).thenReturn("store-test");
        when(failedPayment.getChannel()).thenReturn(channel);
        when(channel.getKey()).thenReturn("channel-test");
        when(failedPayment.getOrderName()).thenReturn("읽어볼까 100잉크");
        when(failedPayment.getVersion()).thenReturn(version);
        when(version.getValue()).thenReturn("V2");
        return failedPayment;
    }
}
