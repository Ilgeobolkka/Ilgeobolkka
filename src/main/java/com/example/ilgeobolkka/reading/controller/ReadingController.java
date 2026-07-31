package com.example.ilgeobolkka.reading.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.reading.dto.OpenPageRequest;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.facade.ReadingFacade;
import jakarta.validation.Valid;
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
public class ReadingController {

    private final ReadingFacade readingFacade;

    @PostMapping("/api/books/{bookId}/reading-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    OpenPageResponse openSession(
            @PathVariable long bookId,
            @Valid @RequestBody OpenPageRequest request,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return readingFacade.openNewSession(
                authenticatedReader.readerId(), bookId, request.pageNumber());
    }

    @PatchMapping("/api/reading-sessions/current/page")
    OpenPageResponse movePage(
            @RequestHeader("X-Viewer-Session-Id") UUID viewerSessionId,
            @Valid @RequestBody OpenPageRequest request,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return readingFacade.movePage(
                authenticatedReader.readerId(), viewerSessionId, request.pageNumber());
    }
}
