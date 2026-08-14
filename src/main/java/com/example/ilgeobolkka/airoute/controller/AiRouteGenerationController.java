package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationApiResult;
import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationRequest;
import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationResponse;
import com.example.ilgeobolkka.airoute.facade.AiRouteGenerationApiFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteGenerationController {

    private final AiRouteGenerationApiFacade aiRouteGenerationApiFacade;

    @PostMapping("/api/books/{bookId}/ai-route-generations")
    ResponseEntity<AiRouteGenerationResponse> generate(
            @PathVariable long bookId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody AiRouteGenerationRequest request,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        AiRouteGenerationApiResult result = aiRouteGenerationApiFacade.generate(
                authenticatedReader.readerId(), idempotencyKey, bookId, request);
        HttpStatus status = result.generating()
                ? HttpStatus.ACCEPTED
                : result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/api/ai-route-generations/{generationId}")
    ResponseEntity<AiRouteGenerationResponse> findGeneration(
            @PathVariable UUID generationId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        AiRouteGenerationApiResult result = aiRouteGenerationApiFacade.find(
                authenticatedReader.readerId(), generationId);
        return ResponseEntity.status(
                        result.generating() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result.response());
    }
}
