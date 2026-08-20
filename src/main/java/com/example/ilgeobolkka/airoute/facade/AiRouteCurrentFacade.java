package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.service.current.AiRouteCurrentService;
import com.example.ilgeobolkka.airoute.service.query.AiRouteQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 경로 지정의 트랜잭션 경계. 지정과 그 결과 응답을 한 transaction 에서 끝낸다.
 *
 * <p>{@link Isolation#READ_COMMITTED} 로 여는 이유는 저장과 같다. 현재 경로 upsert 가 없는 행을 잠금
 * 조회하지 않는다는 전제 위에 서 있고, 응답의 {@code additionalCostStatus} 는 지금 커밋된 대여·소장을
 * 읽어야 한다. 두 경로가 같은 PK 를 다투므로 격리 수준도 맞춘다.
 *
 * <p>응답은 {@link AiRouteQueryService} 로 만든다. 지정 성공 응답은 저장 경로 상세와 같은 계약이라,
 * 여기서 따로 조립하면 같은 계약을 두 곳에서 만들게 된다.
 *
 * <p>{@code ai-route.enabled} 로 막는다. 기능을 끈 서버는 목표 JSON 경로를 등록하지 않는다는 계약이라
 * Controller 와 짝으로 조건을 건다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteCurrentFacade {

    private final AiRouteCurrentService aiRouteCurrentService;
    private final AiRouteQueryService aiRouteQueryService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FindAiRouteResponse changeCurrent(long readerId, long bookId, long routeId) {
        aiRouteCurrentService.selectCurrent(readerId, bookId, routeId);

        return aiRouteQueryService.getRoute(readerId, routeId);
    }
}
