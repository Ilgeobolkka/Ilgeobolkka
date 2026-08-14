package com.example.ilgeobolkka.airoute.controller;

import com.example.ilgeobolkka.airoute.facade.AiRouteContentFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.reading.dto.PageContent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-routes")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteContentController {

    private final AiRouteContentFacade aiRouteContentFacade;

    @PostMapping("/{routeId}/pages/{pageNumber}/content")
    ResponseEntity<byte[]> provideContent(
            @PathVariable long routeId,
            @PathVariable int pageNumber,
            @RequestHeader("X-Viewer-Session-Id") UUID viewerSessionId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        PageContent content = aiRouteContentFacade.provideContent(
                authenticatedReader.readerId(), routeId, pageNumber, viewerSessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(content.mediaType())
                .body(content.body());
    }
}
