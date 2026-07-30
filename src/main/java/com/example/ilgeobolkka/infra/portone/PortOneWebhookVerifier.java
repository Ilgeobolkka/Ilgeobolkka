package com.example.ilgeobolkka.infra.portone;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransactionFailed;
import io.portone.sdk.server.webhook.WebhookTransactionPaid;
import io.portone.sdk.server.webhook.WebhookVerifier;

public class PortOneWebhookVerifier {

    private final WebhookVerifier webhookVerifier;

    public PortOneWebhookVerifier(WebhookVerifier webhookVerifier) {
        this.webhookVerifier = webhookVerifier;
    }

    public PortOneWebhookEvent verify(
            String rawBody,
            String webhookId,
            String webhookSignature,
            String webhookTimestamp) {
        try {
            Webhook webhook = webhookVerifier.verify(
                    rawBody,
                    webhookId,
                    webhookSignature,
                    webhookTimestamp);
            if (webhook instanceof WebhookTransactionPaid paid) {
                return new PortOneWebhookEvent(
                        PortOneWebhookEvent.Type.PAID,
                        paid.getData().getPaymentId());
            }
            if (webhook instanceof WebhookTransactionFailed failed) {
                return new PortOneWebhookEvent(
                        PortOneWebhookEvent.Type.FAILED,
                        failed.getData().getPaymentId());
            }
            return new PortOneWebhookEvent(
                    PortOneWebhookEvent.Type.UNSUPPORTED,
                    null);
        } catch (WebhookVerificationException exception) {
            throw new PortOneWebhookVerificationException(exception);
        }
    }
}
