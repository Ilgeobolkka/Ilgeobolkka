package com.example.ilgeobolkka.reading.dto;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import java.time.Instant;

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

    /** 온라인 소장 도서는 잉크·대여와 무관하게 제공한다(INV-012). */
    public static OpenPageResponse owned(ReadingSession session, BookPage page, int inkBalance) {
        return new OpenPageResponse(
                session.getViewerSessionId().toString(),
                session.getBookId(),
                page.getPageNumber(),
                true,
                0,
                inkBalance,
                null,
                null,
                page.getContentType());
    }
}
