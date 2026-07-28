package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkLedger;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InkLedgerRepository extends Repository<InkLedger, Long> {

    InkLedger save(InkLedger ledger);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ledger FROM InkLedger ledger WHERE ledger.inkPurchaseId = :inkPurchaseId")
    Optional<InkLedger> findByInkPurchaseIdForUpdate(
            @Param("inkPurchaseId") long inkPurchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ledger FROM InkLedger ledger WHERE ledger.pageRentalId = :pageRentalId")
    Optional<InkLedger> findByPageRentalIdForUpdate(
            @Param("pageRentalId") long pageRentalId);

    @Query("SELECT rental.readerId FROM PageRental rental WHERE rental.id = :pageRentalId")
    Optional<Long> findPageRentalReaderId(@Param("pageRentalId") long pageRentalId);

    @Query(
            value =
                    """
                    SELECT new com.example.ilgeobolkka.ink.repository.InkLedgerEntryQuery(
                        ledger.type,
                        ledger.amount,
                        ledger.balanceAfter,
                        book.title,
                        bookPage.pageNumber,
                        rental.rentedAt,
                        rental.expiresAt,
                        ledger.occurredAt
                    )
                    FROM InkLedger ledger
                    LEFT JOIN ledger.pageRental rental
                    LEFT JOIN rental.bookPage bookPage
                    LEFT JOIN bookPage.book book
                    WHERE ledger.readerId = :readerId
                    ORDER BY ledger.occurredAt DESC, ledger.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(ledger)
                    FROM InkLedger ledger
                    WHERE ledger.readerId = :readerId
                    """)
    Page<InkLedgerEntryQuery> findEntriesByReaderId(
            @Param("readerId") long readerId,
            Pageable pageable);
}
