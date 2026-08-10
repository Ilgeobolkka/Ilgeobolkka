package com.example.ilgeobolkka.airoute.entity;

import com.example.ilgeobolkka.book.entity.BookPage;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "ai_route_prerequisite",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_ai_route_prerequisite_edge",
                        columnNames = {
                            "book_id", "prerequisite_page_number", "dependent_page_number"
                        }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRoutePrerequisite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "prerequisite_page_number", nullable = false)
    private int prerequisitePageNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "prerequisite_page_number",
                        referencedColumnName = "page_number",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ai_route_prerequisite_prerequisite_page"))
    private BookPage prerequisitePage;

    @Column(name = "dependent_page_number", nullable = false)
    private int dependentPageNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "book_id",
                        referencedColumnName = "book_id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "dependent_page_number",
                        referencedColumnName = "page_number",
                        insertable = false,
                        updatable = false)
            },
            foreignKey = @ForeignKey(name = "fk_ai_route_prerequisite_dependent_page"))
    private BookPage dependentPage;

    public static AiRoutePrerequisite create(
            long bookId, int prerequisitePageNumber, int dependentPageNumber) {
        if (prerequisitePageNumber == dependentPageNumber) {
            throw new IllegalArgumentException("선수 페이지와 의존 페이지는 달라야 합니다.");
        }

        AiRoutePrerequisite prerequisite = new AiRoutePrerequisite();
        prerequisite.bookId = bookId;
        prerequisite.prerequisitePageNumber = prerequisitePageNumber;
        prerequisite.dependentPageNumber = dependentPageNumber;
        return prerequisite;
    }
}
