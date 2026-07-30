package com.example.ilgeobolkka.ink.exception;

public class PaymentStateConflictException extends RuntimeException {

    public PaymentStateConflictException() {
        super("현재 결제 상태에서는 완료 요청을 처리할 수 없습니다.");
    }
}
