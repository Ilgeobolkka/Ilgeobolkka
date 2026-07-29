package com.example.ilgeobolkka.ink.exception;

public class InsufficientInkException extends RuntimeException {

    public InsufficientInkException() {
        super("잉크 잔액이 부족합니다.");
    }
}
