package com.example.ilgeobolkka.infra.portone;

import io.portone.sdk.server.errors.PaymentNotFoundException;
import io.portone.sdk.server.payment.CancelledPayment;
import io.portone.sdk.server.payment.FailedPayment;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PartialCancelledPayment;
import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.PaymentClient;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

public class PortOneSdkPaymentGateway implements PortOnePaymentGateway {

    private final PaymentClient paymentClient;

    public PortOneSdkPaymentGateway(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Override
    public PortOnePayment getPayment(String paymentId) {
        try {
            return map(paymentClient.getPayment(paymentId).get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PortOnePaymentUnavailableException(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof PaymentNotFoundException) {
                return PortOnePayment.notFound(paymentId);
            }
            throw new PortOnePaymentUnavailableException(exception.getCause());
        } catch (RuntimeException exception) {
            throw new PortOnePaymentUnavailableException(exception);
        }
    }

    private PortOnePayment map(Payment payment) {
        if (!(payment instanceof Payment.Recognized recognized)) {
            throw new PortOnePaymentUnavailableException();
        }

        PortOnePaymentStatus status = statusOf(payment);
        Instant paidAt = payment instanceof PaidPayment paidPayment
                ? paidPayment.getPaidAt()
                : null;
        String channelKey = recognized.getChannel() == null
                ? null
                : recognized.getChannel().getKey();

        return new PortOnePayment(
                recognized.getId(),
                status,
                recognized.getAmount().getTotal(),
                recognized.getCurrency().getValue(),
                recognized.getStoreId(),
                channelKey,
                recognized.getOrderName(),
                recognized.getVersion().getValue(),
                paidAt);
    }

    private PortOnePaymentStatus statusOf(Payment payment) {
        if (payment instanceof PaidPayment) {
            return PortOnePaymentStatus.PAID;
        }
        if (payment instanceof FailedPayment
                || payment instanceof CancelledPayment
                || payment instanceof PartialCancelledPayment) {
            return PortOnePaymentStatus.FAILED;
        }
        return PortOnePaymentStatus.PENDING;
    }
}
