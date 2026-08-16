package com.example.ilgeobolkka.book.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.ilgeobolkka.book.entity.Book;

public record FindBookResponse(
        long bookId,
        String category,
        String coverImagePath,
        String title,
        String author,
        String description,
        int totalPageCount,
        int bookPrice,
        Boolean owned,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean aiRouteSupported) {

    public static FindBookResponse of(
            Book book,
            Boolean owned,
            boolean aiRouteEnabled) {
        return new FindBookResponse(
                book.getId(),
                book.getCategory(),
                book.getCoverImagePath(),
                book.getTitle(),
                book.getAuthor(),
                book.getDescription(),
                book.getTotalPageCount(),
                book.getPriceWon(),
                owned,
                aiRouteEnabled ? book.isAiRouteSupported() : null);
    }
}
