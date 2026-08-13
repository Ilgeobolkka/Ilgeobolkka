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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    /** G07이 같은 읽기 transaction 안에서 생성 입력 snapshot을 만들 때 사용한다. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<BookPage> findPages(long bookId) {
        return bookPageRepository.findAllByBookIdOrderByPageNumber(bookId);
    }

    private String escapeLikePattern(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
