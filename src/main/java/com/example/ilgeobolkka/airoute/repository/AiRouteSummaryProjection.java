package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import java.time.Instant;

/**
 * 저장 경로 한 건의 소유자 조회 결과.
 *
 * <p>목록과 상세가 같은 머리 정보를 쓰므로 하나로 둔다. 상세는 여기에 {@link AiRouteItemProjection} 목록만
 * 더한다.
 *
 * <p>{@code currentRouteId}는 현재 경로 여부를 {@code null} 여부로 전달한다. 값 자체는 언제나 조회한 경로의
 * id 라서 응답에 싣지 않는다. HQL {@code CASE}가 돌려주는 boolean 의 매핑 타입에 기대지 않으려고 left join
 * 컬럼을 그대로 꺼낸다.
 *
 * <p>분석 text·embedding·선수 개념 그래프와 생성 fingerprint 는 조회 계약에 없으므로 여기에 두지 않는다.
 */
public interface AiRouteSummaryProjection {

    long getRouteId();

    long getBookId();

    String getBookTitle();

    String getPurpose();

    Long getCurrentRouteId();

    Instant getCreatedAt();

    Instant getCompletedAt();

    AiReadingRouteFeedback getRating();
}
