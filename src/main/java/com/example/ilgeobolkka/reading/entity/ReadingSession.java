package com.example.ilgeobolkka.reading.entity;

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
import jakarta.persistence.OneToOne;
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
        name = "reading_session",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_reading_session_reader", columnNames = "reader_id"),
            @UniqueConstraint(name = "uk_reading_session_viewer", columnNames = "viewer_session_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reader_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_reading_session_reader"))
    private Reader reader;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "current_page_number", nullable = false)
    private int currentPageNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "current_page_number",
                        referencedColumnName = "page_number",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_reading_session_book_page"))
    private BookPage currentPage;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "viewer_session_id",
            nullable = false,
            length = 36,
            columnDefinition = "CHAR(36)")
    private UUID viewerSessionId;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    public boolean matchesViewer(UUID viewerSessionId) {
        return this.viewerSessionId.equals(viewerSessionId);
    }
}
