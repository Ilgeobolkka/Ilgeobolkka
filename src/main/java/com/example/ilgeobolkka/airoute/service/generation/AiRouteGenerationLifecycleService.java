package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GENERATING} 이후의 상태 전이와 유효 조회를 맡는다. 상태를 바꾸는 문은 이 서비스뿐이며 후속
 * 작업은 Entity 의 status 를 직접 건드리지 않는다.
 *
 * <p>여기 transaction 은 모두 짧다. 외부 호출을 감싸면 OpenAI 응답을 기다리는 동안 생성 행 잠금을 쥐게
 * 되므로, 호출자는 외부 호출을 끝낸 뒤에 부른다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteGenerationLifecycleService {

    /**
     * 완료 시점부터 임시 결과를 보관하는 기간. 이 값을 완료 시각에 더한 것이 {@code expiresAt} 이다.
     * 만료 뒤에는 결과도 멱등 상태도 남기지 않는다.
     */
    public static final Duration RESULT_RETENTION = Duration.ofMinutes(15);

    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final Clock clock;

    /**
     * 경로를 찾은 결과로 완료한다. 항목은 이 전이에서만 저장한다.
     *
     * @throws IllegalArgumentException 항목이 비었을 때. 항목 없는 {@code ROUTE} 는 경로가 없다는 뜻이라
     *     {@code NO_ROUTE} 로 완료해야 한다.
     * @throws IllegalStateException {@code GENERATING} 이 아닐 때
     */
    @Transactional
    public void completeWithRoute(UUID generationId, List<AiRouteResultItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("경로 결과에는 항목이 하나 이상 필요합니다.");
        }

        AiRouteGeneration generation = lockForTransition(generationId);
        Instant completedAt = clock.instant();
        generation.completeRoute(completedAt, completedAt.plus(RESULT_RETENTION));

        generationItemRepository.saveAll(
                items.stream().map(item -> toEntity(generation, item)).toList());
    }

    /**
     * 관련 경로가 없는 결과로 완료한다. 사유와 조건부 최소 잉크의 조합은 Entity 가 검사한다.
     *
     * @throws IllegalStateException {@code GENERATING} 이 아닐 때
     */
    @Transactional
    public void completeWithoutRoute(
            UUID generationId, AiRouteNoRouteReason reason, Integer minimumRequiredInk) {
        AiRouteGeneration generation = lockForTransition(generationId);
        Instant completedAt = clock.instant();
        generation.completeNoRoute(
                reason, minimumRequiredInk, completedAt, completedAt.plus(RESULT_RETENTION));
    }

    /**
     * 실패로 완료한다. {@code failureCode} 는 공급자 정보를 담지 않는 공개 코드여야 한다.
     *
     * <p>실패도 성공과 같은 기간 동안 보관한다. 보관 중 같은 멱등 키 재시도가 최초 오류를 그대로 받아야
     * 하고, 그래야 외부 호출을 다시 시작하지 않는다.
     *
     * @throws IllegalStateException {@code GENERATING} 이 아닐 때
     */
    @Transactional
    public void fail(UUID generationId, String failureCode) {
        AiRouteGeneration generation = lockForTransition(generationId);
        Instant completedAt = clock.instant();
        generation.fail(failureCode, completedAt, completedAt.plus(RESULT_RETENTION));
    }

    /**
     * 소유자의 아직 유효한 생성을 읽는다. 다른 독자의 식별자와 만료한 식별자는 모두 빈 결과다. 호출자는
     * 둘을 구분하지 않고 같은 404 로 응답한다.
     *
     * <p>만료 판정은 정리 배치 실행 여부와 무관하다. 만료 시각을 지난 행은 아직 지워지지 않았어도 여기서
     * 보이지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<AiRouteGenerationView> findOwnedResult(UUID generationId, long readerId) {
        return generationRepository
                .findOwnedNotExpired(generationId, readerId, clock.instant())
                .map(AiRouteGenerationView::from);
    }

    private AiRouteGenerationItem toEntity(AiRouteGeneration generation, AiRouteResultItem item) {
        return AiRouteGenerationItem.create(
                generation.getGenerationId(),
                generation.getBookId(),
                item.bookPageId(),
                item.position(),
                item.relevance(),
                item.prerequisite(),
                item.role());
    }

    private AiRouteGeneration lockForTransition(UUID generationId) {
        return generationRepository
                .findByGenerationIdForUpdate(generationId)
                .orElseThrow(() -> new AiRouteGenerationNotFoundException(generationId));
    }
}
