package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.dto.FindAiRoutesResponse;
import com.example.ilgeobolkka.airoute.service.query.AiRouteQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 경로 조회의 트랜잭션 경계.
 *
 * <p>{@code ai-route.enabled} 로 막는다. 기능을 끈 서버는 목표 JSON 경로를 등록하지 않는다는 계약이라
 * Controller 와 짝으로 조건을 건다. 반면
 * {@link com.example.ilgeobolkka.airoute.service.query.AiRouteQueryService}는 막지 않는다. 이후 저장 경로
 * 화면과 현재 경로 지정·콘텐츠 제공이 같은 조회를 재사용하는데, 서비스까지 조건부가 되면 그쪽도 모두 조건부
 * 빈이 되어야 한다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteQueryFacade {

    private final AiRouteQueryService aiRouteQueryService;

    @Transactional(readOnly = true)
    public FindAiRoutesResponse findRoutes(long readerId, int page) {
        return aiRouteQueryService.getRoutes(readerId, page);
    }

    @Transactional(readOnly = true)
    public FindAiRouteResponse findRoute(long readerId, long routeId) {
        return aiRouteQueryService.getRoute(readerId, routeId);
    }
}
