package com.example.ilgeobolkka.airoute.service.validation;

public final class AiRouteInvalidOutputException extends RuntimeException {

    private final Failure failure;

    AiRouteInvalidOutputException(Failure failure) {
        super(failure.message());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

    public static AiRouteInvalidOutputException retryContractChanged() {
        return new AiRouteInvalidOutputException(Failure.RETRY_CONTRACT_CHANGED);
    }

    public enum Failure {
        CONTEXT_MISMATCH("모델 출력 검증 문맥이 올바르지 않습니다.", false),
        EMPTY_PROPOSAL("모델 출력에 경로 항목이 없습니다.", true),
        PAGE_NOT_FOUND("모델 출력에 존재하지 않는 페이지가 있습니다.", true),
        PAGE_OUTSIDE_ALLOWED_SET("모델 출력에 허용되지 않은 페이지가 있습니다.", true),
        MISSING_CANDIDATE("모델 출력에 검색 후보 페이지가 없습니다.", true),
        DUPLICATE_PAGE("모델 출력에 중복 페이지가 있습니다.", true),
        MISSING_PREREQUISITE("모델 출력에 필요한 선수 페이지가 누락되었습니다.", true),
        INVALID_PREREQUISITE_ORDER("모델 출력의 선수 페이지 순서가 올바르지 않습니다.", true),
        INVALID_ENUM("모델 출력의 열거값이 올바르지 않습니다.", true),
        RETRY_CONTRACT_CHANGED("검증 재시도에서 모델·prompt·schema 계약이 바뀌었습니다.", false);

        private final String message;
        private final boolean retryable;

        Failure(String message, boolean retryable) {
            this.message = message;
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }

        private String message() {
            return message;
        }
    }
}
