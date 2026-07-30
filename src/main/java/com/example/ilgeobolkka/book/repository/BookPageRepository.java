package com.example.ilgeobolkka.book.repository;

import com.example.ilgeobolkka.book.entity.BookPage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookPageRepository extends JpaRepository<BookPage, Long> {

    Optional<BookPage> findByBookIdAndPageNumber(long bookId, int pageNumber);
}
