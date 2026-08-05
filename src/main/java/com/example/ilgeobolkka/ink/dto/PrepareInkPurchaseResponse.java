package com.example.ilgeobolkka.ink.dto;

import com.example.ilgeobolkka.ink.entity.InkPurchase;

public record PrepareInkPurchaseResponse(
        String paymentId,
        String storeId,
        String channelKey,
        String orderName,
        int totalAmount,
        String currency) {

    public static PrepareInkPurchaseResponse from(
            InkPurchase purchase,
            String storeId,
            String channelKey,
            String orderName,
            String currency) {
        return new PrepareInkPurchaseResponse(
                purchase.getPaymentId().toString(),
                storeId,
                channelKey,
                orderName,
                purchase.getAmountWon(),
                currency);
    }
}
