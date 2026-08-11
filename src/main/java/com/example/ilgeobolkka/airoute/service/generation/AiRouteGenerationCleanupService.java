package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import java.time.Clock;
import java.util.List;
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

    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final Clock clock;

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
