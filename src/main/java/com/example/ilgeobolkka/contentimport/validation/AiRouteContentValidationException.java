package com.example.ilgeobolkka.contentimport.validation;

/** 적재 전 콘텐츠 검증 실패. 이 예외가 나면 Embeddings 호출과 DB 변경을 시작하지 않는다. */
public final class AiRouteContentValidationException extends IllegalStateException {

    public AiRouteContentValidationException(String message) {
        super(message);
    }
}
