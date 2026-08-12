package com.example.ilgeobolkka.contentimport.embedding;

/**
 * 콘텐츠 embedding batch 생성 실패. 이 예외가 나면 batch가 만들어지지 않으므로 부분 vector가 다음
 * 단계로 넘어가지 않는다.
 */
public final class AiRouteContentEmbeddingException extends IllegalStateException {

    public AiRouteContentEmbeddingException(String message) {
        super(message);
    }
}
