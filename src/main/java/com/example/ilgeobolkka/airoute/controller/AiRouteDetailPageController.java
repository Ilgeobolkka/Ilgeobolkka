package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.dto.FindAiRouteResponse;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.facade.AiRouteQueryFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteDetailPageController {

    private final AiRouteQueryFacade aiRouteQueryFacade;

    @GetMapping("/ai-routes/{routeId}")
    String detail(
            @PathVariable long routeId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader,
            Model model) {
        FindAiRouteResponse route;
        try {
            route = aiRouteQueryFacade.findRoute(authenticatedReader.readerId(), routeId);
        } catch (AiRouteNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, exception);
        }

        model.addAttribute("pageTitle", "AI 독서 경로");
        model.addAttribute("route", route);
        return "pages/ai-route-detail";
    }
}
