package com.example.ilgeobolkka.airoute.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 현재 경로로 지정할 저장 경로. 도서는 경로에 붙어 있어 받지 않고, 서버가 저장해 둔 값과 대조한다.
 */
public record ChangeCurrentAiRouteRequest(@NotNull Long routeId) {
}
