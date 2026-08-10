package com.example.ilgeobolkka.airoute.entity;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.reader.entity.Reader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "ai_route_generation",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_ai_route_generation_id_book",
                    columnNames = {"generation_id", "book_id"}),
            @UniqueConstraint(
                    name = "uk_ai_route_generation_reader_idempotency",
                    columnNames = {"reader_id", "idempotency_key"}),
            @UniqueConstraint(
                    name = "uk_ai_route_generation_saved_route",
                    columnNames = "saved_route_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRouteGeneration {

    @Id
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
            foreignKey = @ForeignKey(name = "fk_ai_route_generation_reader"))
    private Reader reader;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_route_generation_book"))
    private Book book;

    @Column(name = "content_version", nullable = false, length = 100)
    private String contentVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 36,
            columnDefinition = "CHAR(36)")
    private UUID idempotencyKey;

    @Column(
            name = "request_fingerprint",
            nullable = false,
            length = 64,
            columnDefinition = "CHAR(64)")
    private String requestFingerprint;

    @Column(name = "normalized_purpose", length = 200)
    private String normalizedPurpose;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "request_type", length = 20)
    private AiRouteRequestType requestType;

    @Column(name = "max_additional_ink")
    private Integer maxAdditionalInk;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "depth", length = 20)
    private AiRouteDepth depth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private AiRouteGenerationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "no_route_reason", length = 40)
    private AiRouteNoRouteReason noRouteReason;

    @Column(name = "minimum_required_ink")
    private Integer minimumRequiredInk;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "saved_route_id")
    private Long savedRouteId;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private Instant completedAt;

    @Column(name = "expires_at", columnDefinition = "DATETIME(6)")
    private Instant expiresAt;

    public static AiRouteGeneration start(
            UUID generationId,
            long readerId,
            UUID idempotencyKey,
            String requestFingerprint,
            AiRouteGenerationCommand command,
            Instant createdAt) {
        if (command == null) {
            throw new IllegalArgumentException("AI 경로 생성 명령은 필수입니다.");
        }
        AiRouteGeneration generation = new AiRouteGeneration();
        generation.generationId = generationId;
        generation.readerId = readerId;
        generation.bookId = command.bookId();
        generation.contentVersion = command.contentVersion();
        generation.idempotencyKey = idempotencyKey;
        generation.requestFingerprint = requestFingerprint;
        generation.normalizedPurpose = command.normalizedPurpose();
        generation.requestType = command.requestType();
        generation.maxAdditionalInk = command.maxAdditionalInk();
        generation.depth = command.depth();
        generation.status = AiRouteGenerationStatus.GENERATING;
        generation.createdAt = createdAt;
        return generation;
    }

    public void completeRoute(Instant completedAt, Instant expiresAt) {
        requireGenerating();
        setCompletionTimes(completedAt, expiresAt);
        status = AiRouteGenerationStatus.ROUTE;
    }

    public void completeNoRoute(
            AiRouteNoRouteReason reason,
            Integer minimumRequiredInk,
            Instant completedAt,
            Instant expiresAt) {
        requireGenerating();
        if (reason == null) {
            throw new IllegalArgumentException("경로 없음 사유는 필수입니다.");
        }
        if (reason == AiRouteNoRouteReason.NO_RELEVANT_PAGES && minimumRequiredInk != null) {
            throw new IllegalArgumentException("관련 페이지 없음 결과에는 최소 잉크가 없어야 합니다.");
        }
        if (reason == AiRouteNoRouteReason.INSUFFICIENT_BUDGET
                && (minimumRequiredInk == null
                        || maxAdditionalInk == null
                        || minimumRequiredInk <= maxAdditionalInk)) {
            throw new IllegalArgumentException("예산 부족 결과에는 예산보다 큰 최소 잉크가 필요합니다.");
        }

        setCompletionTimes(completedAt, expiresAt);
        status = AiRouteGenerationStatus.NO_ROUTE;
        noRouteReason = reason;
        this.minimumRequiredInk = minimumRequiredInk;
    }

    public void fail(String failureCode, Instant completedAt, Instant expiresAt) {
        requireGenerating();
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("실패 코드는 필수입니다.");
        }

        setCompletionTimes(completedAt, expiresAt);
        status = AiRouteGenerationStatus.FAILED;
        this.failureCode = failureCode;
    }

    public void saveAsRoute(AiReadingRoute savedRoute) {
        if (status != AiRouteGenerationStatus.ROUTE) {
            throw new IllegalStateException("ROUTE 생성 결과만 저장할 수 있습니다.");
        }
        if (savedRoute == null || savedRoute.getId() == null) {
            throw new IllegalArgumentException("저장한 경로는 필수입니다.");
        }
        if (!generationId.equals(savedRoute.getGenerationId())) {
            throw new IllegalArgumentException("같은 생성 결과로 저장한 경로만 연결할 수 있습니다.");
        }
        if (!Objects.equals(readerId, savedRoute.getReaderId())
                || !Objects.equals(bookId, savedRoute.getBookId())
                || !Objects.equals(contentVersion, savedRoute.getContentVersion())
                || !Objects.equals(normalizedPurpose, savedRoute.getNormalizedPurpose())
                || requestType != savedRoute.getRequestType()
                || !Objects.equals(maxAdditionalInk, savedRoute.getMaxAdditionalInk())
                || depth != savedRoute.getDepth()) {
            throw new IllegalArgumentException("생성 결과와 저장 경로의 소유자·도서·요청이 일치해야 합니다.");
        }

        status = AiRouteGenerationStatus.SAVED;
        savedRouteId = savedRoute.getId();
        normalizedPurpose = null;
        requestType = null;
        maxAdditionalInk = null;
        depth = null;
    }

    public void consume() {
        if (status != AiRouteGenerationStatus.SAVED) {
            throw new IllegalStateException("SAVED 생성 결과만 소비 처리할 수 있습니다.");
        }
        status = AiRouteGenerationStatus.CONSUMED;
        savedRouteId = null;
    }

    private void requireGenerating() {
        if (status != AiRouteGenerationStatus.GENERATING) {
            throw new IllegalStateException("GENERATING 상태만 완료할 수 있습니다.");
        }
    }

    private void setCompletionTimes(Instant completedAt, Instant expiresAt) {
        if (completedAt == null
                || expiresAt == null
                || completedAt.isBefore(createdAt)
                || !expiresAt.isAfter(completedAt)) {
            throw new IllegalArgumentException("완료·만료 시각이 생성 순서와 맞지 않습니다.");
        }
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
    }
}
