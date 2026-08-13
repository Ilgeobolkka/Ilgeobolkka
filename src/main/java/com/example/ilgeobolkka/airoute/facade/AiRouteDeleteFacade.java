package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.service.delete.AiRouteDeleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 경로 삭제의 트랜잭션 경계. 포인터·항목·경로 삭제와 생성의 {@code CONSUMED} 전이, 후속 현재 경로
 * 지정이 모두 이 하나의 transaction 안에서 끝난다. 도중에 실패하면 아무것도 남지 않는다.
 *
 * <p>{@link Isolation#READ_COMMITTED} 로 여는 이유는 저장·현재 경로 지정과 같다. 세 경로가 같은
 * {@code (readerId, bookId)} 현재 경로를 다투므로 격리 수준을 맞춘다.
 *
 * <p>{@code ai-route.enabled} 로 막는다. 기능을 끈 서버는 목표 JSON 경로를 등록하지 않는다는 계약이라
 * Controller 와 짝으로 조건을 건다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteDeleteFacade {

    private final AiRouteDeleteService aiRouteDeleteService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteRoute(long readerId, long routeId) {
        aiRouteDeleteService.delete(readerId, routeId);
    }
}
