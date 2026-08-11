package com.example.ilgeobolkka.book.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "book_page",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_book_page_book_number",
                    columnNames = {"book_id", "page_number"}),
            @UniqueConstraint(
                    name = "uk_book_page_id_book", columnNames = {"id", "book_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "book_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_book_page_book"))
    private Book book;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 20)
    private BookPageContentType contentType;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "ai_analysis_text", columnDefinition = "MEDIUMTEXT")
    private String aiAnalysisText;

    @Column(name = "ai_public_guide_topic", length = 500)
    private String aiPublicGuideTopic;

    @Column(name = "estimated_reading_seconds")
    private Integer estimatedReadingSeconds;

    @JsonIgnore
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @JsonIgnore
    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding_json", columnDefinition = "JSON")
    private List<Double> embedding;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duplicate_group_keys", columnDefinition = "JSON")
    private List<String> duplicateGroupKeys;

    /**
     * AI 경로 후보 집합에 넣을 페이지인가. 목차 같은 구조 페이지와 미지원 도서의 페이지는 {@code false}다.
     * 후보 검색은 이 값으로 대상을 고른 뒤 벡터 유효성을 검증하므로, 벡터가 있는 페이지만 고르는
     * 방식으로 대신하지 않는다.
     */
    @Column(name = "ai_route_candidate", nullable = false)
    private boolean aiRouteCandidate;

    public List<Double> getEmbedding() {
        return embedding == null ? null : List.copyOf(embedding);
    }

    public List<String> getDuplicateGroupKeys() {
        return duplicateGroupKeys == null ? null : List.copyOf(duplicateGroupKeys);
    }

    public void updateAiRouteMetadata(
            String analysisText,
            String publicGuideTopic,
            int estimatedReadingSeconds,
            String embeddingModel,
            List<Double> embedding,
            List<String> duplicateGroupKeys) {
        if (analysisText == null
                || analysisText.isBlank()
                || publicGuideTopic == null
                || publicGuideTopic.isBlank()
                || embeddingModel == null
                || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("AI 페이지 분석 텍스트·공개 가이드·embedding 모델은 필수입니다.");
        }
        if (estimatedReadingSeconds <= 0) {
            throw new IllegalArgumentException("예상 독서 시간은 양수여야 합니다.");
        }
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("embedding은 비어 있지 않아야 합니다.");
        }
        if (duplicateGroupKeys == null) {
            throw new IllegalArgumentException("중복 그룹은 필수입니다.");
        }

        aiAnalysisText = analysisText;
        aiPublicGuideTopic = publicGuideTopic;
        this.estimatedReadingSeconds = estimatedReadingSeconds;
        this.embeddingModel = embeddingModel;
        embeddingDimensions = embedding.size();
        this.embedding = List.copyOf(embedding);
        this.duplicateGroupKeys = List.copyOf(duplicateGroupKeys);
        // 일곱 필드를 모두 갖춘 페이지만 후보다. DB의 ck_book_page_candidate_metadata와 같은 계약이다.
        aiRouteCandidate = true;
    }

    /**
     * 목차처럼 후보가 아니면서 분석 메타데이터는 가지는 구조 페이지를 채운다.
     *
     * <p>임베딩 세 필드는 비운다. DB의 {@code ck_book_page_candidate_metadata}가 후보가 아닌 페이지의
     * 임베딩을 금지하므로 이 메서드와 제약이 같은 계약이다.
     */
    public void updateStructuralPageMetadata(
            String analysisText,
            String publicGuideTopic,
            int estimatedReadingSeconds,
            List<String> duplicateGroupKeys) {
        if (analysisText == null
                || analysisText.isBlank()
                || publicGuideTopic == null
                || publicGuideTopic.isBlank()) {
            throw new IllegalArgumentException("구조 페이지도 분석 텍스트와 공개 가이드는 필수입니다.");
        }
        if (estimatedReadingSeconds <= 0) {
            throw new IllegalArgumentException("예상 독서 시간은 양수여야 합니다.");
        }
        if (duplicateGroupKeys == null) {
            throw new IllegalArgumentException("중복 그룹은 필수입니다.");
        }

        aiAnalysisText = analysisText;
        aiPublicGuideTopic = publicGuideTopic;
        this.estimatedReadingSeconds = estimatedReadingSeconds;
        this.duplicateGroupKeys = List.copyOf(duplicateGroupKeys);
        embeddingModel = null;
        embeddingDimensions = null;
        embedding = null;
        aiRouteCandidate = false;
    }

    public void clearAiRouteMetadata() {
        aiAnalysisText = null;
        aiPublicGuideTopic = null;
        estimatedReadingSeconds = null;
        embeddingModel = null;
        embeddingDimensions = null;
        embedding = null;
        duplicateGroupKeys = null;
        aiRouteCandidate = false;
    }
}
