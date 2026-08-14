package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import java.util.UUID;

/** 유효한 멱등 생성의 canonical 요청 부분. HTTP 재요청이 최신 권한을 보기 전에 같은 입력인지 비교한다. */
public record AiRouteGenerationRequestView(
        UUID generationId,
        long bookId,
        String contentVersion,
        String requestFingerprint,
        AiRouteGenerationStatus status,
        String normalizedPurpose,
        AiRouteRequestType requestType,
        Integer maxAdditionalInk,
        AiRouteDepth depth) {}
