package com.example.ilgeobolkka.infra.openai;

public final class OpenAiRouteException extends RuntimeException {

    private final Failure failure;

    public OpenAiRouteException(Failure failure) {
        super(failure.message());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

    public enum Failure {
        BUDGET_LIMIT("경로 생성 요청 예산 한도를 초과했습니다."),
        TEMPORARY("경로 생성 공급자에 일시적인 문제가 발생했습니다."),
        TIMEOUT_OR_INCOMPLETE("경로 생성 응답이 제한 시간 안에 완료되지 않았습니다."),
        REFUSAL("경로 생성 공급자가 요청을 거절했습니다."),
        MALFORMED_RESPONSE("경로 생성 공급자 응답이 올바르지 않습니다.");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        private String message() {
            return message;
        }
    }
}
