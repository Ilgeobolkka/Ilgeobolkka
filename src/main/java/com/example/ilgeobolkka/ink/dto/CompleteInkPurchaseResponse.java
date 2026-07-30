package com.example.ilgeobolkka.ink.dto;

import com.example.ilgeobolkka.ink.entity.InkPurchase;
import com.example.ilgeobolkka.ink.entity.InkPurchaseStatus;

public record CompleteInkPurchaseResponse(
        String paymentId,
        InkPurchaseStatus status,
        int grantedInk,
        int inkBalance) {

    public static CompleteInkPurchaseResponse paid(InkPurchase purchase, int inkBalance) {
        return new CompleteInkPurchaseResponse(
                purchase.getPaymentId().toString(),
                InkPurchaseStatus.PAID,
                purchase.getGrantedInk(),
                inkBalance);
    }

    public static CompleteInkPurchaseResponse pending(InkPurchase purchase, int inkBalance) {
        return new CompleteInkPurchaseResponse(
                purchase.getPaymentId().toString(),
                InkPurchaseStatus.PENDING,
                0,
                inkBalance);
    }
}
