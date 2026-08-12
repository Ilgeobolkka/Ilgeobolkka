package com.example.ilgeobolkka.airoute.exception;

import java.util.UUID;

/**
 * 저장했던 경로가 이미 삭제돼 같은 생성으로 다시 저장할 수 없다.
 *
 * <p>{@code CONSUMED} 는 저장 경로가 삭제된 뒤에만 남는 상태다. 만료·다른 독자와 달리 404 로 감추지
 * 않는데, 이 식별자의 소유자는 요청한 본인이고 자기가 지운 경로를 되살릴 수 없다는 것이 알려야 할
 * 결과이기 때문이다.
 *
 * <p>생성 endpoint 의 멱등 재조회도 같은 코드를 반환한다. 정의는 먼저 진행하는 S01 에서 하고 G08 은
 * 재사용한다.
 */
public class AiRouteGenerationConsumedException extends RuntimeException {

    public AiRouteGenerationConsumedException(UUID generationId) {
        super("이미 소비한 생성 결과입니다. generationId=" + generationId);
    }
}
