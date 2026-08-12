package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
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
     * 저장 경로로 전환한다. 임시 목적·입력은 Entity 가 지우고, 임시 항목은 여기서 지운다. 멱등 키·요청
     * 지문·저장 경로 식별자는 원래 {@code expiresAt} 까지 그대로 남아, 그 사이 같은 키로 다시 생성을
     * 요청해도 저장된 경로를 돌려줄 수 있다.
     *
     * <p>소유자 확인은 Entity 가 한다. 넘어온 경로의 독자·도서·요청이 생성과 하나라도 다르면 거부한다.
     *
     * @throws AiRouteGenerationNotFoundException 생성이 없거나 이미 만료했을 때. 만료한 식별자와 남의
     *     식별자를 같은 결과로 만들어 존재 여부가 응답에서 갈리지 않게 한다.
     * @throws IllegalStateException {@code ROUTE} 가 아닐 때
     */
    @Transactional
    public void markSaved(UUID generationId, AiReadingRoute savedRoute) {
        AiRouteGeneration generation = lockForTransition(generationId);
        requireNotExpired(generation);

        generation.saveAsRoute(savedRoute);
        generationItemRepository.deleteByGenerationId(generationId);
    }

    /**
     * 저장 경로가 삭제됐음을 남긴다. 저장 경로 식별자를 비워 같은 생성으로 다시 저장하지 못하게 한다.
     *
     * <p>임시 상태가 이미 정리됐으면 아무 일도 하지 않는다. 저장 경로는 기한이 없어 15분이 한참 지난
     * 뒤에도 삭제될 수 있는데, 그때 남아 있지 않은 멱등 상태 때문에 경로 삭제가 실패하면 안 된다.
     *
     * @throws IllegalStateException 남아 있는데 {@code SAVED} 가 아닐 때
     */
    @Transactional
    public void markConsumed(UUID generationId) {
        generationRepository
                .findByGenerationIdForUpdate(generationId)
                .ifPresent(AiRouteGeneration::consume);
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
                .map(generation -> AiRouteGenerationView.from(generation, itemsOf(generation)));
    }

    /** 항목은 {@code ROUTE} 에서만 남는다. 다른 상태에 조회를 날려도 늘 비어 있으므로 아예 묻지 않는다. */
    private List<AiRouteGenerationItem> itemsOf(AiRouteGeneration generation) {
        if (generation.getStatus() != AiRouteGenerationStatus.ROUTE) {
            return List.of();
        }
        return generationItemRepository.findAllOrderedByGenerationId(generation.getGenerationId());
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

    /**
     * 만료 판정을 정리 배치 실행 여부에 기대지 않는다. 만료 시각을 지난 행은 아직 지워지지 않았어도 없는
     * 것으로 본다. 경계는 {@code now < expiresAt} 이 유효다.
     *
     * <p>{@code expiresAt} 이 {@code null} 인 {@code GENERATING} 은 만료 대상이 아니다. 이 검사를 지나가도
     * 뒤따르는 Entity 전이가 상태를 보고 거부한다.
     */
    private void requireNotExpired(AiRouteGeneration generation) {
        Instant expiresAt = generation.getExpiresAt();
        if (expiresAt != null && !clock.instant().isBefore(expiresAt)) {
            throw new AiRouteGenerationNotFoundException(generation.getGenerationId());
        }
    }
}
