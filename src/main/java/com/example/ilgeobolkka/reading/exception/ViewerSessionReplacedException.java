package com.example.ilgeobolkka.reading.exception;

/**
 * 요청한 {@code viewerSessionId}가 현재 열람 세션과 다를 때 던진다.
 * 뷰어 세션 ID는 로그와 오류 응답에 남기지 않으므로 메시지에 포함하지 않는다.
 */
public class ViewerSessionReplacedException extends RuntimeException {

    public ViewerSessionReplacedException(long readerId) {
        super("열람 세션이 새 뷰어로 교체되었습니다: " + readerId);
    }
}
