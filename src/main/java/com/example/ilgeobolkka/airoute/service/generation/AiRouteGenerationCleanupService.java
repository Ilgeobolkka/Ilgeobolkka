package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /**
     * 한 스윕이 다룰 최대 건수. 계정당 하루 10회 제한은 계정 수를 제한하지 않으므로 전체 대상 수에
     * 상한이 없다. 배치가 한동안 멈췄다 살아나면 backlog 를 한 transaction 에 담게 되어 메모리와
     * {@code IN} 절 크기, 잠금 유지 시간이 함께 커진다. 나눠서 주기마다 조금씩 흘려보낸다.
     */
    static final int BATCH_SIZE = 200;

    private static final Pageable BATCH = PageRequest.ofSize(BATCH_SIZE);

    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final Clock clock;

    /**
     * 버려진 {@code GENERATING} 을 {@code FAILED} 로 되돌린다. 정상 실패와 같은 모양으로 만들어, 보관
     * 기간 동안 같은 멱등 키가 최초 오류를 그대로 받고 외부 호출을 다시 시작하지 않게 한다.
     *
     * <p>보관 기간은 <b>복구를 돌린 시각이 아니라 논리적으로 실패한 시각</b>부터 잰다. 제한 시간을 넘긴
     * 순간 이미 실패한 요청이므로 완료 시각은 {@code createdAt + 제한 시간} 이고 만료는 거기서 15분
     * 뒤다. 지금 시각을 기준으로 잡으면 한참 전에 사라졌어야 할 멱등 상태가 복구할 때마다 15분씩
     * 되살아나고, {@code completed_at} 에도 "그때 완료됐다" 는 거짓이 남는다.
     *
     * <p>그래서 오래 방치된 생성은 복구되자마자 이미 만료 상태이며, 같은 스윕의 {@link #removeExpired}
     * 가 바로 지운다.
     *
     * <p>대상을 한 번에 잠근다. 건마다 잠금 조회를 돌리면 한 스윕이 상한만큼 쿼리를 낸다.
     *
     * <p>잠근 뒤 상태를 다시 본다. 목록을 뽑은 시점과 잠그는 시점 사이에 호출자가 정상 완료했을 수
     * 있는데, 그때는 이미 결과가 있으므로 건너뛴다. 그사이 사라진 행은 잠금 결과에 아예 나오지 않는다.
     * 반대로 복구가 이겼다면 호출자의 완료가 {@code GENERATING} 이 아니라며 거부되는데 그것도 맞는
     * 결과다. 어느 쪽도 오류가 아니다.
     *
     * <p>이 재확인이 막는 것은 잘못된 상태 덮어쓰기가 아니다. 그쪽은 Entity 의 전이 가드가 이미 막는다.
     * 여기서 건너뛰지 않으면 그 가드가 예외를 올려 스윕 한 사이클이 통째로 롤백된다. 다음 주기에는 그
     * 행이 조회 결과에서 빠지므로 저절로 정상화되지만, 그 사이 다른 행의 복구까지 밀린다.
     *
     * @return 실패로 되돌린 수
     */
    @Transactional
    public int recoverAbandoned() {
        List<UUID> abandoned =
                generationRepository.findAbandonedGenerationIds(
                        clock.instant().minus(GENERATION_TIME_LIMIT), BATCH);
        if (abandoned.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (AiRouteGeneration generation :
                generationRepository.lockAllByGenerationIdIn(abandoned)) {
            if (generation.getStatus() != AiRouteGenerationStatus.GENERATING) {
                continue;
            }

            Instant failedAt = generation.getCreatedAt().plus(GENERATION_TIME_LIMIT);
            generation.fail(
                    TIMEOUT_FAILURE_CODE,
                    failedAt,
                    failedAt.plus(AiRouteGenerationLifecycleService.RESULT_RETENTION));
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
     * <p>한 번에 {@link #BATCH} 건까지만 지운다. 남으면 다음 주기가 이어받는다.
     *
     * @return 지운 생성 수
     */
    @Transactional
    public int removeExpired() {
        List<UUID> expired = generationRepository.findExpiredGenerationIds(clock.instant(), BATCH);
        if (expired.isEmpty()) {
            return 0;
        }

        // 생성 행을 먼저 잠근다. 시작 경로가 만료 행을 지울 때도 생성 → 항목 순서라, 반대로 잡으면
        // 같은 generationId 에 동시에 닿았을 때 교착한다.
        generationRepository.lockAllByGenerationIdIn(expired);
        generationItemRepository.deleteByGenerationIdIn(expired);
        generationRepository.deleteAllByIdInBatch(expired);
        return expired.size();
    }
}
