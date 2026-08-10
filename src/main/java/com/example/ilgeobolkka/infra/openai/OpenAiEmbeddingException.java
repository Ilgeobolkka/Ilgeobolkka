package com.example.ilgeobolkka.infra.openai;

public final class OpenAiEmbeddingException extends RuntimeException {

    private final Failure failure;

    OpenAiEmbeddingException(Failure failure) {
        super(failure.message());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

    public enum Failure {
        BUDGET_LIMIT("Embedding 요청 예산 한도를 초과했습니다."),
        RATE_LIMIT("Embedding 요청 속도 한도를 초과했습니다."),
        TEMPORARY("Embedding 공급자에 일시적인 문제가 발생했습니다."),
        INVALID_RESPONSE("Embedding 공급자 응답이 올바르지 않습니다.");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        private String message() {
            return message;
        }
    }
}
