package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import java.time.Instant;

/**
 * 저장 경로 항목 한 건의 소유자 조회 결과.
 *
 * <p>{@code bookPageId}는 응답 필드가 아니라 추가 비용 상태를 다시 계산할 때 쓰는 조회 키다. 계약이 노출하는
 * 페이지 식별자는 원본 PDF {@code pageNumber} 뿐이다.
 *
 * <p>{@code guideTopic}·{@code estimatedReadingSeconds}는 저장된 검수 값이고, 응답의 {@code guide}·
 * {@code estimatedMinutes}는 이것을 고정 템플릿과 최소 시간 규칙에 넣어 만든다. 페이지 본문과 비공개 분석
 * 텍스트는 여기에 담지 않는다.
 */
public interface AiRouteItemProjection {

    int getPosition();

    long getBookPageId();

    int getPageNumber();

    AiRouteItemRelevance getRelevance();

    boolean isPrerequisite();

    AiRouteItemRole getRole();

    Instant getOpenedAt();

    String getGuideTopic();

    Integer getEstimatedReadingSeconds();
}
