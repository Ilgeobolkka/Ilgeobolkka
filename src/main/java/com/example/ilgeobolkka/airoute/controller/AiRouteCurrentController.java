package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.dto.ChangeCurrentAiRouteRequest;
import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.facade.AiRouteCurrentFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/ai-routes")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteCurrentController {

    private final AiRouteCurrentFacade aiRouteCurrentFacade;

    /**
     * 같은 도서의 저장 경로 하나를 현재 경로로 지정한다.
     *
     * <p>응답은 저장 경로 상세와 같은 계약이다. 지정 뒤 화면이 바로 그 경로를 보여 주므로 지정과 상세를
     * 두 번 왕복하게 두지 않는다.
     */
    @PutMapping("/current")
    FindAiRouteResponse changeCurrentRoute(
            @PathVariable long bookId,
            @Valid @RequestBody ChangeCurrentAiRouteRequest request,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return aiRouteCurrentFacade.changeCurrent(
                authenticatedReader.readerId(), bookId, request.routeId());
    }
}
