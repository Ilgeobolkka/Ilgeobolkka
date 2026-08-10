package com.example.ilgeobolkka.contentimport.manifest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ContentManifestFormatValidator {

    private static final String INITIAL_CONTENT_VERSION = "initial-v1";
    private static final String AI_ROUTE_CONTENT_VERSION = "ai-route-v2";
    private static final Set<Integer> ALLOWED_ADDITIONAL_INK = Set.of(0, 5, 10, 15);

    private ContentManifestFormatValidator() {}

    static void validate(InitialContentManifest manifest) {
        require(manifest != null, "초기 콘텐츠 manifest가 필요합니다.");
        require(
                INITIAL_CONTENT_VERSION.equals(manifest.contentVersion()),
                "초기 콘텐츠 manifest의 contentVersion은 initial-v1이어야 합니다.");
        require(manifest.books() != null, "초기 콘텐츠 manifest의 books는 배열이어야 합니다.");

        Set<Long> bookIds = new HashSet<>();
        for (InitialContentManifest.Book book : manifest.books()) {
            require(book != null, "초기 콘텐츠 manifest의 book은 null일 수 없습니다.");
            validateBookIdentity(
                    book.bookId(), book.pdfPath(), book.pdfSha256(), book.totalPageCount());
            require(bookIds.add(book.bookId()), "초기 콘텐츠 manifest의 bookId가 중복됩니다.");
        }
    }

    static void validate(AiRouteContentManifest manifest) {
        require(manifest != null, "AI 경로 manifest가 필요합니다.");
        require(
                AI_ROUTE_CONTENT_VERSION.equals(manifest.contentVersion()),
                "AI 경로 manifest의 contentVersion은 ai-route-v2여야 합니다.");
        requireNonBlank(manifest.dataPolicyVersion(), "dataPolicyVersion");
        requireNonBlank(manifest.embeddingModel(), "embeddingModel");
        require(manifest.embeddingDimensions() > 0, "embeddingDimensions는 양수여야 합니다.");
        require(manifest.books() != null, "AI 경로 manifest의 books는 배열이어야 합니다.");

        Set<Long> bookIds = new HashSet<>();
        for (AiRouteContentManifest.Book book : manifest.books()) {
            require(book != null, "AI 경로 manifest의 book은 null일 수 없습니다.");
            validateBookIdentity(
                    book.bookId(), book.pdfPath(), book.pdfSha256(), book.totalPageCount());
            require(bookIds.add(book.bookId()), "AI 경로 manifest의 bookId가 중복됩니다.");
            require(book.pages() != null, "AI 경로 manifest의 pages는 배열이어야 합니다.");
            validatePages(book.pages());
        }
    }

    static void validate(AiRouteEvaluationDataset evaluation) {
        require(evaluation != null, "AI 경로 evaluation이 필요합니다.");
        require(
                AI_ROUTE_CONTENT_VERSION.equals(evaluation.contentVersion()),
                "AI 경로 evaluation의 contentVersion은 ai-route-v2여야 합니다.");
        require(evaluation.cases() != null, "AI 경로 evaluation의 cases는 배열이어야 합니다.");

        Set<String> caseIds = new HashSet<>();
        for (AiRouteEvaluationDataset.EvaluationCase evaluationCase : evaluation.cases()) {
            require(evaluationCase != null, "AI 경로 evaluation case는 null일 수 없습니다.");
            requireNonBlank(evaluationCase.caseId(), "caseId");
            require(caseIds.add(evaluationCase.caseId()), "AI 경로 evaluation의 caseId가 중복됩니다.");
            require(evaluationCase.bookId() > 0, "evaluation bookId는 양수여야 합니다.");
            requireNonBlank(evaluationCase.purpose(), "purpose");
            validateOwnershipInput(evaluationCase);
            requirePositiveNumbers(evaluationCase.activeRentalPageNumbers(), "activeRentalPageNumbers");
            requireNonBlankStrings(evaluationCase.requiredConcepts(), "requiredConcepts", true);
            requireNonBlankStrings(evaluationCase.helpfulConcepts(), "helpfulConcepts", false);
            validateRequiredPrerequisites(evaluationCase.requiredPrerequisites());
            requirePositiveNumbers(evaluationCase.irrelevantPageNumbers(), "irrelevantPageNumbers");
            validateDuplicatePageGroups(evaluationCase.duplicatePageGroups());
            requirePositiveNumbers(evaluationCase.referencePageNumbers(), "referencePageNumbers");
            requirePositiveNumbers(
                    evaluationCase.allowedAlternativePageNumbers(),
                    "allowedAlternativePageNumbers");
        }
    }

    private static void validatePages(List<AiRouteContentManifest.Page> pages) {
        Set<Integer> pageNumbers = new HashSet<>();
        for (AiRouteContentManifest.Page page : pages) {
            require(page != null, "AI 경로 manifest의 page는 null일 수 없습니다.");
            require(page.pageNumber() > 0, "pageNumber는 양수여야 합니다.");
            require(pageNumbers.add(page.pageNumber()), "AI 경로 manifest의 pageNumber가 중복됩니다.");
            requireNonBlank(page.chapter(), "chapter");
            requireNonBlank(page.section(), "section");
            requireNonBlankStrings(page.primaryConcepts(), "primaryConcepts", true);
            requireNonBlankStrings(page.secondaryConcepts(), "secondaryConcepts", false);
            require(page.contentRole() != null, "contentRole은 필수입니다.");
            requireNonBlank(page.aiAnalysisText(), "aiAnalysisText");
            requireSha256(page.aiAnalysisInputSha256(), "aiAnalysisInputSha256");
            requireNonBlank(page.aiPublicGuideTopic(), "aiPublicGuideTopic");
            require(
                    page.estimatedReadingSeconds() > 0,
                    "estimatedReadingSeconds는 양수여야 합니다.");
            requirePositiveNumbers(page.prerequisitePageNumbers(), "prerequisitePageNumbers");
            requireNonBlankStrings(page.duplicateGroupKeys(), "duplicateGroupKeys", false);
        }
    }

    private static void validateBookIdentity(
            long bookId, String pdfPath, String pdfSha256, int totalPageCount) {
        require(bookId > 0, "bookId는 양수여야 합니다.");
        requireNonBlank(pdfPath, "pdfPath");
        requireSha256(pdfSha256, "pdfSha256");
        require(totalPageCount > 0, "totalPageCount는 양수여야 합니다.");
    }

    private static void validateOwnershipInput(
            AiRouteEvaluationDataset.EvaluationCase evaluationCase) {
        if (evaluationCase.owned()) {
            require(
                    evaluationCase.maxAdditionalInk() == null,
                    "소장 evaluation은 maxAdditionalInk가 null이어야 합니다.");
            require(evaluationCase.depth() != null, "소장 evaluation은 depth가 필요합니다.");
            return;
        }
        require(evaluationCase.depth() == null, "비소장 evaluation은 depth가 null이어야 합니다.");
        require(
                evaluationCase.maxAdditionalInk() != null
                        && ALLOWED_ADDITIONAL_INK.contains(
                                evaluationCase.maxAdditionalInk()),
                "비소장 evaluation의 maxAdditionalInk는 0, 5, 10, 15 중 하나여야 합니다.");
    }

    private static void validateRequiredPrerequisites(
            List<AiRouteEvaluationDataset.RequiredPrerequisite> prerequisites) {
        require(prerequisites != null, "requiredPrerequisites는 배열이어야 합니다.");
        for (AiRouteEvaluationDataset.RequiredPrerequisite prerequisite : prerequisites) {
            require(prerequisite != null, "requiredPrerequisites 항목은 null일 수 없습니다.");
            require(
                    prerequisite.beforePageNumber() > 0
                            && prerequisite.afterPageNumber() > 0,
                    "requiredPrerequisites 페이지 번호는 양수여야 합니다.");
        }
    }

    private static void validateDuplicatePageGroups(List<List<Integer>> groups) {
        require(groups != null, "duplicatePageGroups는 배열이어야 합니다.");
        for (List<Integer> group : groups) {
            require(
                    group != null && !group.isEmpty(),
                    "duplicatePageGroups 항목은 비어 있을 수 없습니다.");
            requirePositiveNumbers(group, "duplicatePageGroups");
        }
    }

    private static void requireNonBlankStrings(
            List<String> values, String fieldName, boolean requireNotEmpty) {
        require(values != null, fieldName + "는 배열이어야 합니다.");
        if (requireNotEmpty) {
            require(!values.isEmpty(), fieldName + "는 하나 이상의 항목이 필요합니다.");
        }
        for (String value : values) {
            requireNonBlank(value, fieldName + " 항목");
        }
    }

    private static void requirePositiveNumbers(List<Integer> values, String fieldName) {
        require(values != null, fieldName + "는 배열이어야 합니다.");
        for (Integer value : values) {
            require(value != null && value > 0, fieldName + " 항목은 양수여야 합니다.");
        }
    }

    private static void requireSha256(String value, String fieldName) {
        require(
                value != null && value.matches("[0-9a-f]{64}"),
                fieldName + "는 소문자 64자리 SHA-256이어야 합니다.");
    }

    private static void requireNonBlank(String value, String fieldName) {
        require(value != null && !value.isBlank(), fieldName + "는 필수 문자열입니다.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ContentManifestFormatException(message);
        }
    }
}
