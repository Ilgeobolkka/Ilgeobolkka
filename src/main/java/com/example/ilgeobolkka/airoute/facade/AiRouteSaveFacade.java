package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.dto.SaveAiRouteResult;
import com.example.ilgeobolkka.airoute.service.query.AiRouteQueryService;
import com.example.ilgeobolkka.airoute.service.save.AiRouteSaveService;
import com.example.ilgeobolkka.airoute.service.save.SavedAiRoute;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장의 트랜잭션 경계. 경로·항목·현재 경로 지정과 G06 의 {@code SAVED} 전이가 모두 이 하나의 transaction
 * 안에서 끝난다.
 *
 * <p>{@link Isolation#READ_COMMITTED} 로 여는 이유는 저장 판단이 잠금 전 스냅샷이 아니라 지금 커밋된
 * 권한을 읽어야 하기 때문이다. InnoDB 기본값인 REPEATABLE READ 에서는 생성 행을 잠근 뒤에 읽는 소장·대여도
 * transaction 이 시작될 때 고정한 read view 를 보므로, 그사이 만료·성립한 대여가 보이지 않아 재계산이
 * 저장 시점 권한이 아니게 된다. 페이지 열기가 같은 이유로 같은 격리 수준을 쓴다.
 *
 * <p>응답은 {@link AiRouteQueryService} 로 만든다. 저장 성공 응답은 저장 경로 상세와 같은 계약이라,
 * 여기서 따로 조립하면 같은 계약을 두 곳에서 만들게 된다.
 *
 * <p>{@code ai-route.enabled} 로 막는다. 기능을 끈 서버는 목표 JSON 경로를 등록하지 않는다는 계약이라
 * Controller 와 짝으로 조건을 건다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteSaveFacade {

    private final AiRouteSaveService aiRouteSaveService;
    private final AiRouteQueryService aiRouteQueryService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SaveAiRouteResult saveRoute(long readerId, UUID generationId) {
        SavedAiRoute saved = aiRouteSaveService.save(readerId, generationId);
        FindAiRouteResponse route = aiRouteQueryService.getRoute(readerId, saved.routeId());

        return new SaveAiRouteResult(route, saved.created());
    }
}
