package com.example.ilgeobolkka.contentimport.manifest;

import java.util.List;

public record InitialContentManifest(String contentVersion, List<Book> books)
        implements ContentManifest {

    public InitialContentManifest {
        if (books != null) {
            books = List.copyOf(books);
        }
    }

    public record Book(long bookId, String pdfPath, String pdfSha256, int totalPageCount) {}
}
