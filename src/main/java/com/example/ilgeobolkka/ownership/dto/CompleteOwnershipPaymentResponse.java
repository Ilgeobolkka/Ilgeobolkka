package com.example.ilgeobolkka.ownership.dto;

import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;

public record CompleteOwnershipPaymentResponse(
        String paymentId,
        OwnershipPaymentStatus status,
        long bookId,
        boolean owned) {

    public static CompleteOwnershipPaymentResponse paid(OwnershipPayment payment) {
        return new CompleteOwnershipPaymentResponse(
                payment.getPaymentId().toString(),
                OwnershipPaymentStatus.PAID,
                payment.getBookId(),
                true);
    }

    public static CompleteOwnershipPaymentResponse pending(OwnershipPayment payment) {
        return new CompleteOwnershipPaymentResponse(
                payment.getPaymentId().toString(),
                OwnershipPaymentStatus.PENDING,
                payment.getBookId(),
                false);
    }
}
