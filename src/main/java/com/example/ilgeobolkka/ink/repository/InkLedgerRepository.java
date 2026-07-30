package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InkLedgerRepository extends JpaRepository<InkLedger, Long> {

    boolean existsByInkPurchaseId(long inkPurchaseId);

    boolean existsByPageRentalId(long pageRentalId);

    long countByReaderId(long readerId);

    @Query(
            value =
                    """
                    SELECT ledger.type AS type,
                           ledger.amount AS amount,
                           ledger.balanceAfter AS balanceAfter,
                           book.title AS bookTitle,
                           bookPage.pageNumber AS pageNumber,
                           rental.rentedAt AS rentedAt,
                           rental.expiresAt AS expiresAt,
                           ledger.occurredAt AS occurredAt
                    FROM InkLedger ledger
                    LEFT JOIN ledger.pageRental rental
                    LEFT JOIN rental.bookPage bookPage
                    LEFT JOIN bookPage.book book
                    WHERE ledger.readerId = :readerId
                    ORDER BY ledger.occurredAt DESC, ledger.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(ledger.id)
                    FROM InkLedger ledger
                    WHERE ledger.readerId = :readerId
                    """)
    Page<InkLedgerEntryProjection> findEntriesByReaderId(
            @Param("readerId") long readerId,
            Pageable pageable);
}
