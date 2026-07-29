package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkOperationClaim;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InkOperationClaimRepository extends Repository<InkOperationClaim, Long> {

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ink_operation_claim
                        (reader_id, ink_purchase_id, claim_token)
                    VALUES (:readerId, :inkPurchaseId, :claimToken)
                    ON DUPLICATE KEY UPDATE id = id
                    """,
            nativeQuery = true)
    void insertGrantClaim(
            @Param("readerId") long readerId,
            @Param("inkPurchaseId") long inkPurchaseId,
            @Param("claimToken") String claimToken);

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO ink_operation_claim
                        (reader_id, page_rental_id, claim_token)
                    VALUES (:readerId, :pageRentalId, :claimToken)
                    ON DUPLICATE KEY UPDATE id = id
                    """,
            nativeQuery = true)
    void insertDeductionClaim(
            @Param("readerId") long readerId,
            @Param("pageRentalId") long pageRentalId,
            @Param("claimToken") String claimToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT claim
            FROM InkOperationClaim claim
            WHERE claim.inkPurchaseId = :inkPurchaseId
            """)
    Optional<InkOperationClaim> findGrantClaimForUpdate(
            @Param("inkPurchaseId") long inkPurchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT claim
            FROM InkOperationClaim claim
            WHERE claim.pageRentalId = :pageRentalId
            """)
    Optional<InkOperationClaim> findDeductionClaimForUpdate(
            @Param("pageRentalId") long pageRentalId);
}
