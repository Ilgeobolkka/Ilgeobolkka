package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.facade.AiRouteDeleteFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-routes")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteDeleteController {

    private final AiRouteDeleteFacade aiRouteDeleteFacade;

    /**
     * 저장 경로를 지운다. 성공은 바디 없는 204 다. 지운 뒤 남은 현재 경로는 목록·상세로 확인한다.
     *
     * <p>이미 지운 경로의 재시도는 404 다. 삭제를 멱등한 204 로 만들면 남의 경로 식별자를 넣어 본 요청과
     * 같은 응답이 되어 존재 여부가 새어 나간다.
     */
    @DeleteMapping("/{routeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRoute(
            @PathVariable long routeId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        aiRouteDeleteFacade.deleteRoute(authenticatedReader.readerId(), routeId);
    }
}
