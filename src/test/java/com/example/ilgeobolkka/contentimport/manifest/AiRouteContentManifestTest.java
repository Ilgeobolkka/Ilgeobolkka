package com.example.ilgeobolkka.contentimport.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiRouteContentManifestTest {

    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);

    private final ContentManifestParser parser = new ContentManifestParser(new ObjectMapper());

    @Test
    void 최소_AI_manifest와_evaluation을_불변_타입으로_읽는다() {
        ContentManifest parsedManifest = parser.parseManifest(validManifest());
        AiRouteContentManifest manifest =
                assertInstanceOf(AiRouteContentManifest.class, parsedManifest);

        assertEquals("ai-route-v2", manifest.contentVersion());
        assertEquals(1536, manifest.embeddingDimensions());
        assertEquals(1, manifest.books().size());
        assertEquals(AiRouteContentManifest.ContentRole.CORE,
                manifest.books().getFirst().pages().getFirst().contentRole());

        AiRouteEvaluationDataset evaluation = parser.parseEvaluation(validEvaluation());

        assertEquals("ai-route-v2", evaluation.contentVersion());
        assertEquals(1, evaluation.cases().size());
        assertEquals(5, evaluation.cases().getFirst().maxAdditionalInk());
    }

    @Test
    void 비지원_도서와_소장_evaluation의_정상_형식을_읽는다() {
        AiRouteContentManifest manifest =
                assertInstanceOf(
                        AiRouteContentManifest.class,
                        parser.parseManifest(validManifest(validNonCandidateBook())));

        assertFalse(manifest.books().getFirst().aiRouteCandidate());
        assertEquals(0, manifest.books().getFirst().pages().size());

        AiRouteEvaluationDataset evaluation = parser.parseEvaluation(validOwnedEvaluation());
        AiRouteEvaluationDataset.EvaluationCase evaluationCase =
                evaluation.cases().getFirst();

        assertEquals(AiRouteEvaluationDataset.Depth.DEEP, evaluationCase.depth());
        assertEquals(1, evaluationCase.requiredPrerequisites().size());
        assertEquals(2, evaluationCase.duplicatePageGroups().getFirst().size());
    }

    @Test
    void parser_결과의_중첩_목록을_변경할_수_없다() {
        AiRouteContentManifest manifest =
                assertInstanceOf(AiRouteContentManifest.class, parser.parseManifest(validManifest()));
        AiRouteEvaluationDataset evaluation = parser.parseEvaluation(validOwnedEvaluation());

        assertThrows(
                UnsupportedOperationException.class,
                () -> manifest.books().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> manifest.books().getFirst().pages().getFirst().primaryConcepts().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        evaluation.cases()
                                .getFirst()
                                .duplicatePageGroups()
                                .getFirst()
                                .add(3));
    }

    @Test
    void initial_v1_manifest를_명시적_subtype으로_읽는다() {
        String json = """
                {
                  "contentVersion": "initial-v1",
                  "books": [{
                    "bookId": 1,
                    "pdfPath": "pdfs/book-001.pdf",
                    "pdfSha256": "%s",
                    "totalPageCount": 4
                  }]
                }
                """.formatted(SHA_A);

        ContentManifest parsed = parser.parseManifest(json);

        InitialContentManifest manifest =
                assertInstanceOf(InitialContentManifest.class, parsed);
        assertEquals(1, manifest.books().getFirst().bookId());
    }

    @Test
    void 필수_필드_누락과_unknown_field를_거부한다() {
        String missingRequired =
                validManifest().replace("\"aiExternalTransferAllowed\": true,", "");
        String unknownField =
                validManifest().replace(
                        "\"embeddingDimensions\": 1536,",
                        "\"embeddingDimensions\": 1536,\n  \"unexpected\": true,");

        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(missingRequired));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(unknownField));
    }

    @Test
    void null_배열과_blank_문자열을_거부한다() {
        String nullArray =
                validManifest().replace("\"secondaryConcepts\": []", "\"secondaryConcepts\": null");
        String blankString =
                validManifest().replace("\"aiAnalysisText\": \"분석 텍스트\"", "\"aiAnalysisText\": \" \"");

        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(nullArray));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(blankString));
    }

    @Test
    void 중복_bookId_pageNumber_caseId를_거부한다() {
        String book = validBook(validPage());
        String duplicateBooks = validManifest(book + "," + book);
        String duplicatePages = validManifest(validBook(validPage() + "," + validPage()));
        String evaluationCase = validEvaluationCase();
        String duplicateCases = validEvaluation(evaluationCase + "," + evaluationCase);

        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(duplicateBooks));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(duplicatePages));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(duplicateCases));
    }

    @Test
    void 잘못된_Enum과_숫자_범위를_거부한다() {
        String invalidRole =
                validManifest()
                        .replace("\"contentRole\": \"CORE\"", "\"contentRole\": \"OTHER\"");
        String invalidDimensions =
                validManifest()
                        .replace("\"embeddingDimensions\": 1536", "\"embeddingDimensions\": 0");
        String invalidReadingSeconds =
                validManifest()
                        .replace(
                                "\"estimatedReadingSeconds\": 60",
                                "\"estimatedReadingSeconds\": 0");

        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(invalidRole));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(invalidDimensions));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(invalidReadingSeconds));
    }

    @Test
    void JSON_필드의_타입_강제_변환과_중복_property를_거부한다() {
        String decimalToInteger =
                validManifest()
                        .replace("\"embeddingDimensions\": 1536", "\"embeddingDimensions\": 1.5");
        String stringToInteger =
                validManifest()
                        .replace(
                                "\"embeddingDimensions\": 1536",
                                "\"embeddingDimensions\": \"1536\"");
        String decimalListItem =
                validManifest()
                        .replace(
                                "\"prerequisitePageNumbers\": []",
                                "\"prerequisitePageNumbers\": [1.5]");
        String stringToBoolean =
                validManifest()
                        .replace(
                                "\"aiRouteCandidate\": true",
                                "\"aiRouteCandidate\": \"true\"");
        String integerToBoolean =
                validManifest()
                        .replace("\"aiRouteCandidate\": true", "\"aiRouteCandidate\": 1");
        String emptyStringToBoolean =
                validManifest()
                        .replace("\"aiRouteCandidate\": true", "\"aiRouteCandidate\": \"\"");
        String numberToString =
                validManifest()
                        .replace(
                                "\"embeddingModel\": \"text-embedding-3-small\"",
                                "\"embeddingModel\": 123");
        String numberToEnum =
                validManifest()
                        .replace("\"contentRole\": \"CORE\"", "\"contentRole\": 1");
        String duplicateProperty =
                validManifest()
                        .replace(
                                "\"embeddingDimensions\": 1536,",
                                "\"embeddingDimensions\": 0,\n"
                                        + "  \"embeddingDimensions\": 1536,");

        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(decimalToInteger));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(stringToInteger));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(decimalListItem));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(stringToBoolean));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(integerToBoolean));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(emptyStringToBoolean));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(numberToString));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(numberToEnum));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseManifest(duplicateProperty));
    }

    @Test
    void 소장_여부와_예산_depth의_배타_입력을_검증한다() {
        String ownedWithBudget =
                validEvaluation()
                        .replace("\"owned\": false", "\"owned\": true")
                        .replace("\"depth\": null", "\"depth\": \"QUICK\"");
        String notOwnedWithDepth =
                validEvaluation().replace("\"depth\": null", "\"depth\": \"BALANCED\"");
        String invalidBudget =
                validEvaluation().replace("\"maxAdditionalInk\": 5", "\"maxAdditionalInk\": 3");
        String nullBudget =
                validEvaluation()
                        .replace("\"maxAdditionalInk\": 5", "\"maxAdditionalInk\": null");
        String ownedWithEmptyBudget =
                validOwnedEvaluation()
                        .replace("\"maxAdditionalInk\": null", "\"maxAdditionalInk\": \"\"");
        String notOwnedWithEmptyDepth =
                validEvaluation().replace("\"depth\": null", "\"depth\": \"\"");

        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(ownedWithBudget));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(notOwnedWithDepth));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(invalidBudget));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(nullBudget));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(ownedWithEmptyBudget));
        assertThrows(
                ContentManifestFormatException.class,
                () -> parser.parseEvaluation(notOwnedWithEmptyDepth));
    }

    private String validManifest() {
        return validManifest(validBook(validPage()));
    }

    private String validManifest(String books) {
        return """
                {
                  "contentVersion": "ai-route-v2",
                  "dataPolicyVersion": "OPENAI_DEFAULT_RETENTION_V1",
                  "embeddingModel": "text-embedding-3-small",
                  "embeddingDimensions": 1536,
                  "books": [%s]
                }
                """.formatted(books);
    }

    private String validBook(String pages) {
        return """
                {
                  "bookId": 1,
                  "pdfPath": "pdfs/book-001.pdf",
                  "pdfSha256": "%s",
                  "totalPageCount": 1,
                  "aiRouteCandidate": true,
                  "aiExternalTransferAllowed": true,
                  "pages": [%s]
                }
                """.formatted(SHA_A, pages);
    }

    private String validNonCandidateBook() {
        return """
                {
                  "bookId": 1,
                  "pdfPath": "pdfs/book-001.pdf",
                  "pdfSha256": "%s",
                  "totalPageCount": 4,
                  "aiRouteCandidate": false,
                  "aiExternalTransferAllowed": false,
                  "pages": []
                }
                """.formatted(SHA_A);
    }

    private String validPage() {
        return """
                {
                  "pageNumber": 1,
                  "chapter": "1장",
                  "section": "1절",
                  "primaryConcepts": ["핵심 개념"],
                  "secondaryConcepts": [],
                  "contentRole": "CORE",
                  "aiAnalysisText": "분석 텍스트",
                  "aiAnalysisInputSha256": "%s",
                  "aiPublicGuideTopic": "공개 주제",
                  "estimatedReadingSeconds": 60,
                  "prerequisitePageNumbers": [],
                  "duplicateGroupKeys": []
                }
                """.formatted(SHA_B);
    }

    private String validEvaluation() {
        return validEvaluation(validEvaluationCase());
    }

    private String validOwnedEvaluation() {
        String ownedCase =
                validEvaluationCase()
                        .replace("\"owned\": false", "\"owned\": true")
                        .replace("\"maxAdditionalInk\": 5", "\"maxAdditionalInk\": null")
                        .replace("\"depth\": null", "\"depth\": \"DEEP\"")
                        .replace(
                                "\"requiredPrerequisites\": []",
                                "\"requiredPrerequisites\": "
                                        + "[{\"beforePageNumber\": 1, \"afterPageNumber\": 2}]")
                        .replace(
                                "\"duplicatePageGroups\": []",
                                "\"duplicatePageGroups\": [[1, 2]]");
        return validEvaluation(ownedCase);
    }

    private String validEvaluation(String cases) {
        return """
                {
                  "contentVersion": "ai-route-v2",
                  "cases": [%s]
                }
                """.formatted(cases);
    }

    private String validEvaluationCase() {
        return """
                {
                  "caseId": "case-001",
                  "bookId": 1,
                  "purpose": "핵심 개념을 이해한다",
                  "owned": false,
                  "maxAdditionalInk": 5,
                  "depth": null,
                  "activeRentalPageNumbers": [],
                  "requiredConcepts": ["핵심 개념"],
                  "helpfulConcepts": [],
                  "requiredPrerequisites": [],
                  "irrelevantPageNumbers": [],
                  "duplicatePageGroups": [],
                  "referencePageNumbers": [1],
                  "allowedAlternativePageNumbers": []
                }
                """;
    }
}
