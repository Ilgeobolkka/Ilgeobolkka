package com.example.ilgeobolkka.airoute.exception;

/**
 * 후보 비교에 쓸 수 없는 임베딩이다. 모델·차원이 다르거나, 값이 유한 실수가 아니거나, zero norm이다.
 *
 * <p>벡터 값은 메시지에 담지 않는다. 어느 페이지의 무엇이 잘못됐는지만 남긴다.
 */
public class InvalidAiRouteEmbeddingException extends RuntimeException {

    public InvalidAiRouteEmbeddingException(String reason) {
        super("AI 경로 임베딩이 올바르지 않습니다. " + reason);
    }
}
