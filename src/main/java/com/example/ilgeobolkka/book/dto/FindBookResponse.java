package com.example.ilgeobolkka.book.dto;

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
        Boolean owned) {

    public static FindBookResponse of(Book book, Boolean owned) {
        return new FindBookResponse(
                book.getId(),
                book.getCategory(),
                book.getCoverImagePath(),
                book.getTitle(),
                book.getAuthor(),
                book.getDescription(),
                book.getTotalPageCount(),
                book.getPriceWon(),
                owned);
    }
}
