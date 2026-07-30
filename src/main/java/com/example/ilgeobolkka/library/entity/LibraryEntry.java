package com.example.ilgeobolkka.library.entity;

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
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "library_entry",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_library_entry_reader_book",
                        columnNames = {"reader_id", "book_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LibraryEntry {

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
            foreignKey = @ForeignKey(name = "fk_library_entry_reader"))
    private Reader reader;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "last_page_number", nullable = false)
    private int lastPageNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "last_page_number",
                        referencedColumnName = "page_number",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_library_entry_book_page"))
    private BookPage lastPage;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    public static LibraryEntry create(
            long readerId, long bookId, int lastPageNumber, Instant updatedAt) {
        LibraryEntry entry = new LibraryEntry();
        entry.readerId = readerId;
        entry.bookId = bookId;
        entry.lastPageNumber = lastPageNumber;
        entry.updatedAt = updatedAt;
        return entry;
    }

    public void moveTo(int lastPageNumber, Instant updatedAt) {
        this.lastPageNumber = lastPageNumber;
        this.updatedAt = updatedAt;
    }
}
