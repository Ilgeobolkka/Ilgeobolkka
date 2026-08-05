package com.example.ilgeobolkka.book.dto;

import com.example.ilgeobolkka.book.entity.Book;

public record BookListItemResponse(
        long bookId,
        String category,
        String coverImagePath,
        String title,
        String author,
        int bookPrice) {

    public static BookListItemResponse from(Book book) {
        return new BookListItemResponse(
                book.getId(),
                book.getCategory(),
                book.getCoverImagePath(),
                book.getTitle(),
                book.getAuthor(),
                book.getPriceWon());
    }
}
