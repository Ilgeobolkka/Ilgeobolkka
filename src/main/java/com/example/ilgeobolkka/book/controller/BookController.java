package com.example.ilgeobolkka.book.controller;

import com.example.ilgeobolkka.book.dto.FindBookResponse;
import com.example.ilgeobolkka.book.dto.FindBooksResponse;
import com.example.ilgeobolkka.book.facade.BookFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookFacade bookFacade;

    @GetMapping
    FindBooksResponse findBooks(
            @RequestParam @Min(1) int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return bookFacade.findBooks(page, keyword, category);
    }

    @GetMapping("/{bookId}")
    FindBookResponse findBook(
            @PathVariable @Positive long bookId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        Long readerId = authenticatedReader == null ? null : authenticatedReader.readerId();

        return bookFacade.findBook(bookId, readerId);
    }
}
