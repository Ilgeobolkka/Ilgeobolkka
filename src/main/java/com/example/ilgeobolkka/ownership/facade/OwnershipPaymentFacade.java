package com.example.ilgeobolkka.ownership.facade;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.ink.exception.PaymentVerificationException;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.dto.CompleteOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.dto.FindOwnershipPaymentsResponse;
import com.example.ilgeobolkka.ownership.dto.PrepareOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.service.OwnershipPaymentService;
import java.time.Clock;
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
public class OwnershipPaymentFacade {

    public static final String CLIENT_CURRENCY = "CURRENCY_KRW";

    private final InkService inkService;
    private final BookService bookService;
    private final OwnershipPaymentService ownershipPaymentService;
    private final PortOnePaymentGateway paymentGateway;
    private final PortOnePaymentProperties paymentProperties;
    private final Clock clock;

    @Transactional
    public Preparation prepare(long readerId, long bookId) {
        inkService.lockAccount(readerId);
        Book book = bookService.findBook(bookId);
        OwnershipPaymentService.PaymentPreparation preparation =
                ownershipPaymentService.preparePayment(
                        readerId,
                        bookId,
                        UUID.randomUUID(),
                        book.getPriceWon(),
                        clock.instant());
        PrepareOwnershipPaymentResponse response = PrepareOwnershipPaymentResponse.from(
                preparation.payment(),
                paymentProperties.storeId(),
                paymentProperties.channelKey(),
                OwnershipPaymentService.ORDER_NAME,
                CLIENT_CURRENCY);
        return new Preparation(response, preparation.created());
    }

    public CompleteOwnershipPaymentResponse complete(long readerId, UUID paymentId) {
        var cachedResponse = ownershipPaymentService.findCachedCompletion(readerId, paymentId);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        PortOnePayment payment = paymentGateway.getPayment(paymentId.toString());
        OwnershipPaymentService.CompletionResult result =
                ownershipPaymentService.applyPaymentResult(readerId, paymentId, payment);
        if (result.errorCode() != null) {
            throw new PaymentVerificationException(result.errorCode());
        }
        return result.response();
    }

    public boolean existsByPaymentId(UUID paymentId) {
        return ownershipPaymentService.existsByPaymentId(paymentId);
    }

    public void completeWebhook(UUID paymentId) {
        PortOnePayment payment = paymentGateway.getPayment(paymentId.toString());
        ownershipPaymentService.applyWebhookPaymentResult(paymentId, payment);
    }

    public FindOwnershipPaymentsResponse findHistory(long readerId, int page) {
        return FindOwnershipPaymentsResponse.from(
                ownershipPaymentService.getHistory(readerId, page), page);
    }

    public record Preparation(
            PrepareOwnershipPaymentResponse response,
            boolean created) {}
}
