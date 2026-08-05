package com.example.ilgeobolkka.ownership.dto;

import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;

public record PrepareOwnershipPaymentResponse(
        String paymentId,
        String storeId,
        String channelKey,
        String orderName,
        int totalAmount,
        String currency) {

    public static PrepareOwnershipPaymentResponse from(
            OwnershipPayment payment,
            String storeId,
            String channelKey,
            String orderName,
            String currency) {
        return new PrepareOwnershipPaymentResponse(
                payment.getPaymentId().toString(),
                storeId,
                channelKey,
                orderName,
                payment.getAmountWon(),
                currency);
    }
}
