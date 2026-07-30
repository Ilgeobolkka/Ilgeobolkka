package com.example.ilgeobolkka.book.service;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.exception.BookNotFoundException;
import com.example.ilgeobolkka.book.exception.BookPageNotFoundException;
import com.example.ilgeobolkka.book.repository.BookPageRepository;
import com.example.ilgeobolkka.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

        return bookRepository.findByKeyword(
                escapeLikePattern(normalizedKeyword),
                PageRequest.of(page - 1, PAGE_SIZE));
    }

    public Book findBook(long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
    }

    public BookPage findPage(long bookId, int pageNumber) {
        return bookPageRepository
                .findByBookIdAndPageNumber(bookId, pageNumber)
                .orElseThrow(() -> new BookPageNotFoundException(bookId, pageNumber));
    }

    private String escapeLikePattern(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
