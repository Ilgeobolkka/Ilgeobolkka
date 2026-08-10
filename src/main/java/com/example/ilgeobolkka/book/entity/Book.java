package com.example.ilgeobolkka.book.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "book",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_book_id_price",
                        columnNames = {"id", "price_won"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    private static final String INITIAL_CONTENT_VERSION = "initial-v1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "author", nullable = false, length = 255)
    private String author;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "cover_image_path", length = 500)
    private String coverImagePath;

    @Column(name = "total_page_count", nullable = false)
    private int totalPageCount;

    @Column(name = "price_won", nullable = false)
    private int priceWon;

    @Column(name = "content_version", nullable = false, length = 100)
    private String contentVersion = INITIAL_CONTENT_VERSION;

    @Column(name = "ai_route_supported", nullable = false)
    private boolean aiRouteSupported;

    @Column(name = "ai_external_transfer_allowed", nullable = false)
    private boolean aiExternalTransferAllowed;

    @Column(name = "ai_data_policy_version", length = 100)
    private String aiDataPolicyVersion;

    public void updateAiRouteMetadata(
            String contentVersion,
            boolean externalTransferAllowed,
            String dataPolicyVersion) {
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new IllegalArgumentException("콘텐츠 버전은 필수입니다.");
        }

        this.contentVersion = contentVersion;
        aiRouteSupported = false;
        aiExternalTransferAllowed = externalTransferAllowed;
        aiDataPolicyVersion = dataPolicyVersion;
    }

    public void activateAiRouteSupport() {
        if (!aiExternalTransferAllowed
                || aiDataPolicyVersion == null
                || aiDataPolicyVersion.isBlank()) {
            throw new IllegalStateException("외부 전송과 데이터 정책이 확인된 도서만 AI 경로를 지원할 수 있습니다.");
        }
        aiRouteSupported = true;
    }

    public void deactivateAiRouteSupport() {
        aiRouteSupported = false;
    }
}
