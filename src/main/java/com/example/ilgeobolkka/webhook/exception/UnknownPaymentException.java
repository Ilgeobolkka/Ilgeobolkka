package com.example.ilgeobolkka.webhook.exception;

import java.util.UUID;

public class UnknownPaymentException extends RuntimeException {

    public UnknownPaymentException(UUID paymentId) {
        super("잉크 구매·소장 결제 어디에도 없는 결제입니다. paymentId=" + paymentId);
    }
}
