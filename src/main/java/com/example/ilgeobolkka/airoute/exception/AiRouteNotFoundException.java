package com.example.ilgeobolkka.airoute.exception;

/**
 * 인증 계정이 소유한 저장 경로를 찾지 못했다.
 *
 * <p>없는 경로와 다른 독자의 경로를 구분하지 않는다. 둘을 다른 응답으로 나누면 남의 경로 식별자를 넣어 보는
 * 것만으로 존재 여부를 알 수 있다.
 */
public class AiRouteNotFoundException extends RuntimeException {

    public AiRouteNotFoundException(long routeId) {
        super("소유한 저장 경로를 찾을 수 없습니다. routeId=" + routeId);
    }
}
