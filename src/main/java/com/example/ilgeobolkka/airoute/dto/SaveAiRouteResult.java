package com.example.ilgeobolkka.airoute.dto;

/**
 * 저장 응답 바디와 그 바디를 201 로 낼지 200 으로 낼지.
 *
 * <p>바디는 저장 경로 상세와 같은 {@link FindAiRouteResponse} 다. 첫 저장과 재시도가 같은 바디를 주고
 * 상태 코드만 다르다는 것이 계약이라, 만들었는지 여부는 바디 밖에 둔다.
 */
public record SaveAiRouteResult(FindAiRouteResponse route, boolean created) {}
