package com.example.ilgeobolkka.reading.dto;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.rental.entity.PageRental;
import java.time.Instant;
import java.util.UUID;

public record OpenPageResponse(
        String viewerSessionId,
        long bookId,
        int pageNumber,
        boolean owned,
        int deductedInk,
        int inkBalance,
        Instant rentedAt,
        Instant expiresAt,
        BookPageContentType contentType) {

    public static OpenPageResponse owned(UUID viewerSessionId, BookPage page, int inkBalance) {
        return new OpenPageResponse(
                viewerSessionId.toString(),
                page.getBookId(),
                page.getPageNumber(),
                true,
                0,
                inkBalance,
                null,
                null,
                page.getContentType());
    }

    public static OpenPageResponse activeRental(
            UUID viewerSessionId, BookPage page, PageRental rental, int inkBalance) {
        return new OpenPageResponse(
                viewerSessionId.toString(),
                page.getBookId(),
                page.getPageNumber(),
                false,
                0,
                inkBalance,
                rental.getRentedAt(),
                rental.getExpiresAt(),
                page.getContentType());
    }

    public static OpenPageResponse newRental(
            UUID viewerSessionId, BookPage page, PageRental rental, int inkBalance) {
        return new OpenPageResponse(
                viewerSessionId.toString(),
                page.getBookId(),
                page.getPageNumber(),
                false,
                1,
                inkBalance,
                rental.getRentedAt(),
                rental.getExpiresAt(),
                page.getContentType());
    }
}
