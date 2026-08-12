package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.dto.FindAiRoutesResponse;
import com.example.ilgeobolkka.airoute.facade.AiRouteQueryFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-routes")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteQueryController {

    private final AiRouteQueryFacade aiRouteQueryFacade;

    /**
     * 인증 계정의 저장 경로 목록.
     *
     * <p>{@code page}는 필수다. 생략을 1페이지로 보정하면 정본이 정한 400 계약이 깨진다.
     */
    @GetMapping
    FindAiRoutesResponse findRoutes(
            @RequestParam @Min(1) int page,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return aiRouteQueryFacade.findRoutes(authenticatedReader.readerId(), page);
    }

    @GetMapping("/{routeId}")
    FindAiRouteResponse findRoute(
            @PathVariable long routeId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return aiRouteQueryFacade.findRoute(authenticatedReader.readerId(), routeId);
    }
}
