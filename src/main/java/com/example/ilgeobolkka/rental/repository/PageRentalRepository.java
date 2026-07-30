package com.example.ilgeobolkka.rental.repository;

import com.example.ilgeobolkka.rental.entity.PageRental;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageRentalRepository extends JpaRepository<PageRental, Long> {

    /** INV-006 경계({@code rentedAt <= now < expiresAt})를 만족하는 활성 대여만 조회한다. */
    @Query(
            """
            SELECT rental
            FROM PageRental rental
            WHERE rental.readerId = :readerId
              AND rental.bookPageId = :bookPageId
              AND rental.rentedAt <= :now
              AND rental.expiresAt > :now
            ORDER BY rental.expiresAt DESC
            """)
    List<PageRental> findActive(
            @Param("readerId") long readerId,
            @Param("bookPageId") long bookPageId,
            @Param("now") Instant now);
}
