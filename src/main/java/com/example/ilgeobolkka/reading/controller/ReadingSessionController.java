package com.example.ilgeobolkka.reading.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.reading.dto.OpenPageRequest;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.facade.OpenPageViewer;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReadingSessionController {

    private final ReadingFacade readingFacade;

    @PostMapping("/api/books/{bookId}/reading-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    OpenPageResponse open(
            @PathVariable @Positive long bookId,
            @RequestBody @Valid OpenPageRequest request,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return readingFacade.openPage(
                authenticatedReader.readerId(),
                new OpenPageViewer.NewViewer(bookId),
                request.pageNumber());
    }

    @PatchMapping("/api/reading-sessions/current/page")
    OpenPageResponse move(
            @RequestHeader("X-Viewer-Session-Id") UUID viewerSessionId,
            @RequestBody @Valid OpenPageRequest request,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return readingFacade.openPage(
                authenticatedReader.readerId(),
                new OpenPageViewer.ExistingViewer(viewerSessionId),
                request.pageNumber());
    }
}
