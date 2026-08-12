package com.example.ilgeobolkka.airoute.exception;

import java.util.UUID;

/**
 * 생성 시점의 콘텐츠 버전과 지금 도서의 콘텐츠 버전이 다르다.
 *
 * <p>2차 MVP 는 사용자 기록이 있는 환경에서 {@code contentVersion} 을 재발급하지 않으므로 도달 가능한
 * 사용자 경로가 아니다. 그래도 저장은 생성 시점 페이지 구성을 그대로 옮기는 작업이라, 구성이 가리키는
 * 콘텐츠가 바뀌었는데 저장을 진행하면 조용히 어긋난 경로가 남는다. 계약을 지우지 않고 불변식 방어로 둔다.
 *
 * <p>메시지는 응답으로 나가지 않는다. 응답 바디는 {@code ErrorCode#message} 를 쓰고 실패 로그는 예외
 * 타입만 남긴다. 운영에서 {@code generationId} 를 찾을 때는 같은 {@code requestId} 로 남은 요청 로그의
 * {@code path} 를 본다.
 */
public class AiRouteContentChangedException extends RuntimeException {

    public AiRouteContentChangedException(UUID generationId) {
        super("생성 시점과 도서의 콘텐츠 버전이 다릅니다. generationId=" + generationId);
    }
}
