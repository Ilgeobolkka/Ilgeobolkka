package com.example.ilgeobolkka.contentimport;

/** AI 콘텐츠 적재 입력 또는 트랜잭션이 전체 계약을 만족하지 못했을 때 발생한다. */
public final class AiRouteContentImportException extends IllegalStateException {

    public AiRouteContentImportException(String message) {
        super(message);
    }
}
