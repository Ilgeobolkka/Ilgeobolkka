package com.example.ilgeobolkka.ownership.exception;

import java.util.UUID;

public class OwnershipPaymentNotFoundException extends RuntimeException {

    public OwnershipPaymentNotFoundException(UUID paymentId) {
        super("소장 결제를 찾을 수 없습니다. paymentId=" + paymentId);
    }
}
