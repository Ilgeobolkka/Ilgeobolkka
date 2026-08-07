package com.example.ilgeobolkka.airoute.exception;

/** 정규화한 독서 목적이 code point 1~200 범위를 벗어났다. 원문은 메시지에 담지 않는다. */
public class InvalidAiRoutePurposeException extends RuntimeException {

    public InvalidAiRoutePurposeException(String reason) {
        super("독서 목적 입력이 올바르지 않습니다. " + reason);
    }
}
