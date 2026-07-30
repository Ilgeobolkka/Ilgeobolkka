package com.example.ilgeobolkka.ink.exception;

import com.example.ilgeobolkka.global.exception.ErrorCode;

public class PaymentVerificationException extends RuntimeException {

    private final ErrorCode errorCode;

    public PaymentVerificationException(ErrorCode errorCode) {
        super(errorCode.message());
        if (errorCode != ErrorCode.PAYMENT_VERIFICATION_FAILED
                && errorCode != ErrorCode.INVALID_PAYMENT_AMOUNT) {
            throw new IllegalArgumentException("결제 검증 오류 코드만 사용할 수 있습니다.");
        }
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
