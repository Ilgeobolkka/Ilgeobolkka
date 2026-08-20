package com.example.ilgeobolkka.airoute.entity;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
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
        name = "ai_reading_route",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_ai_reading_route_generation", columnNames = "generation_id"),
            @UniqueConstraint(
                    name = "uk_ai_reading_route_id_book", columnNames = {"id", "book_id"}),
            @UniqueConstraint(
                    name = "uk_ai_reading_route_id_generation",
                    columnNames = {"id", "generation_id"}),
            @UniqueConstraint(
                    name = "uk_ai_reading_route_owner",
                    columnNames = {"reader_id", "book_id", "id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiReadingRoute {

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

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reader_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_reading_route_reader"))
    private Reader reader;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_reading_route_book"))
    private Book book;

    @Column(name = "content_version", nullable = false, length = 100)
    private String contentVersion;

    @Column(name = "normalized_purpose", nullable = false, length = 200)
    private String normalizedPurpose;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "request_type", nullable = false, length = 20)
    private AiRouteRequestType requestType;

    @Column(name = "max_additional_ink")
    private Integer maxAdditionalInk;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "depth", length = 20)
    private AiRouteDepth depth;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "feedback", length = 20)
    private AiReadingRouteFeedback feedback;

    @Column(name = "feedback_at", columnDefinition = "DATETIME(6)")
    private Instant feedbackAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    public static AiReadingRoute createWithInkBudget(
            UUID generationId,
            long readerId,
            long bookId,
            String contentVersion,
            String normalizedPurpose,
            int maxAdditionalInk,
            Instant createdAt) {
        if (maxAdditionalInk < 0) {
            throw new IllegalArgumentException("추가 잉크 예산은 음수일 수 없습니다.");
        }
        AiReadingRoute route =
                create(
                        generationId,
                        readerId,
                        bookId,
                        contentVersion,
                        normalizedPurpose,
                        createdAt);
        route.requestType = AiRouteRequestType.INK_BUDGET;
        route.maxAdditionalInk = maxAdditionalInk;
        return route;
    }

    public static AiReadingRoute createWithOwnedDepth(
            UUID generationId,
            long readerId,
            long bookId,
            String contentVersion,
            String normalizedPurpose,
            AiRouteDepth depth,
            Instant createdAt) {
        if (depth == null) {
            throw new IllegalArgumentException("소장 도서의 경로 깊이는 필수입니다.");
        }
        AiReadingRoute route =
                create(
                        generationId,
                        readerId,
                        bookId,
                        contentVersion,
                        normalizedPurpose,
                        createdAt);
        route.requestType = AiRouteRequestType.OWNED_DEPTH;
        route.depth = depth;
        return route;
    }

    private static AiReadingRoute create(
            UUID generationId,
            long readerId,
            long bookId,
            String contentVersion,
            String normalizedPurpose,
            Instant createdAt) {
        AiReadingRoute route = new AiReadingRoute();
        route.generationId = generationId;
        route.readerId = readerId;
        route.bookId = bookId;
        route.contentVersion = contentVersion;
        route.normalizedPurpose = normalizedPurpose;
        route.createdAt = createdAt;
        return route;
    }

    public void complete(Instant completedAt) {
        if (completedAt == null || completedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("완료 시각은 생성 시각보다 빠를 수 없습니다.");
        }
        if (this.completedAt != null) {
            throw new IllegalStateException("이미 완료한 저장 경로입니다.");
        }
        this.completedAt = completedAt;
    }

    public void updateFeedback(AiReadingRouteFeedback feedback, Instant feedbackAt) {
        if (completedAt == null) {
            throw new IllegalStateException("완료한 저장 경로만 피드백을 남길 수 있습니다.");
        }
        if (feedback == null || feedbackAt == null) {
            throw new IllegalArgumentException("피드백과 피드백 시각은 필수입니다.");
        }
        if (feedbackAt.isBefore(completedAt)
                || (this.feedbackAt != null && feedbackAt.isBefore(this.feedbackAt))) {
            throw new IllegalArgumentException("피드백 시각은 완료 시각과 이전 피드백 시각보다 빠를 수 없습니다.");
        }
        this.feedback = feedback;
        this.feedbackAt = feedbackAt;
    }
}
