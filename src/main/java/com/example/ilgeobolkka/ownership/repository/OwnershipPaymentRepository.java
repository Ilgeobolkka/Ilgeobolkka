package com.example.ilgeobolkka.ownership.repository;

import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnershipPaymentRepository extends JpaRepository<OwnershipPayment, Long> {

    Optional<OwnershipPayment> findFirstByReaderIdAndBookIdAndStatusOrderByIdDesc(
            long readerId,
            long bookId,
            OwnershipPaymentStatus status);

    Optional<OwnershipPayment> findByPaymentIdAndReaderId(UUID paymentId, long readerId);

    @Query("SELECT payment.readerId FROM OwnershipPayment payment WHERE payment.paymentId = :paymentId")
    Optional<Long> findReaderIdByPaymentId(@Param("paymentId") UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);

    long countByReaderIdAndStatus(long readerId, OwnershipPaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT payment
            FROM OwnershipPayment payment
            WHERE payment.paymentId = :paymentId
              AND payment.readerId = :readerId
            """)
    Optional<OwnershipPayment> findByPaymentIdAndReaderIdForUpdate(
            @Param("paymentId") UUID paymentId,
            @Param("readerId") long readerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT payment
            FROM OwnershipPayment payment
            WHERE payment.paymentId = :paymentId
            """)
    Optional<OwnershipPayment> findByPaymentIdForUpdate(@Param("paymentId") UUID paymentId);

    @Query(
            value =
                    """
                    SELECT payment.paymentId AS paymentId,
                           payment.bookId AS bookId,
                           book.title AS bookTitle,
                           payment.amountWon AS amountWon,
                           payment.paidAt AS paidAt
                    FROM OwnershipPayment payment
                    JOIN payment.book book
                    WHERE payment.readerId = :readerId AND payment.status = :status
                    ORDER BY payment.paidAt DESC, payment.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(payment.id)
                    FROM OwnershipPayment payment
                    WHERE payment.readerId = :readerId AND payment.status = :status
                    """)
    Page<OwnershipPaymentEntryProjection> findEntriesByReaderIdAndStatus(
            @Param("readerId") long readerId,
            @Param("status") OwnershipPaymentStatus status,
            Pageable pageable);
}
