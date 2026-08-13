package com.example.ilgeobolkka.airoute.service.current;

import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteCurrentRepository;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 독자·도서의 현재 경로를 다른 저장 경로로 옮긴다.
 *
 * <p>경로의 내용·항목·진행·피드백은 건드리지 않는다. 이 작업이 바꾸는 것은 {@code ai_route_current} 한
 * 행이 가리키는 대상뿐이다.
 *
 * <p>기록은 저장이 쓰는 upsert 를 그대로 쓴다. 같은 {@code (readerId, bookId)} 를 동시에 노린 요청은
 * 뒤에 온 쪽이 앞의 commit 을 기다렸다가 덮어쓰므로, 어떤 완료 시점에도 현재 경로는 최대 하나다.
 * 지정 전용 문장을 따로 두면 저장과 지정이 서로 다른 방식으로 같은 PK 를 다투게 된다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteCurrentService {

    private final AiReadingRouteRepository routeRepository;
    private final AiRouteCurrentRepository currentRepository;
    private final Clock clock;

    /**
     * 요청한 경로를 현재 경로로 지정한다.
     *
     * @throws AiRouteNotFoundException 인증 계정의 경로가 아니거나 요청한 도서의 경로가 아닐 때. 소유자가
     *     아닌 경우와 도서가 어긋난 경우를 같은 결과로 만들어, 남의 경로 식별자를 넣어 보는 것만으로
     *     존재 여부를 알 수 없게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void selectCurrent(long readerId, long bookId, long routeId) {
        AiReadingRoute route =
                routeRepository
                        .findOwnedByIdForUpdate(readerId, routeId)
                        .orElseThrow(() -> new AiRouteNotFoundException(routeId));
        if (!Objects.equals(route.getBookId(), bookId)) {
            throw new AiRouteNotFoundException(routeId);
        }

        currentRepository.selectAsCurrent(readerId, bookId, routeId, clock.instant());
    }
}
