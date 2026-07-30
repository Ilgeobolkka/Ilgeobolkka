package com.example.ilgeobolkka.infra.portone;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.portone.sdk.server.webhook.WebhookVerifier;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PortOneWebhookVerifierTest {

    private static final byte[] SECRET = "test-webhook-secret".getBytes(UTF_8);
    private static final String PAYMENT_ID = "d574ffb6-10ff-4b4e-8840-68a6f5012685";

    private final PortOneWebhookVerifier verifier =
            new PortOneWebhookVerifier(new WebhookVerifier(SECRET));

    @Test
    void 유효한_PAID와_FAILED_웹훅을_내부_이벤트로_변환한다() throws Exception {
        SignedWebhook paid = 서명한다(웹훅_본문("Transaction.Paid", PAYMENT_ID));
        SignedWebhook failed = 서명한다(웹훅_본문("Transaction.Failed", PAYMENT_ID));

        PortOneWebhookEvent paidEvent = verifier.verify(
                paid.body(), paid.id(), paid.signature(), paid.timestamp());
        PortOneWebhookEvent failedEvent = verifier.verify(
                failed.body(), failed.id(), failed.signature(), failed.timestamp());

        assertAll(
                () -> assertEquals(PortOneWebhookEvent.Type.PAID, paidEvent.type()),
                () -> assertEquals(PAYMENT_ID, paidEvent.paymentId()),
                () -> assertEquals(PortOneWebhookEvent.Type.FAILED, failedEvent.type()),
                () -> assertEquals(PAYMENT_ID, failedEvent.paymentId()));
    }

    @Test
    void 정상_서명의_미지원_이벤트는_내부_미지원_유형으로_변환한다() throws Exception {
        SignedWebhook signed =
                서명한다(웹훅_본문("Transaction.PayPending", PAYMENT_ID));

        PortOneWebhookEvent event = verifier.verify(
                signed.body(), signed.id(), signed.signature(), signed.timestamp());

        assertAll(
                () -> assertEquals(PortOneWebhookEvent.Type.UNSUPPORTED, event.type()),
                () -> assertNull(event.paymentId()));
    }

    @Test
    void 서명_헤더가_누락되거나_서명이_다르면_검증_예외로_변환한다() throws Exception {
        String body = 웹훅_본문("Transaction.Paid", PAYMENT_ID);
        SignedWebhook signed = 서명한다(body);

        assertAll(
                () -> assertThrows(
                        PortOneWebhookVerificationException.class,
                        () -> verifier.verify(body, null, null, null)),
                () -> assertThrows(
                        PortOneWebhookVerificationException.class,
                        () -> verifier.verify(
                                body,
                                signed.id(),
                                "v1,aW52YWxpZA==",
                                signed.timestamp())));
    }

    private SignedWebhook 서명한다(String body) throws Exception {
        String id = "webhook-test";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        byte[] signature = mac.doFinal((id + "." + timestamp + "." + body).getBytes(UTF_8));
        return new SignedWebhook(
                body,
                id,
                "v1," + Base64.getEncoder().encodeToString(signature),
                timestamp);
    }

    private String 웹훅_본문(String type, String paymentId) {
        return """
                {
                  "type": "%s",
                  "timestamp": "2026-07-30T03:00:00Z",
                  "data": {
                    "storeId": "store-test",
                    "paymentId": "%s",
                    "transactionId": "transaction-test"
                  }
                }
                """.formatted(type, paymentId);
    }

    private record SignedWebhook(
            String body,
            String id,
            String signature,
            String timestamp) {
    }
}
