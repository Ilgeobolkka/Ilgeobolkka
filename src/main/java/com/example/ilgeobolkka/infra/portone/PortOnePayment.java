package com.example.ilgeobolkka.infra.portone;

import java.time.Instant;

public record PortOnePayment(
        String paymentId,
        PortOnePaymentStatus status,
        long totalAmount,
        String currency,
        String storeId,
        String channelKey,
        String orderName,
        String version,
        Instant paidAt) {

    public static PortOnePayment notFound(String paymentId) {
        return new PortOnePayment(
                paymentId,
                PortOnePaymentStatus.NOT_FOUND,
                0,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
