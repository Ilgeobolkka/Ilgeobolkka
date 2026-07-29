package com.example.ilgeobolkka.ink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Immutable
@Table(
        name = "ink_operation_claim",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_ink_operation_claim_purchase",
                    columnNames = "ink_purchase_id"),
            @UniqueConstraint(
                    name = "uk_ink_operation_claim_rental",
                    columnNames = "page_rental_id"),
            @UniqueConstraint(
                    name = "uk_ink_operation_claim_token",
                    columnNames = "claim_token")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InkOperationClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "ink_purchase_id")
    private Long inkPurchaseId;

    @Column(name = "page_rental_id")
    private Long pageRentalId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "claim_token", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID claimToken;

    public boolean isOwnedBy(UUID token) {
        return claimToken.equals(token);
    }
}
