package com.example.ilgeobolkka.airoute.exception;

/**
 * 소장 경로의 후보·선수 묶음을 선택한 깊이의 페이지 상한 안에서 완성할 수 없다.
 *
 * <p>G07이 이 예외를 공개 정상 결과 계약으로 변환하는 작업은 SCRUM-486에서 추적한다.
 */
public class AiRouteDepthLimitExceededException extends RuntimeException {

    public AiRouteDepthLimitExceededException() {
        super("소장 경로를 선택한 깊이 상한 안에서 완성할 수 없습니다.");
    }
}
