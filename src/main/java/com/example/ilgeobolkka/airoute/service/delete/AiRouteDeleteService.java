package com.example.ilgeobolkka.airoute.service.delete;

import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteCurrentRepository;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 경로 하나를 지우고, 그 삭제로 비는 자리를 같은 transaction 에서 메운다.
 *
 * <p>지우는 것은 경로가 들고 있던 것뿐이다. 목적·항목·열람 시각·피드백은 경로 행과 항목 행에 있어 함께
 * 사라지고, 페이지 대여와 잉크 원장·서재·열람 세션은 건드리지 않으며 환불도 하지 않는다.
 *
 * <p>잠금은 경로 → 생성 → 현재 경로 순으로 잡는다. 저장이 생성 → 현재 경로 순으로 잡으므로 두 경로가
 * 현재 경로보다 생성 행을 먼저 잠그고, 저장은 기존 경로 행을 잠그지 않아 대기 고리가 만들어지지 않는다.
 * 그래서 상태 전이가 삭제 문장들보다 앞에 있다. 지우는 순서 자체는 정본이 정한 대로 현재 포인터 → 항목
 * → 경로다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteDeleteService {

    private final AiReadingRouteRepository routeRepository;
    private final AiReadingRouteItemRepository routeItemRepository;
    private final AiRouteCurrentRepository currentRepository;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final Clock clock;

    /**
     * @throws AiRouteNotFoundException 인증 계정의 경로가 아닐 때. 이미 지운 경로의 재시도도 같은 결과라,
     *     재시도가 다른 경로나 생성을 건드리지 않고 404 계약을 그대로 따른다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(long readerId, long routeId) {
        AiReadingRoute route =
                routeRepository
                        .findOwnedByIdForUpdate(readerId, routeId)
                        .orElseThrow(() -> new AiRouteNotFoundException(routeId));
        long bookId = route.getBookId();
        UUID generationId = route.getGenerationId();

        // 상태 전이는 G06 만 한다. 남아 있는 멱등 행만 CONSUMED 가 되고, 이미 정리됐으면 아무 일도 없다.
        // 전이가 saved_route_id 를 비우므로 경로를 지우기 전에 나가야 한다. 뒤따르는 삭제 문장이
        // flush 를 앞세워 그 순서를 지킨다.
        lifecycleService.markConsumed(generationId);

        // 빈 결과는 정상 경로에 없다. 저장이 경로를 만들 때마다 포인터를 함께 쓰고 삭제가 지운 포인터
        // 자리를 같은 transaction 에서 메우므로, 경로가 있으면 포인터도 있다. 그 불변식이 깨진 데이터에서는
        // 이 잠금이 하나도 잡히지 않아 같은 도서의 삭제들이 직렬화되지 않는다.
        boolean wasCurrent =
                currentRepository
                        .findRouteIdForUpdate(readerId, bookId)
                        .filter(currentRouteId -> currentRouteId.longValue() == routeId)
                        .isPresent();
        if (wasCurrent) {
            currentRepository.clearCurrent(readerId, bookId);
        }
        routeItemRepository.deleteByRouteId(routeId);
        routeRepository.delete(route);
        routeRepository.flush();

        if (wasCurrent) {
            selectNextCurrent(readerId, bookId);
        }
    }

    /**
     * 현재 경로를 지운 자리를 같은 도서에 남은 가장 최근 경로로 메운다. 남은 경로가 없으면 현재 경로도
     * 없는 상태로 둔다.
     *
     * <p>경로 행을 지우고 flush 한 뒤에 읽으므로 방금 지운 경로가 다시 뽑히지 않는다.
     */
    private void selectNextCurrent(long readerId, long bookId) {
        List<Long> nextRouteIds =
                routeRepository.findRouteIdsByReaderIdAndBookId(
                        readerId, bookId, PageRequest.of(0, 1));
        if (nextRouteIds.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        currentRepository.selectAsCurrent(readerId, bookId, nextRouteIds.getFirst(), now);
    }
}
