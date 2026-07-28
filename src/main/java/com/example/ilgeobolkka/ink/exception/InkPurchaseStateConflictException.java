package com.example.ilgeobolkka.ink.exception;

public class InkPurchaseStateConflictException extends RuntimeException {

    public InkPurchaseStateConflictException() {
        super("결제가 완료되지 않은 잉크 구매입니다.");
    }
}
