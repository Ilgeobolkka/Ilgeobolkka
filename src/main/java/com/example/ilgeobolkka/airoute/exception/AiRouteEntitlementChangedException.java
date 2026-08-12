package com.example.ilgeobolkka.airoute.exception;

import java.util.UUID;

/**
 * 저장 시점에 다시 계산한 추가 잉크가 생성 예산을 넘었다.
 *
 * <p>입력이 아니라 서버가 들고 있는 대여·소장 권한이 생성 시점과 달라진 충돌이라 409 다. 콘텐츠 버전
 * 변경·입력 오류·잉크 부족과는 원인이 달라 그 코드로 대신하지 않는다.
 *
 * <p>메시지에 재계산한 비용과 어느 페이지가 유료인지를 넣지 않는다. 응답 바디는 {@code code}·
 * {@code message}뿐이고 그 둘은 권한 상세를 담지 않는다는 것이 GATE-AIR-03 의 결정이다. 로그도 예외 타입만
 * 남기므로 여기 문자열이 곧 노출 경계다.
 */
public class AiRouteEntitlementChangedException extends RuntimeException {

    public AiRouteEntitlementChangedException(UUID generationId) {
        super("저장 시점 권한이 생성 예산을 넘었습니다. generationId=" + generationId);
    }
}
