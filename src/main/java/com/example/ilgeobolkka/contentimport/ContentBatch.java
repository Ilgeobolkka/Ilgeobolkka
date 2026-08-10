package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import java.util.List;

record ContentBatch(String contentVersion, String manifestSha256, List<ConvertedBook> books) {

    List<ConvertedPage> pages() {
        return books.stream().flatMap(book -> book.pages().stream()).toList();
    }
}

record ConvertedBook(
        long bookId, String sourceSha256, int totalPageCount, List<ConvertedPage> pages) {}

record ConvertedPage(
        long bookId,
        int pageNumber,
        BookPageContentType contentType,
        String textContent,
        String imagePath,
        Long imageBytes) {}

record ContentResultManifest(
        String contentVersion,
        String manifestSha256,
        String pdftotextVersion,
        String pdftoppmVersion,
        ImageConversion imageConversion,
        List<ConvertedBook> books) {}

record ImageConversion(String format, int dpi, int quality) {}
