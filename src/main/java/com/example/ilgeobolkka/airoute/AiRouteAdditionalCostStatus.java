package com.example.ilgeobolkka.airoute;

/**
 * 경로 항목 페이지를 여는 데 드는 추가 비용 상태.
 *
 * <p>생성 결과에서는 생성 시점 권한의 사본이고, 저장 경로 상세에서는 조회 시점의 현재 권한으로 다시 계산한
 * 값이다. 두 쓰임이 같은 API 계약을 공유하도록 어느 한쪽 패키지가 아니라 모듈 최상위에 둔다.
 */
public enum AiRouteAdditionalCostStatus {

    /** 활성 대여도 소장도 없어 페이지를 열 때 1잉크를 쓴다. */
    ONE_INK,

    /** 유효한 페이지 대여가 남아 있어 추가 잉크가 들지 않는다. */
    ACTIVE_RENTAL,

    /** 도서를 소장해 추가 잉크가 들지 않는다. */
    OWNED
}
