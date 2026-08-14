package com.example.ilgeobolkka.airoute.service.generation;

import java.util.UUID;

/** 유효한 멱등 생성의 식별자와 요청 지문. HTTP 재요청이 최신 권한을 보기 전에 같은 입력인지 비교한다. */
public record AiRouteGenerationRequestView(
        UUID generationId,
        long bookId,
        String contentVersion,
        String requestFingerprint) {}
