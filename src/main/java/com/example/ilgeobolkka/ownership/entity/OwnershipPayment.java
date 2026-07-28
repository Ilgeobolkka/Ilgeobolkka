package com.example.ilgeobolkka.ownership.entity;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.reader.entity.Reader;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "ownership_payment",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_ownership_payment_provider_id", columnNames = "payment_id"),
            @UniqueConstraint(
                    name = "uk_ownership_payment_owner",
                    columnNames = {"reader_id", "book_id", "id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnershipPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reader_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ownership_payment_reader"))
    private Reader reader;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "amount_won",
                        referencedColumnName = "price_won",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ownership_payment_book_price"))
    private Book book;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OwnershipPaymentStatus status;

    @Column(name = "amount_won", nullable = false)
    private int amountWon;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "paid_at", columnDefinition = "DATETIME(6)")
    private Instant paidAt;
}
