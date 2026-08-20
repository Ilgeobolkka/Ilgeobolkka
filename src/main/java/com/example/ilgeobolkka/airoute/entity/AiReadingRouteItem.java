package com.example.ilgeobolkka.airoute.entity;

import com.example.ilgeobolkka.book.entity.BookPage;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "ai_reading_route_item",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_ai_reading_route_item_position",
                    columnNames = {"route_id", "position"}),
            @UniqueConstraint(
                    name = "uk_ai_reading_route_item_page",
                    columnNames = {"route_id", "book_page_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReadingRouteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "route_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ai_reading_route_item_route_book"))
    private AiReadingRoute route;

    @Column(name = "book_page_id", nullable = false)
    private Long bookPageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "book_page_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ai_reading_route_item_page"))
    private BookPage bookPage;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "relevance", nullable = false, length = 20)
    private AiRouteItemRelevance relevance;

    @Column(name = "prerequisite", nullable = false)
    private boolean prerequisite;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    private AiRouteItemRole role;

    @Column(name = "opened_at", columnDefinition = "DATETIME(6)")
    private Instant openedAt;

    public static AiReadingRouteItem create(
            long routeId,
            long bookId,
            long bookPageId,
            int position,
            AiRouteItemRelevance relevance,
            boolean prerequisite,
            AiRouteItemRole role) {
        if (position <= 0) {
            throw new IllegalArgumentException("저장 경로 순서는 양수여야 합니다.");
        }

        AiReadingRouteItem item = new AiReadingRouteItem();
        item.routeId = routeId;
        item.bookId = bookId;
        item.bookPageId = bookPageId;
        item.position = position;
        item.relevance = relevance;
        item.prerequisite = prerequisite;
        item.role = role;
        return item;
    }

    public void markOpened(Instant openedAt) {
        if (openedAt == null) {
            throw new IllegalArgumentException("최초 열람 시각은 필수입니다.");
        }
        if (this.openedAt == null) {
            this.openedAt = openedAt;
        }
    }
}
