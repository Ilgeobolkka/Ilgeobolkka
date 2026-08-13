package com.example.ilgeobolkka.airoute.exception;

/** 도서 지원·외부 전송 권리·데이터 정책 프로필 중 하나가 현재 서버 계약과 맞지 않는다. */
public class AiRouteNotSupportedException extends RuntimeException {

    public AiRouteNotSupportedException(long bookId) {
        super("AI 경로 생성을 지원하지 않는 도서 또는 정책입니다. bookId=" + bookId);
    }
}
