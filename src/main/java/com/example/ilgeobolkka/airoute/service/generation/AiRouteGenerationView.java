package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import java.time.Instant;
import java.util.UUID;

/**
 * 아직 유효한 생성의 상태 사본. Entity 를 그대로 내보내지 않는 이유는 두 가지다. 후속 작업이 상태를 직접
 * 바꾸지 못하게 하고, transaction 밖에서 지연 로딩 연관을 건드리지 않게 한다.
 *
 * <p>경로 항목은 담지 않는다. 항목까지 필요한 화면 응답은 G08 이 따로 조회한다.
 *
 * @param status 이 값에 따라 아래 세 묶음 중 하나만 채워진다
 * @param normalizedPurpose 저장으로 전환하면 지워지므로 {@code SAVED}·{@code CONSUMED} 는 {@code null}
 * @param noRouteReason {@code NO_ROUTE} 에서만 값이 있다
 * @param minimumRequiredInk {@code INSUFFICIENT_BUDGET} 에서만 값이 있다
 * @param failureCode {@code FAILED} 에서만 값이 있다
 * @param savedRouteId {@code SAVED} 에서만 값이 있다
 * @param expiresAt {@code GENERATING} 은 아직 {@code null} 이다
 */
public record AiRouteGenerationView(
        UUID generationId,
        long bookId,
        String contentVersion,
        AiRouteGenerationStatus status,
        String normalizedPurpose,
        AiRouteNoRouteReason noRouteReason,
        Integer minimumRequiredInk,
        String failureCode,
        Long savedRouteId,
        Instant expiresAt) {

    static AiRouteGenerationView from(AiRouteGeneration generation) {
        return new AiRouteGenerationView(
                generation.getGenerationId(),
                generation.getBookId(),
                generation.getContentVersion(),
                generation.getStatus(),
                generation.getNormalizedPurpose(),
                generation.getNoRouteReason(),
                generation.getMinimumRequiredInk(),
                generation.getFailureCode(),
                generation.getSavedRouteId(),
                generation.getExpiresAt());
    }
}
