package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
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
 * 만료한 임시 생성을 지운다.
 *
 * <p>이 정리는 조회·저장 거부의 근거가 아니다. 만료 판정은
 * {@link AiRouteGenerationLifecycleService} 가 시각으로 하고, 여기서는 이미 판정이 끝난 행을 실제로
 * 없앨 뿐이다. 정리가 늦어도 만료한 결과가 다시 보이지 않는다.
 *
 * <p>흔적을 남기지 않는다. 지운 멱등 키를 따로 기록하면 "이 키가 전에 쓰였는가" 를 판별할 수 있게 되는데,
 * 만료 뒤 같은 키는 새 요청으로 취급해야 한다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteGenerationCleanupService {

    /**
     * 한 번의 검증 재시도를 포함한 전체 요청 제한. 이 시간을 넘도록 {@code GENERATING} 인 생성은 버려진
     * 것으로 본다. G07 이 자기 타임아웃에도 같은 값을 쓴다.
     */
    public static final Duration GENERATION_TIME_LIMIT = Duration.ofSeconds(20);

    /** 제한 시간 초과의 공개 실패 코드. 공급자 정보를 담지 않는다. */
    public static final String TIMEOUT_FAILURE_CODE = "AI_ROUTE_GENERATION_TIMEOUT";

    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final Clock clock;

    /**
     * 버려진 {@code GENERATING} 을 {@code FAILED} 로 되돌린다. 정상 실패와 같은 모양으로 만들어, 보관
     * 기간 동안 같은 멱등 키가 최초 오류를 그대로 받고 외부 호출을 다시 시작하지 않게 한다.
     *
     * <p>잠근 뒤 상태를 다시 본다. 목록을 뽑은 시점과 잠그는 시점 사이에 호출자가 정상 완료했을 수
     * 있는데, 그때는 이미 결과가 있으므로 건너뛴다. 반대로 복구가 이겼다면 호출자의 완료가
     * {@code GENERATING} 이 아니라며 거부되는데 그것도 맞는 결과다. 어느 쪽도 오류가 아니다.
     *
     * <p>이 재확인이 막는 것은 잘못된 상태 덮어쓰기가 아니다. 그쪽은 Entity 의 전이 가드가 이미 막는다.
     * 여기서 건너뛰지 않으면 그 가드가 예외를 올려 스윕 한 사이클이 통째로 롤백된다. 다음 주기에는 그
     * 행이 조회 결과에서 빠지므로 저절로 정상화되지만, 그 사이 다른 행의 복구까지 밀린다.
     *
     * @return 실패로 되돌린 수
     */
    @Transactional
    public int recoverAbandoned() {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(AiRouteGenerationLifecycleService.RESULT_RETENTION);
        List<UUID> abandoned =
                generationRepository.findAbandonedGenerationIds(now.minus(GENERATION_TIME_LIMIT));

        int recovered = 0;
        for (UUID generationId : abandoned) {
            Optional<AiRouteGeneration> locked =
                    generationRepository.findByGenerationIdForUpdate(generationId);
            if (locked.isEmpty()
                    || locked.get().getStatus() != AiRouteGenerationStatus.GENERATING) {
                continue;
            }
            locked.get().fail(TIMEOUT_FAILURE_CODE, now, expiresAt);
            recovered++;
        }
        return recovered;
    }

    /**
     * 만료한 생성과 그 항목을 한 transaction 에서 지운다. 중간에 실패하면 둘 다 남는다.
     *
     * <p>항목을 먼저 지운다. {@code fk_ai_route_generation_item_generation_book} 이 생성 행을 가리키므로
     * 순서를 바꾸면 제약에 걸린다.
     *
     * <p>대상을 나눠 담지 않는다. 보관 기간이 15분이고 계정당 하루 10건이라 한 번에 도는 양이 이미
     * 작다. 쌓이는 일이 생기면 그때 나누는 것이 맞다.
     *
     * @return 지운 생성 수
     */
    @Transactional
    public int removeExpired() {
        List<UUID> expired = generationRepository.findExpiredGenerationIds(clock.instant());
        if (expired.isEmpty()) {
            return 0;
        }

        generationItemRepository.deleteByGenerationIdIn(expired);
        generationRepository.deleteAllByIdInBatch(expired);
        return expired.size();
    }
}
