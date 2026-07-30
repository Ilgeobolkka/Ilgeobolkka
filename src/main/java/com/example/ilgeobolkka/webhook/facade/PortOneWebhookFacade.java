package com.example.ilgeobolkka.webhook.facade;

import com.example.ilgeobolkka.infra.portone.PortOneWebhookEvent;
import com.example.ilgeobolkka.infra.portone.PortOneWebhookVerifier;
import com.example.ilgeobolkka.ink.facade.InkPurchaseFacade;
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
        inkPurchaseFacade.completeWebhook(UUID.fromString(event.paymentId()));
    }
}
