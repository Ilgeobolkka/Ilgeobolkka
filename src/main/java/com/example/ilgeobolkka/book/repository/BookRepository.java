package com.example.ilgeobolkka.book.repository;

import com.example.ilgeobolkka.book.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query(
            value =
                    """
                    SELECT *
                    FROM book b
                    WHERE b.title LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                       OR b.author LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                    ORDER BY b.category ASC, b.title ASC, b.id ASC
                    """,
            countQuery =
                    """
                    SELECT COUNT(*)
                    FROM book b
                    WHERE b.title LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                       OR b.author LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                    """,
            nativeQuery = true)
    Page<Book> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query(
            value =
                    """
                    SELECT COUNT(*)
                    FROM book b
                    WHERE b.title LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                       OR b.author LIKE CONCAT('%', :keyword, '%') ESCAPE '!'
                    """,
            nativeQuery = true)
    long countByKeyword(@Param("keyword") String keyword);
}
