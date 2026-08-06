package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import java.util.ArrayList;
import java.util.List;

final class ContentBatchTestFixture {

    private ContentBatchTestFixture() {}

    static ContentBatch demoPageCountBatch() {
        List<ConvertedBook> books = new ArrayList<>();
        for (long bookId = 1; bookId <= 100; bookId++) {
            int totalPageCount =
                    switch ((int) (bookId % 3)) {
                        case 1 -> 4;
                        case 2 -> 5;
                        default -> 3;
                    };
            List<ConvertedPage> pages = new ArrayList<>();
            for (int pageNumber = 1; pageNumber <= totalPageCount; pageNumber++) {
                if (pageNumber == 2) {
                    pages.add(
                            new ConvertedPage(
                                    bookId,
                                    pageNumber,
                                    BookPageContentType.IMAGE,
                                    null,
                                    "var/content/pages/test-batch/book-%03d/page-002.jpg"
                                            .formatted(bookId),
                                    10L));
                } else {
                    pages.add(
                            new ConvertedPage(
                                    bookId,
                                    pageNumber,
                                    BookPageContentType.TEXT,
                                    "도서 %d의 %d페이지".formatted(bookId, pageNumber),
                                    null,
                                    null));
                }
            }
            books.add(
                    new ConvertedBook(
                            bookId,
                            "a".repeat(64),
                            totalPageCount,
                            List.copyOf(pages)));
        }
        return new ContentBatch("initial-v1", "b".repeat(64), List.copyOf(books));
    }
}
