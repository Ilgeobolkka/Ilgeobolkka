package com.example.ilgeobolkka.book.facade;

import com.example.ilgeobolkka.book.dto.FindBookResponse;
import com.example.ilgeobolkka.book.dto.FindBooksResponse;
import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.openai.AiRouteFeatureProperties;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookFacade {

    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final AiRouteFeatureProperties aiRouteFeatureProperties;

    @Transactional(readOnly = true)
    public FindBooksResponse findBooks(int page, String keyword) {
        Page<Book> books = bookService.findBooks(page, keyword);

        return FindBooksResponse.from(books, page);
    }

    @Transactional(readOnly = true)
    public FindBookResponse findBook(long bookId, Long readerId) {
        Book book = bookService.findBook(bookId);
        Boolean owned =
                readerId == null ? null : ownershipService.isOwned(readerId, bookId);

        return FindBookResponse.of(book, owned, aiRouteFeatureProperties.enabled());
    }
}
