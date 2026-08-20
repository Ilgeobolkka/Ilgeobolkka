package com.example.ilgeobolkka.airoute.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "ai_route_current",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_ai_route_current_route", columnNames = "route_id"))
@IdClass(AiRouteCurrentId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRouteCurrent {

    @Id
    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Id
    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

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
                        name = "route_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ai_route_current_route"))
    private AiReadingRoute route;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    public static AiRouteCurrent select(
            long readerId, long bookId, AiReadingRoute route, Instant updatedAt) {
        requireSameOwnerAndBook(readerId, bookId, route);

        AiRouteCurrent current = new AiRouteCurrent();
        current.readerId = readerId;
        current.bookId = bookId;
        current.routeId = route.getId();
        current.route = route;
        current.updatedAt = updatedAt;
        return current;
    }

    public void changeRoute(AiReadingRoute route, Instant updatedAt) {
        requireSameOwnerAndBook(readerId, bookId, route);

        routeId = route.getId();
        this.route = route;
        this.updatedAt = updatedAt;
    }

    private static void requireSameOwnerAndBook(
            long readerId, long bookId, AiReadingRoute route) {
        if (route == null
                || !Objects.equals(readerId, route.getReaderId())
                || !Objects.equals(bookId, route.getBookId())) {
            throw new IllegalArgumentException("같은 독자와 도서의 저장 경로만 현재 경로로 지정할 수 있습니다.");
        }
    }
}
