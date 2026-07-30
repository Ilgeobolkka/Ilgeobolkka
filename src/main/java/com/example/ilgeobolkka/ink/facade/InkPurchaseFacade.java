package com.example.ilgeobolkka.ink.facade;

import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentGateway;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.ink.dto.CompleteInkPurchaseResponse;
import com.example.ilgeobolkka.ink.dto.PrepareInkPurchaseResponse;
import com.example.ilgeobolkka.ink.entity.InkPurchase;
import com.example.ilgeobolkka.ink.exception.PaymentVerificationException;
import com.example.ilgeobolkka.ink.service.InkPurchaseService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class InkPurchaseFacade {

    private final InkPurchaseService inkPurchaseService;
    private final PortOnePaymentGateway paymentGateway;
    private final PortOnePaymentProperties paymentProperties;
    private final Clock clock;

    public PrepareInkPurchaseResponse prepare(long readerId) {
        InkPurchase purchase =
                inkPurchaseService.prepare(readerId, UUID.randomUUID(), clock.instant());
        return PrepareInkPurchaseResponse.from(
                purchase,
                paymentProperties.storeId(),
                paymentProperties.channelKey(),
                InkPurchaseService.ORDER_NAME,
                InkPurchaseService.CLIENT_CURRENCY);
    }

    public CompleteInkPurchaseResponse complete(long readerId, UUID paymentId) {
        var cachedResponse = inkPurchaseService.findCachedCompletion(readerId, paymentId);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        PortOnePayment payment = paymentGateway.getPayment(paymentId.toString());
        InkPurchaseService.CompletionResult result =
                inkPurchaseService.applyPaymentResult(readerId, paymentId, payment);
        if (result.errorCode() != null) {
            throw new PaymentVerificationException(result.errorCode());
        }
        return result.response();
    }
}
