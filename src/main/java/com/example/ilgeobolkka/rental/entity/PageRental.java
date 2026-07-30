package com.example.ilgeobolkka.rental.entity;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.reader.entity.Reader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "page_rental",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_page_rental_reader_id",
                        columnNames = {"reader_id", "id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageRental {

    private static final Duration RENTAL_PERIOD = Duration.ofDays(30);

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
            foreignKey = @ForeignKey(name = "fk_page_rental_reader"))
    private Reader reader;

    @Column(name = "book_page_id", nullable = false)
    private Long bookPageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_page_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_page_rental_book_page"))
    private BookPage bookPage;

    @Column(name = "rented_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant rentedAt;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant expiresAt;

    public static PageRental start(long readerId, long bookPageId, Instant rentedAt) {
        PageRental rental = new PageRental();
        rental.readerId = readerId;
        rental.bookPageId = bookPageId;
        rental.rentedAt = rentedAt;
        rental.expiresAt = rentedAt.plus(RENTAL_PERIOD);
        return rental;
    }

    /** 활성 대여는 {@code serverNow < expiresAt}일 때이며 {@code expiresAt}부터 만료다. */
    public boolean isActive(Instant now) {
        return now.isBefore(expiresAt);
    }
}
