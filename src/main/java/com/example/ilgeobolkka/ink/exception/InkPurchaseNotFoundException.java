package com.example.ilgeobolkka.ink.exception;

public class InkPurchaseNotFoundException extends RuntimeException {

    public InkPurchaseNotFoundException() {
        super("잉크 구매 기록을 찾을 수 없습니다.");
    }
}
