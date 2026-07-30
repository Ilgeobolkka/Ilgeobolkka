package com.example.ilgeobolkka.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.BAD_REQUEST, "웹훅 서명이 유효하지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_CSRF_TOKEN(HttpStatus.FORBIDDEN, "CSRF 토큰이 유효하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    BOOK_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 소장한 도서입니다."),
    PAYMENT_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 결제 상태에서는 요청을 처리할 수 없습니다."),
    VIEWER_SESSION_REPLACED(HttpStatus.CONFLICT, "다른 위치에서 새로 열람을 시작해 현재 열람 세션이 만료되었습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "결제 검증에 실패했습니다."),
    INVALID_PAYMENT_AMOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "결제 금액이 올바르지 않습니다."),
    INSUFFICIENT_INK(HttpStatus.UNPROCESSABLE_CONTENT, "잉크가 부족합니다."),
    PAYMENT_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제사 조회가 일시적으로 불가능합니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
