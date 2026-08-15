package com.example.ilgeobolkka.book.repository;

import com.example.ilgeobolkka.book.entity.Book;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** 활성화 대상 전체를 PK 순서로 잠가 동시 실행의 부분 성공과 잠금 순서 역전을 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT book
            FROM Book book
            WHERE book.contentVersion = :contentVersion
              AND book.aiDataPolicyVersion = :dataPolicyVersion
              AND book.aiExternalTransferAllowed = true
            ORDER BY book.id ASC
            """)
    List<Book> findActivationTargetsForUpdate(
            @Param("contentVersion") String contentVersion,
            @Param("dataPolicyVersion") String dataPolicyVersion);
}
