package com.example.ilgeobolkka.reading.exception;

/**
 * 독자당 하나인 현재 {@code ReadingSession}이 다른 탭·기기의 새 뷰어 세션으로 이미 교체되어,
 * 요청이 들고 온 {@code viewerSessionId}가 더는 유효하지 않을 때 던진다.
 *
 * <p>세션 레코드 자체가 없는 {@link ReadingSessionNotFoundException}(404)과는 실패 원인이
 * 다르다. 이쪽은 세션은 존재하지만 다른 뷰어로 교체되었다는 뜻이므로 docs/api-spec.md
 * 415~417행의 계약대로 {@code 409 VIEWER_SESSION_REPLACED}로 구분해 응답해야 한다. 메시지에는
 * {@code viewerSessionId}를 포함하지 않는다(응답·로그 비노출 규칙).
 */
public class ViewerSessionReplacedException extends RuntimeException {

    public ViewerSessionReplacedException(long readerId) {
        super("현재 열람 세션이 다른 뷰어 세션으로 교체되었습니다: readerId=" + readerId);
    }
}
