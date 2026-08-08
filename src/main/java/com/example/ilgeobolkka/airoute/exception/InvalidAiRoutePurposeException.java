package com.example.ilgeobolkka.airoute.exception;

/**
 * 독서 목적을 정규화한 결과를 쓸 수 없다. 값이 없거나, 공백·보이지 않는 문자를 빼면 내용이 남지 않거나,
 * code point 200개를 넘은 경우다. 원문은 메시지에 담지 않는다.
 */
public class InvalidAiRoutePurposeException extends RuntimeException {

    public InvalidAiRoutePurposeException(String reason) {
        super("독서 목적 입력이 올바르지 않습니다. " + reason);
    }
}
