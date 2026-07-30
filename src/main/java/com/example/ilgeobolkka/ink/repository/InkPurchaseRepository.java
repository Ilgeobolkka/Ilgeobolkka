package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkPurchase;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InkPurchaseRepository extends JpaRepository<InkPurchase, Long> {

    Optional<InkPurchase> findByPaymentIdAndReaderId(UUID paymentId, long readerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT purchase
            FROM InkPurchase purchase
            WHERE purchase.paymentId = :paymentId
              AND purchase.readerId = :readerId
            """)
    Optional<InkPurchase> findByPaymentIdAndReaderIdForUpdate(
            @Param("paymentId") UUID paymentId,
            @Param("readerId") long readerId);
}
