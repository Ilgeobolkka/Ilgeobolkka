package com.example.ilgeobolkka.contentimport;

/** AI 콘텐츠 적재 실패. 트랜잭션 안에서 나면 전체가 rollback된다. */
public final class AiRouteContentImportException extends IllegalStateException {

    public AiRouteContentImportException(String message) {
        super(message);
    }
}
