package com.example.ilgeobolkka.ownership.facade;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.dto.PrepareOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
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
    private static final String ORDER_NAME = "읽어볼까 도서 소장";

    private final InkService inkService;
    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final PortOnePaymentProperties paymentProperties;
    private final Clock clock;

    @Transactional
    public Preparation prepare(long readerId, long bookId) {
        inkService.lockAccount(readerId);
        Book book = bookService.findBook(bookId);
        OwnershipService.PaymentPreparation preparation = ownershipService.preparePayment(
                readerId,
                bookId,
                UUID.randomUUID(),
                book.getPriceWon(),
                clock.instant());
        PrepareOwnershipPaymentResponse response = PrepareOwnershipPaymentResponse.from(
                preparation.payment(),
                paymentProperties.storeId(),
                paymentProperties.channelKey(),
                ORDER_NAME,
                CLIENT_CURRENCY);
        return new Preparation(response, preparation.created());
    }

    public record Preparation(
            PrepareOwnershipPaymentResponse response,
            boolean created) {}
}
