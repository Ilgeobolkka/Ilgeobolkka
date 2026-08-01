package com.example.ilgeobolkka.ownership.facade;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.ink.exception.PaymentVerificationException;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.library.service.LibraryService;
import com.example.ilgeobolkka.ownership.dto.CompleteOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.dto.PrepareOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.service.OwnershipPaymentService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class OwnershipPaymentFacade {

    public static final String CLIENT_CURRENCY = "CURRENCY_KRW";

    private final InkService inkService;
    private final BookService bookService;
    private final OwnershipPaymentService ownershipPaymentService;
    private final LibraryService libraryService;
    private final PortOnePaymentGateway paymentGateway;
    private final PortOnePaymentProperties paymentProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

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

    /**
     * PortOne 조회를 DB 트랜잭션 밖에서 하려고 단계마다 짧은 트랜잭션을 쓴다. 이 메서드에
     * {@code @Transactional}을 붙이면 안 된다. 검증 실패는 예외 대신
     * {@link OwnershipPaymentService.CompletionResult#errorCode()}로 돌아오는데, 이는
     * {@code FAILED} 전이를 커밋한 뒤 트랜잭션 밖에서 던지기 위함이다. 여기서 트랜잭션을 열면 그
     * 전이가 함께 되돌아간다.
     */
    public CompleteOwnershipPaymentResponse complete(long readerId, UUID paymentId) {
        var cachedResponse = ownershipPaymentService.findCachedCompletion(readerId, paymentId);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        PortOnePayment payment = paymentGateway.getPayment(paymentId.toString());
        OwnershipPaymentService.CompletionResult result =
                transactionTemplate.execute(status -> {
                    var completion =
                            ownershipPaymentService.applyPaymentResult(readerId, paymentId, payment);
                    recordOwnership(completion);
                    return completion;
                });
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
        transactionTemplate.executeWithoutResult(status -> {
            var completion = ownershipPaymentService.applyWebhookPaymentResult(paymentId, payment);
            recordOwnership(completion);
        });
    }

    private void recordOwnership(OwnershipPaymentService.CompletionResult completion) {
        if (completion.ownershipGrant() == null) {
            return;
        }
        var grant = completion.ownershipGrant();
        libraryService.recordOwnership(grant.readerId(), grant.bookId(), grant.ownedAt());
    }

    public record Preparation(
            PrepareOwnershipPaymentResponse response,
            boolean created) {}
}
