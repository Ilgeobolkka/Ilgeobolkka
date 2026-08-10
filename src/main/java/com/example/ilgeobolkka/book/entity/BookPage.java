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
    }

    public void clearAiRouteMetadata() {
        aiAnalysisText = null;
        aiPublicGuideTopic = null;
        estimatedReadingSeconds = null;
        embeddingModel = null;
        embeddingDimensions = null;
        embedding = null;
        duplicateGroupKeys = null;
    }
}
