package com.example.ilgeobolkka.book.service;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.exception.BookNotFoundException;
import com.example.ilgeobolkka.book.exception.BookPageNotFoundException;
import com.example.ilgeobolkka.book.repository.BookPageRepository;
import com.example.ilgeobolkka.book.repository.BookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookService {

    private static final int PAGE_SIZE = 10;

    private final BookRepository bookRepository;
    private final BookPageRepository bookPageRepository;

    public Page<Book> findBooks(int page, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        String escapedKeyword = escapeLikePattern(normalizedKeyword);
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE);

        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(
                    List.of(), pageable, bookRepository.countByKeyword(escapedKeyword));
        }

        return bookRepository.findByKeyword(escapedKeyword, pageable);
    }

    public Book findBook(long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
    }

    public BookPage findPage(long bookId, int pageNumber) {
        return bookPageRepository.findByBookIdAndPageNumber(bookId, pageNumber)
                .orElseThrow(() -> new BookPageNotFoundException(bookId, pageNumber));
    }

    private String escapeLikePattern(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
