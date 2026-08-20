package com.example.ilgeobolkka.book.dto;

import com.example.ilgeobolkka.book.entity.Book;
import java.util.List;
import org.springframework.data.domain.Page;

public record FindBooksResponse(
        List<BookListItemResponse> books,
        int page,
        int totalPages,
        long totalCount,
        List<String> categories,
        String selectedCategory) {

    public static FindBooksResponse from(
            Page<Book> books,
            int requestedPage,
            List<String> categories,
            String selectedCategory) {
        List<BookListItemResponse> items =
                books.getContent().stream()
                        .map(BookListItemResponse::from)
                        .toList();
        return new FindBooksResponse(
                items,
                requestedPage,
                books.getTotalPages(),
                books.getTotalElements(),
                List.copyOf(categories),
                selectedCategory);
    }
}
