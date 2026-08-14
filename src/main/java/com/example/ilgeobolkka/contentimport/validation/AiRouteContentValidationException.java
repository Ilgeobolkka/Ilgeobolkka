package com.example.ilgeobolkka.contentimport.validation;

/** AI 경로 콘텐츠와 평가 데이터의 계약 검증 실패. */
public final class AiRouteContentValidationException extends IllegalStateException {

    public AiRouteContentValidationException(String message) {
        super(message);
    }
}
