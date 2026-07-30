package com.example.ilgeobolkka.ink.service;

import com.example.ilgeobolkka.global.exception.ErrorCode;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import com.example.ilgeobolkka.ink.dto.CompleteInkPurchaseResponse;
import com.example.ilgeobolkka.ink.entity.InkPurchase;
import com.example.ilgeobolkka.ink.entity.InkPurchaseStatus;
import com.example.ilgeobolkka.ink.exception.InkPurchaseNotFoundException;
import com.example.ilgeobolkka.ink.exception.PaymentStateConflictException;
import com.example.ilgeobolkka.ink.repository.InkPurchaseRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class InkPurchaseService {

    public static final String ORDER_NAME = "읽어볼까 100잉크";
    public static final String CLIENT_CURRENCY = "CURRENCY_KRW";
    private static final String SERVER_CURRENCY = "KRW";
    private static final String PORTONE_VERSION = "V2";

    private final InkPurchaseRepository inkPurchaseRepository;
    private final InkService inkService;
    private final PortOnePaymentProperties paymentProperties;

    @Transactional
    public InkPurchase prepare(long readerId, UUID paymentId, Instant createdAt) {
        return inkPurchaseRepository.save(InkPurchase.create(readerId, paymentId, createdAt));
    }

    @Transactional(readOnly = true)
    public Optional<CompleteInkPurchaseResponse> findCachedCompletion(
            long readerId, UUID paymentId) {
        InkPurchase purchase = findPurchase(paymentId, readerId);
        if (purchase.getStatus() == InkPurchaseStatus.FAILED) {
            throw new PaymentStateConflictException();
        }
        if (purchase.getStatus() == InkPurchaseStatus.PAID) {
            return Optional.of(CompleteInkPurchaseResponse.paid(
                    purchase,
                    inkService.getBalance(readerId)));
        }
        return Optional.empty();
    }

    @Transactional
    public CompletionResult applyPaymentResult(
            long readerId,
            UUID paymentId,
            PortOnePayment payment) {
        InkPurchase purchase = findPurchaseForUpdate(paymentId, readerId);
        if (purchase.getStatus() == InkPurchaseStatus.PAID) {
            return CompletionResult.success(CompleteInkPurchaseResponse.paid(
                    purchase,
                    inkService.getBalance(readerId)));
        }
        if (purchase.getStatus() == InkPurchaseStatus.FAILED) {
            throw new PaymentStateConflictException();
        }

        return applyPendingPaymentResult(purchase, payment);
    }

    @Transactional
    public void applyWebhookPaymentResult(
            UUID paymentId,
            PortOnePayment payment) {
        InkPurchase purchase = findPurchaseForUpdate(paymentId);
        if (purchase.getStatus() != InkPurchaseStatus.PENDING) {
            return;
        }
        applyPendingPaymentResult(purchase, payment);
    }

    private CompletionResult applyPendingPaymentResult(
            InkPurchase purchase,
            PortOnePayment payment) {
        long readerId = purchase.getReaderId();
        if (payment.status() == PortOnePaymentStatus.NOT_FOUND) {
            return CompletionResult.success(CompleteInkPurchaseResponse.pending(
                    purchase,
                    inkService.getBalance(readerId)));
        }

        ErrorCode verificationError = verifyPayment(purchase, payment);
        if (verificationError != null) {
            purchase.markFailed();
            return CompletionResult.failure(verificationError);
        }

        if (payment.status() == PortOnePaymentStatus.PENDING) {
            return CompletionResult.success(CompleteInkPurchaseResponse.pending(
                    purchase,
                    inkService.getBalance(readerId)));
        }
        if (payment.status() == PortOnePaymentStatus.FAILED) {
            purchase.markFailed();
            return CompletionResult.failure(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        if (payment.paidAt() == null) {
            purchase.markFailed();
            return CompletionResult.failure(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }

        purchase.markPaid(payment.paidAt());
        int balance = inkService.grant(readerId, purchase.getId(), payment.paidAt());
        return CompletionResult.success(CompleteInkPurchaseResponse.paid(purchase, balance));
    }

    private ErrorCode verifyPayment(InkPurchase purchase, PortOnePayment payment) {
        if (payment.totalAmount() != purchase.getAmountWon()) {
            return ErrorCode.INVALID_PAYMENT_AMOUNT;
        }
        if (!Objects.equals(payment.paymentId(), purchase.getPaymentId().toString())
                || !Objects.equals(payment.currency(), SERVER_CURRENCY)
                || !Objects.equals(payment.storeId(), paymentProperties.storeId())
                || !Objects.equals(payment.orderName(), ORDER_NAME)
                || !Objects.equals(payment.version(), PORTONE_VERSION)) {
            return ErrorCode.PAYMENT_VERIFICATION_FAILED;
        }
        if (payment.channelKey() == null) {
            return payment.status() == PortOnePaymentStatus.PENDING
                    ? null
                    : ErrorCode.PAYMENT_VERIFICATION_FAILED;
        }
        return Objects.equals(payment.channelKey(), paymentProperties.channelKey())
                ? null
                : ErrorCode.PAYMENT_VERIFICATION_FAILED;
    }

    private InkPurchase findPurchase(UUID paymentId, long readerId) {
        return inkPurchaseRepository
                .findByPaymentIdAndReaderId(paymentId, readerId)
                .orElseThrow(() -> new InkPurchaseNotFoundException(paymentId));
    }

    private InkPurchase findPurchaseForUpdate(UUID paymentId, long readerId) {
        return inkPurchaseRepository
                .findByPaymentIdAndReaderIdForUpdate(paymentId, readerId)
                .orElseThrow(() -> new InkPurchaseNotFoundException(paymentId));
    }

    private InkPurchase findPurchaseForUpdate(UUID paymentId) {
        return inkPurchaseRepository
                .findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new InkPurchaseNotFoundException(paymentId));
    }

    public record CompletionResult(
            CompleteInkPurchaseResponse response,
            ErrorCode errorCode) {

        private static CompletionResult success(CompleteInkPurchaseResponse response) {
            return new CompletionResult(response, null);
        }

        private static CompletionResult failure(ErrorCode errorCode) {
            return new CompletionResult(null, errorCode);
        }
    }
}
