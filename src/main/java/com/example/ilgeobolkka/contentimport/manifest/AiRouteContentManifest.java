package com.example.ilgeobolkka.contentimport.manifest;

import java.util.List;

public record AiRouteContentManifest(
        String contentVersion,
        String dataPolicyVersion,
        String embeddingModel,
        int embeddingDimensions,
        List<Book> books)
        implements ContentManifest {

    public AiRouteContentManifest {
        if (books != null) {
            books = List.copyOf(books);
        }
    }

    public record Book(
            long bookId,
            String title,
            String pdfPath,
            String pdfSha256,
            int totalPageCount,
            boolean aiRouteCandidate,
            boolean aiExternalTransferAllowed,
            List<Page> pages) {

        public Book {
            if (pages != null) {
                pages = List.copyOf(pages);
            }
        }
    }

    public record Page(
            int pageNumber,
            String chapter,
            String section,
            List<String> primaryConcepts,
            List<String> secondaryConcepts,
            ContentRole contentRole,
            boolean aiRouteCandidatePage,
            String aiAnalysisText,
            String aiAnalysisInputSha256,
            String aiPublicGuideTopic,
            int estimatedReadingSeconds,
            List<Integer> prerequisitePageNumbers,
            List<String> duplicateGroupKeys) {

        public Page {
            if (primaryConcepts != null) {
                primaryConcepts = List.copyOf(primaryConcepts);
            }
            if (secondaryConcepts != null) {
                secondaryConcepts = List.copyOf(secondaryConcepts);
            }
            if (prerequisitePageNumbers != null) {
                prerequisitePageNumbers = List.copyOf(prerequisitePageNumbers);
            }
            if (duplicateGroupKeys != null) {
                duplicateGroupKeys = List.copyOf(duplicateGroupKeys);
            }
        }
    }

    public enum ContentRole {
        PREREQUISITE,
        CORE,
        EXAMPLE,
        COUNTERPOINT,
        CONCLUSION,
        /** 목차처럼 본문 설명을 담지 않는 구조 페이지. 항상 aiRouteCandidatePage=false다. */
        FRONT_MATTER
    }
}
