package com.example.ilgeobolkka.airoute.exception;

public class AiRouteNotCompletedException extends RuntimeException {
    public AiRouteNotCompletedException() {
        super("완료되지 않은 경로에는 피드백을 남길 수 없습니다.");
    }
}
