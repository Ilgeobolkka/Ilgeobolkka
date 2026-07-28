package com.example.ilgeobolkka.ink.exception;

public class InkBalanceOverflowException extends RuntimeException {

    public InkBalanceOverflowException() {
        super("잉크 잔액이 저장 가능한 범위를 초과했습니다.");
    }
}
