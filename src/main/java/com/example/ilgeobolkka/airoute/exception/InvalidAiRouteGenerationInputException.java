package com.example.ilgeobolkka.airoute.exception;

/** 예산·깊이 배타 규칙이나 예산 범위를 어긴 생성 입력이다. */
public class InvalidAiRouteGenerationInputException extends RuntimeException {

    public InvalidAiRouteGenerationInputException(String reason) {
        super("생성 입력이 올바르지 않습니다. " + reason);
    }
}
