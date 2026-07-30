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
        return of(session, page, true, 0, inkBalance, null, null);
    }

    /** 활성 대여 중인 페이지는 잉크를 다시 차감하지 않고 기존 대여 기간을 그대로 반환한다(INV-002). */
    public static OpenPageResponse rented(
            ReadingSession session,
            BookPage page,
            int inkBalance,
            Instant rentedAt,
            Instant expiresAt) {
        return of(session, page, false, 0, inkBalance, rentedAt, expiresAt);
    }

    /** 소장·활성 대여가 없어 1잉크를 차감하고 새 30일 대여를 시작한 페이지의 응답이다. */
    public static OpenPageResponse newlyRented(
            ReadingSession session,
            BookPage page,
            int inkBalance,
            Instant rentedAt,
            Instant expiresAt) {
        return of(session, page, false, 1, inkBalance, rentedAt, expiresAt);
    }

    private static OpenPageResponse of(
            ReadingSession session,
            BookPage page,
            boolean owned,
            int deductedInk,
            int inkBalance,
            Instant rentedAt,
            Instant expiresAt) {
        return new OpenPageResponse(
                session.getViewerSessionId().toString(),
                session.getBookId(),
                page.getPageNumber(),
                owned,
                deductedInk,
                inkBalance,
                rentedAt,
                expiresAt,
                page.getContentType());
    }
}
