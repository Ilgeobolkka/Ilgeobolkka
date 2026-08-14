package com.example.ilgeobolkka.airoute.exception;

import com.example.ilgeobolkka.global.exception.ErrorCode;

/** 생성 실행의 HTTP 공개 오류 코드와 선택적인 Retry-After 초를 전달한다. */
public class AiRouteGenerationApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;

    public AiRouteGenerationApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public AiRouteGenerationApiException(ErrorCode errorCode, Long retryAfterSeconds) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
