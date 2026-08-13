package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.dto.AiRouteFeedbackRequest;
import com.example.ilgeobolkka.airoute.dto.AiRouteFeedbackResponse;
import com.example.ilgeobolkka.airoute.facade.AiRouteFeedbackFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-routes")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteFeedbackController {

    private final AiRouteFeedbackFacade aiRouteFeedbackFacade;

    @PutMapping("/{routeId}/feedback")
    ResponseEntity<AiRouteFeedbackResponse> changeFeedback(
            @PathVariable long routeId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader,
            @RequestBody @Valid AiRouteFeedbackRequest request) {
        AiRouteFeedbackResponse response =
                aiRouteFeedbackFacade.changeFeedback(routeId, authenticatedReader.readerId(), request);

        return ResponseEntity.ok(response);
    }
}
