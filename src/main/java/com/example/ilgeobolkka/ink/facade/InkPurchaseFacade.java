package com.example.ilgeobolkka.ink.facade;

import com.example.ilgeobolkka.global.exception.ErrorCode;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import com.example.ilgeobolkka.ink.dto.CompleteInkPurchaseResponse;
import com.example.ilgeobolkka.ink.dto.PrepareInkPurchaseResponse;
import com.example.ilgeobolkka.ink.entity.InkPurchase;
import com.example.ilgeobolkka.ink.entity.InkPurchaseStatus;
import com.example.ilgeobolkka.ink.exception.InkPurchaseNotFoundException;
import com.example.ilgeobolkka.ink.exception.PaymentStateConflictException;
import com.example.ilgeobolkka.ink.exception.PaymentVerificationException;
import com.example.ilgeobolkka.ink.repository.InkPurchaseRepository;
import com.example.ilgeobolkka.ink.service.InkService;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
public class InkPurchaseFacade {

    static final String ORDER_NAME = "읽어볼까 100잉크";
    static final String CLIENT_CURRENCY = "CURRENCY_KRW";
    static final String SERVER_CURRENCY = "KRW";
    static final String PORTONE_VERSION = "V2";

    private final InkPurchaseRepository inkPurchaseRepository;
    private final InkService inkService;
    private final PortOnePaymentGateway paymentGateway;
    private final PortOnePaymentProperties paymentProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public InkPurchaseFacade(
            InkPurchaseRepository inkPurchaseRepository,
            InkService inkService,
            PortOnePaymentGateway paymentGateway,
            PortOnePaymentProperties paymentProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.inkPurchaseRepository = inkPurchaseRepository;
        this.inkService = inkService;
        this.paymentGateway = paymentGateway;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PrepareInkPurchaseResponse prepare(long readerId) {
        return transactionTemplate.execute(status -> {
            InkPurchase purchase = InkPurchase.create(readerId, UUID.randomUUID(), clock.instant());
            inkPurchaseRepository.save(purchase);
            return PrepareInkPurchaseResponse.from(
                    purchase,
                    paymentProperties.storeId(),
                    paymentProperties.channelKey(),
                    ORDER_NAME,
                    CLIENT_CURRENCY);
        });
    }

    public CompleteInkPurchaseResponse complete(long readerId, UUID paymentId) {
        CompleteInkPurchaseResponse cachedResponse = transactionTemplate.execute(status -> {
            InkPurchase purchase = findPurchase(paymentId, readerId);
            if (purchase.getStatus() == InkPurchaseStatus.FAILED) {
                throw new PaymentStateConflictException();
            }
            if (purchase.getStatus() == InkPurchaseStatus.PAID) {
                return CompleteInkPurchaseResponse.paid(
                        purchase,
                        inkService.getBalance(readerId));
            }
            return null;
        });
        if (cachedResponse != null) {
            return cachedResponse;
        }

        PortOnePayment payment = paymentGateway.getPayment(paymentId.toString());
        CompletionResult result = transactionTemplate.execute(status ->
                applyPaymentResult(readerId, paymentId, payment));
        if (result.errorCode() != null) {
            throw new PaymentVerificationException(result.errorCode());
        }
        return result.response();
    }

    private CompletionResult applyPaymentResult(
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
                || !Objects.equals(payment.channelKey(), paymentProperties.channelKey())
                || !Objects.equals(payment.orderName(), ORDER_NAME)
                || !Objects.equals(payment.version(), PORTONE_VERSION)) {
            return ErrorCode.PAYMENT_VERIFICATION_FAILED;
        }
        return null;
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

    private record CompletionResult(
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
