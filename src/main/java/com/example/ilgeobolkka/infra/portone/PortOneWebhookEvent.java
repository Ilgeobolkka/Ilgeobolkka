package com.example.ilgeobolkka.infra.portone;

public record PortOneWebhookEvent(
        Type type,
        String paymentId) {

    public enum Type {
        PAID,
        FAILED,
        UNSUPPORTED
    }
}
