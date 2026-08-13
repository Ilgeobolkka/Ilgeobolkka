package com.example.ilgeobolkka.airoute.exception;

/** 도서 지원·외부 전송 권리·데이터 정책·후보 페이지 메타데이터 중 하나가 현재 서버 계약과 맞지 않는다. */
public class AiRouteNotSupportedException extends RuntimeException {

    public AiRouteNotSupportedException(long bookId, String reason) {
        super("AI 경로 생성을 지원하지 않습니다. bookId=" + bookId + ", reason=" + reason);
    }
}
