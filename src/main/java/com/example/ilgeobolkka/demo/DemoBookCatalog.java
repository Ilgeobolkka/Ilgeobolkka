package com.example.ilgeobolkka.demo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!prod & (local | demo | test | content-import)")
public class DemoBookCatalog {

    private static final int BOOK_COUNT = 100;
    private static final String BOOK_RESOURCE_PATH = "demo/books.json";

    private final List<BookSeed> books;

    DemoBookCatalog(ObjectMapper objectMapper) {
        this.books = loadBooks(objectMapper);
    }

    public List<BookSeed> books() {
        return books;
    }

    private List<BookSeed> loadBooks(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(BOOK_RESOURCE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            BookMetadata[] metadata = objectMapper.readValue(inputStream, BookMetadata[].class);
            validate(metadata);
            return Arrays.stream(metadata).map(this::createBook).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("시연 도서 리소스를 읽을 수 없습니다.", exception);
        }
    }

    private void validate(BookMetadata[] metadata) {
        if (metadata.length != BOOK_COUNT) {
            throw new IllegalStateException("시연 도서 리소스는 정확히 100권이어야 합니다.");
        }

        Set<Long> ids = new HashSet<>();
        for (BookMetadata book : metadata) {
            if (book.id() < 1
                    || book.id() > BOOK_COUNT
                    || !ids.add(book.id())
                    || book.category().isBlank()
                    || book.title().isBlank()
                    || book.author().isBlank()
                    || book.totalPageCount() < 1
                    || book.priceWon() < 1) {
                throw new IllegalStateException("시연 도서 리소스에 유효하지 않은 항목이 있습니다.");
            }
        }
    }

    private BookSeed createBook(BookMetadata metadata) {
        return new BookSeed(
                metadata.id(),
                metadata.category(),
                metadata.title(),
                metadata.author(),
                metadata.description(),
                metadata.coverImagePath(),
                metadata.totalPageCount(),
                metadata.priceWon());
    }

    private record BookMetadata(
            long id,
            String category,
            String title,
            String author,
            String description,
            String coverImagePath,
            int totalPageCount,
            int priceWon) {}

    public record BookSeed(
            long id,
            String category,
            String title,
            String author,
            String description,
            String coverImagePath,
            int totalPageCount,
            int priceWon) {}
}
