package com.example.ilgeobolkka.global.exception;

public record ApiErrorResponse(String code, String message) {

    public static ApiErrorResponse from(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.name(), errorCode.message());
    }
}
