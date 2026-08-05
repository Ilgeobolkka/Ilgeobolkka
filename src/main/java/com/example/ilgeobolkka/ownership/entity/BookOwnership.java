package com.example.ilgeobolkka.ownership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(
        name = "book_ownership",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_book_ownership_reader_book",
                    columnNames = {"reader_id", "book_id"}),
            @UniqueConstraint(
                    name = "uk_book_ownership_payment", columnNames = "ownership_payment_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookOwnership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "ownership_payment_id", nullable = false)
    private Long ownershipPaymentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "reader_id",
                        referencedColumnName = "reader_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "ownership_payment_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_book_ownership_payment"))
    private OwnershipPayment ownershipPayment;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    public static BookOwnership create(
            long readerId,
            long bookId,
            long ownershipPaymentId,
            Instant createdAt) {
        if (readerId <= 0) {
            throw new IllegalArgumentException("독자 ID는 양수여야 합니다.");
        }
        if (bookId <= 0) {
            throw new IllegalArgumentException("도서 ID는 양수여야 합니다.");
        }
        if (ownershipPaymentId <= 0) {
            throw new IllegalArgumentException("소장 결제 ID는 양수여야 합니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("소장 생성 시각은 필수입니다.");
        }

        BookOwnership ownership = new BookOwnership();
        ownership.readerId = readerId;
        ownership.bookId = bookId;
        ownership.ownershipPaymentId = ownershipPaymentId;
        ownership.createdAt = createdAt;
        return ownership;
    }
}
