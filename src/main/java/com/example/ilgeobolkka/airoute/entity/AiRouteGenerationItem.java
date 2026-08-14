package com.example.ilgeobolkka.airoute.entity;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "ai_route_generation_item",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_ai_route_generation_item_position",
                    columnNames = {"generation_id", "position"}),
            @UniqueConstraint(
                    name = "uk_ai_route_generation_item_page",
                    columnNames = {"generation_id", "book_page_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRouteGenerationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "generation_id",
            nullable = false,
            length = 36,
            columnDefinition = "CHAR(36)")
    private UUID generationId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "generation_id",
                        referencedColumnName = "generation_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ai_route_generation_item_generation_book"))
    private AiRouteGeneration generation;

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
            foreignKey = @ForeignKey(name = "fk_ai_route_generation_item_page"))
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "additional_cost_status", nullable = false, length = 20)
    private AiRouteAdditionalCostStatus additionalCostStatus;

    public static AiRouteGenerationItem create(
            UUID generationId,
            long bookId,
            long bookPageId,
            int position,
            AiRouteItemRelevance relevance,
            boolean prerequisite,
            AiRouteItemRole role,
            AiRouteAdditionalCostStatus additionalCostStatus) {
        if (position <= 0) {
            throw new IllegalArgumentException("생성 경로 순서는 양수여야 합니다.");
        }
        if (additionalCostStatus == null) {
            throw new IllegalArgumentException("생성 시점 추가 비용 상태는 필수입니다.");
        }

        AiRouteGenerationItem item = new AiRouteGenerationItem();
        item.generationId = generationId;
        item.bookId = bookId;
        item.bookPageId = bookPageId;
        item.position = position;
        item.relevance = relevance;
        item.prerequisite = prerequisite;
        item.role = role;
        item.additionalCostStatus = additionalCostStatus;
        return item;
    }
}
