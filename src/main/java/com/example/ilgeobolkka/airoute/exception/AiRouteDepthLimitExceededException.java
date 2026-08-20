package com.example.ilgeobolkka.airoute.exception;

/**
 * 소장 경로의 후보·선수 묶음을 선택한 깊이의 페이지 상한 안에서 완성할 수 없다.
 *
 * <p>현재 G04는 이 경계를 {@code INSUFFICIENT_DEPTH} 조립 결과로 직접 반환한다. 이 타입은 기존 호출자
 * 정리 승인을 받기 전까지 호환 목적으로만 남겨 둔다.
 */
public class AiRouteDepthLimitExceededException extends RuntimeException {

    public AiRouteDepthLimitExceededException() {
        super("소장 경로를 선택한 깊이 상한 안에서 완성할 수 없습니다.");
    }
}
