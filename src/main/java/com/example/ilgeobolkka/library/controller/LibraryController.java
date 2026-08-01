package com.example.ilgeobolkka.library.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.library.dto.FindLibraryResponse;
import com.example.ilgeobolkka.library.facade.LibraryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryFacade libraryFacade;

    @GetMapping
    FindLibraryResponse findLibrary(
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return libraryFacade.findLibrary(authenticatedReader.readerId());
    }
}
