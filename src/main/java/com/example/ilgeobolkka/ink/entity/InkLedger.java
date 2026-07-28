package com.example.ilgeobolkka.ink.entity;

import com.example.ilgeobolkka.rental.entity.PageRental;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "ink_ledger",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_ink_ledger_purchase", columnNames = "ink_purchase_id"),
            @UniqueConstraint(name = "uk_ink_ledger_rental", columnNames = "page_rental_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InkLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private InkLedgerType type;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "ink_purchase_id")
    private Long inkPurchaseId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "reader_id",
                        referencedColumnName = "reader_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "ink_purchase_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ink_ledger_purchase"))
    private InkPurchase inkPurchase;

    @Column(name = "page_rental_id")
    private Long pageRentalId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "reader_id",
                        referencedColumnName = "reader_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "page_rental_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ink_ledger_rental"))
    private PageRental pageRental;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant occurredAt;
}
