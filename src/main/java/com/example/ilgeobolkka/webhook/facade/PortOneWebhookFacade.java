package com.example.ilgeobolkka.webhook.facade;

import com.example.ilgeobolkka.infra.portone.PortOneWebhookEvent;
import com.example.ilgeobolkka.infra.portone.PortOneWebhookVerifier;
import com.example.ilgeobolkka.ink.facade.InkPurchaseFacade;
import com.example.ilgeobolkka.ownership.facade.OwnershipPaymentFacade;
import com.example.ilgeobolkka.webhook.exception.UnknownPaymentException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class PortOneWebhookFacade {

    private final PortOneWebhookVerifier portOneWebhookVerifier;
    private final InkPurchaseFacade inkPurchaseFacade;
    private final OwnershipPaymentFacade ownershipPaymentFacade;

    /**
     * paymentId는 잉크 이용권·소장 결제 어느 테이블에 속하는지 이벤트에 담기지 않는다. 두 종류를
     * 구분하는 별도 스키마 없이 내부 기록 존재 여부로 먼저 판정한 뒤, 결정된 한 종류로만 PortOne을
     * 조회한다. 존재 확인 없이 순서대로 시도하면 알 수 없는 paymentId마다 PortOne을 두 번 호출한다.
     */
    public void handle(
            String rawBody,
            String webhookId,
            String webhookSignature,
            String webhookTimestamp) {
        PortOneWebhookEvent event = portOneWebhookVerifier.verify(
                rawBody,
                webhookId,
                webhookSignature,
                webhookTimestamp);
        if (event.type() == PortOneWebhookEvent.Type.UNSUPPORTED) {
            return;
        }
        String rawPaymentId = event.paymentId();
        UUID paymentId;
        try {
            paymentId = UUID.fromString(rawPaymentId);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (!paymentId.toString().equalsIgnoreCase(rawPaymentId)) {
            return;
        }
        if (inkPurchaseFacade.existsByPaymentId(paymentId)) {
            inkPurchaseFacade.completeWebhook(paymentId);
        } else if (ownershipPaymentFacade.existsByPaymentId(paymentId)) {
            ownershipPaymentFacade.completeWebhook(paymentId);
        } else {
            throw new UnknownPaymentException(paymentId);
        }
    }
}
