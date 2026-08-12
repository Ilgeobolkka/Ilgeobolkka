package com.example.ilgeobolkka.airoute.exception;

import java.util.UUID;

public class AiRouteGenerationNotFoundException extends RuntimeException {

    public AiRouteGenerationNotFoundException(UUID generationId) {
        super("AI 경로 생성을 찾을 수 없습니다: " + generationId);
    }
}
