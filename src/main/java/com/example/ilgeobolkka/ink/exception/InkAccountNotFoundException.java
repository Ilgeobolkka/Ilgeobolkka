package com.example.ilgeobolkka.ink.exception;

public class InkAccountNotFoundException extends RuntimeException {

    public InkAccountNotFoundException(long readerId) {
        super("잉크 계좌를 찾을 수 없습니다: " + readerId);
    }
}
