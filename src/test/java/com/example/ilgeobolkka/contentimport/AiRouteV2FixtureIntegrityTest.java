package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiRouteV2FixtureIntegrityTest {

    private static final Path ROOT = Path.of("fixtures/content/ai-route-v2");
    private static final Path INITIAL_ROOT = Path.of("fixtures/content");
    private static final Path BOOKS_PATH = Path.of("src/main/resources/demo/books.json");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> MANIFEST_FIELDS =
            Set.of(
                    "contentVersion",
                    "dataPolicyVersion",
                    "embeddingModel",
                    "embeddingDimensions",
                    "books");
    private static final Set<String> BOOK_FIELDS =
            Set.of(
                    "bookId",
                    "pdfPath",
                    "pdfSha256",
                    "totalPageCount",
                    "aiRouteCandidate",
                    "aiExternalTransferAllowed",
                    "pages");
    private static final Set<String> PAGE_FIELDS =
            Set.of(
                    "pageNumber",
                    "chapter",
                    "section",
                    "primaryConcepts",
                    "secondaryConcepts",
                    "contentRole",
                    "aiAnalysisText",
                    "aiAnalysisInputSha256",
                    "aiPublicGuideTopic",
                    "estimatedReadingSeconds",
                    "prerequisitePageNumbers",
                    "duplicateGroupKeys");
    private static final Set<String> CONTENT_ROLES =
            Set.of("PREREQUISITE", "CORE", "EXAMPLE", "COUNTERPOINT", "CONCLUSION");
    private static final Set<String> CASE_FIELDS =
            Set.of(
                    "caseId",
                    "bookId",
                    "purpose",
                    "owned",
                    "maxAdditionalInk",
                    "depth",
                    "activeRentalPageNumbers",
                    "requiredConcepts",
                    "helpfulConcepts",
                    "requiredPrerequisites",
                    "irrelevantPageNumbers",
                    "duplicatePageGroups",
                    "referencePageNumbers",
                    "allowedAlternativePageNumbers");
    private static final Set<String> EVALUATION_SCENARIOS =
            Set.of(
                    "UNOWNED:0:ACTIVE",
                    "UNOWNED:5:INACTIVE",
                    "UNOWNED:10:INACTIVE",
                    "UNOWNED:15:INACTIVE",
                    "OWNED:QUICK",
                    "OWNED:BALANCED",
                    "OWNED:DEEP");
    private static final String REPEATED_APPLICATION_SENTENCE =
            "관찰 사실과 해석을 분리하고 시작 조건과 결과를 같은 기준으로 비교한다.";
    private static final String REPEATED_LIMIT_SENTENCE =
            "한 사례를 일반화하지 않고 조건이 달라지면 판단을 다시 검토한다.";

    @Test
    void manifest는_페이지_구조_분석_해시와_DAG_계약을_지킨다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        assertEquals(MANIFEST_FIELDS, Set.copyOf(manifest.propertyNames()));
        assertEquals("ai-route-v2", manifest.get("contentVersion").asText());

        Set<Long> bookIds = new HashSet<>();
        Set<Integer> candidatePageCounts = new HashSet<>();
        Set<String> analysisTexts = new HashSet<>();
        int candidateBooks = 0;
        int candidatePages = 0;
        int preservedNovelBooks = 0;
        int totalPages = 0;

        for (JsonNode book : manifest.get("books")) {
            assertEquals(BOOK_FIELDS, Set.copyOf(book.propertyNames()));
            long bookId = book.get("bookId").asLong();
            int totalPageCount = book.get("totalPageCount").asInt();
            assertTrue(bookIds.add(bookId), "중복 bookId: " + bookId);
            totalPages += totalPageCount;

            if (!book.get("aiRouteCandidate").asBoolean()) {
                preservedNovelBooks++;
                assertFalse(book.get("aiExternalTransferAllowed").asBoolean());
                assertTrue(book.get("pages").isEmpty());
                continue;
            }

            candidateBooks++;
            candidatePages += totalPageCount;
            candidatePageCounts.add(totalPageCount);
            assertTrue(book.get("aiExternalTransferAllowed").asBoolean());
            assertTrue(totalPageCount >= 48 && totalPageCount <= 72);
            assertEquals(totalPageCount, book.get("pages").size());

            Map<Integer, JsonNode> pages = pagesByNumber(book);
            Set<String> chapters = new HashSet<>();
            Set<String> contentRoles = new HashSet<>();
            Map<Integer, List<Integer>> outgoingEdges = new HashMap<>();
            Map<Integer, Integer> indegrees = new HashMap<>();
            Map<String, Set<Integer>> duplicateGroups = new HashMap<>();
            for (int pageNumber = 1; pageNumber <= totalPageCount; pageNumber++) {
                JsonNode page = pages.get(pageNumber);
                assertNotNull(page, "누락 페이지: book=" + bookId + ", page=" + pageNumber);
                assertEquals(PAGE_FIELDS, Set.copyOf(page.propertyNames()));
                assertFalse(page.get("chapter").asText().isBlank());
                assertFalse(page.get("section").asText().isBlank());
                assertFalse(page.get("primaryConcepts").isEmpty());
                assertTrue(CONTENT_ROLES.contains(page.get("contentRole").asText()));
                contentRoles.add(page.get("contentRole").asText());
                assertTrue(page.get("estimatedReadingSeconds").asInt() > 0);

                String chapter = page.get("chapter").asText();
                if (chapter.matches("[0-9]+장 .+")) {
                    chapters.add(chapter);
                }

                String analysisText = page.get("aiAnalysisText").asText();
                assertFalse(analysisText.isBlank());
                assertTrue(analysisTexts.add(analysisText), "중복 분석 텍스트: " + analysisText);
                assertEquals(
                        ContentBatchConverter.sha256(analysisText.getBytes()),
                        page.get("aiAnalysisInputSha256").asText());
                assertFalse(analysisText.contains("관계을"));
                assertFalse(analysisText.contains("한계을"));
                assertFalse(analysisText.contains(REPEATED_APPLICATION_SENTENCE));
                assertFalse(analysisText.contains(REPEATED_LIMIT_SENTENCE));

                indegrees.put(pageNumber, page.get("prerequisitePageNumbers").size());
                for (JsonNode prerequisite : page.get("prerequisitePageNumbers")) {
                    int prerequisitePageNumber = prerequisite.asInt();
                    assertTrue(pages.containsKey(prerequisitePageNumber));
                    assertTrue(prerequisitePageNumber != pageNumber);
                    outgoingEdges
                            .computeIfAbsent(prerequisitePageNumber, ignored -> new ArrayList<>())
                            .add(pageNumber);
                }
                for (JsonNode duplicateGroupKey : page.get("duplicateGroupKeys")) {
                    duplicateGroups
                            .computeIfAbsent(
                                    duplicateGroupKey.asText(), ignored -> new HashSet<>())
                            .add(pageNumber);
                }
            }

            assertTrue(chapters.size() >= 6, "장 수 부족: book=" + bookId);
            assertEquals(CONTENT_ROLES, contentRoles, "필수 페이지 역할 누락: book=" + bookId);
            assertAcyclic(bookId, pages.keySet(), outgoingEdges, indegrees);
            for (Map.Entry<String, Set<Integer>> duplicateGroup : duplicateGroups.entrySet()) {
                assertTrue(
                        duplicateGroup.getValue().size() >= 2,
                        "단일 페이지 중복 그룹: " + duplicateGroup.getKey());
            }
        }

        assertEquals(100, bookIds.size());
        assertEquals(90, candidateBooks);
        assertEquals(10, preservedNovelBooks);
        assertEquals(5445, candidatePages);
        assertEquals(5485, totalPages);
        assertEquals(25, candidatePageCounts.size());
    }

    @Test
    void 평가_90건은_manifest_개념_페이지_선수_중복_그룹과_연결된다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        JsonNode evaluation = read(ROOT.resolve("evaluation.json"));
        JsonNode verificationSummary = read(ROOT.resolve("verification-summary.json"));
        Map<Long, JsonNode> candidateBooks = candidateBooksById(manifest);
        Set<Long> caseBookIds = new HashSet<>();
        Set<String> caseIds = new HashSet<>();
        Set<String> scenarios = new HashSet<>();

        assertEquals("ai-route-v2", evaluation.get("contentVersion").asText());
        assertEquals(90, evaluation.get("cases").size());
        for (JsonNode evaluationCase : evaluation.get("cases")) {
            assertEquals(CASE_FIELDS, Set.copyOf(evaluationCase.propertyNames()));
            String caseId = evaluationCase.get("caseId").asText();
            long bookId = evaluationCase.get("bookId").asLong();
            assertTrue(caseIds.add(caseId), "중복 caseId: " + caseId);
            assertTrue(caseBookIds.add(bookId), "도서별 평가가 1건이 아님: " + bookId);
            JsonNode book = candidateBooks.get(bookId);
            assertNotNull(book, "후보가 아닌 도서의 평가: " + bookId);
            Map<Integer, JsonNode> pages = pagesByNumber(book);

            Set<String> primaryConcepts = new HashSet<>();
            Map<String, Set<Integer>> duplicateGroups = new HashMap<>();
            for (JsonNode page : book.get("pages")) {
                primaryConcepts.addAll(textSet(page.get("primaryConcepts")));
                for (JsonNode key : page.get("duplicateGroupKeys")) {
                    duplicateGroups
                            .computeIfAbsent(key.asText(), ignored -> new HashSet<>())
                            .add(page.get("pageNumber").asInt());
                }
            }
            assertTrue(primaryConcepts.containsAll(textSet(evaluationCase.get("requiredConcepts"))));
            assertTrue(primaryConcepts.containsAll(textSet(evaluationCase.get("helpfulConcepts"))));

            assertExistingPages(evaluationCase.get("activeRentalPageNumbers"), pages.keySet());
            assertExistingPages(evaluationCase.get("irrelevantPageNumbers"), pages.keySet());
            assertExistingPages(evaluationCase.get("referencePageNumbers"), pages.keySet());
            assertExistingPages(evaluationCase.get("allowedAlternativePageNumbers"), pages.keySet());

            for (JsonNode prerequisite : evaluationCase.get("requiredPrerequisites")) {
                int before = prerequisite.get("beforePageNumber").asInt();
                int after = prerequisite.get("afterPageNumber").asInt();
                assertTrue(pages.containsKey(before));
                assertTrue(pages.containsKey(after));
                assertTrue(intSet(pages.get(after).get("prerequisitePageNumbers")).contains(before));
            }
            for (JsonNode duplicatePageGroup : evaluationCase.get("duplicatePageGroups")) {
                Set<Integer> expectedPages = intSet(duplicatePageGroup);
                assertExistingPages(duplicatePageGroup, pages.keySet());
                assertTrue(
                        duplicateGroups.containsValue(expectedPages),
                        "manifest에 없는 중복 그룹: book=" + bookId + ", pages=" + expectedPages);
            }

            scenarios.add(scenarioOf(evaluationCase));
        }

        assertEquals(candidateBooks.keySet(), caseBookIds);
        assertEquals(EVALUATION_SCENARIOS, scenarios);
        assertEquals(
                ContentBatchConverter.sha256(Files.readAllBytes(ROOT.resolve("evaluation.json"))),
                verificationSummary.get("evaluationSha256").asText());
    }

    @Test
    void AI_생성_사진은_도서마다_한_장이고_도표는_없으며_검수를_마쳤다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        JsonNode evidence = read(ROOT.resolve("review-evidence.json"));
        JsonNode qualitySamples = read(ROOT.resolve("quality-samples.json"));
        JsonNode verificationSummary = read(ROOT.resolve("verification-summary.json"));
        Map<Long, JsonNode> candidateBooks = candidateBooksById(manifest);
        Set<String> imagePageKeys = new HashSet<>();
        Map<Long, Integer> imageCounts = new HashMap<>();

        assertEquals(
                "HUMAN_REVIEW_COMPLETED",
                evidence.get("humanReviewGate").get("status").asText());
        assertEquals("HUMAN_REVIEW_COMPLETED", qualitySamples.get("humanReviewStatus").asText());
        assertEquals(
                "PASS",
                qualitySamples.get("checks").get("sampledNearDuplicateAppearance").asText());
        assertEquals("HUMAN_REVIEW_COMPLETED", verificationSummary.get("humanReviewStatus").asText());
        for (JsonNode file : evidence.get("files")) {
            if (candidateBooks.containsKey(file.get("bookId").asLong())) {
                assertEquals("HUMAN_FILE_REVIEW_COMPLETED", file.get("humanReviewStatus").asText());
            }
        }

        for (JsonNode book : candidateBooks.values()) {
            for (JsonNode page : book.get("pages")) {
                assertFalse(page.get("section").asText().contains("네 가지 확인"));
                assertFalse(page.get("section").asText().contains("도표"));
                assertFalse(page.get("aiAnalysisText").asText().contains("순환 도표"));
                assertFalse(page.get("aiPublicGuideTopic").asText().contains("도표"));
            }
        }

        assertEquals(90, evidence.get("imagePages").size());
        for (JsonNode imagePage : evidence.get("imagePages")) {
            long bookId = imagePage.get("bookId").asLong();
            int pageNumber = imagePage.get("pageNumber").asInt();
            String imagePageKey = bookId + ":" + pageNumber;
            assertTrue(imagePageKeys.add(imagePageKey), "중복 IMAGE 근거: " + imagePageKey);
            imageCounts.merge(bookId, 1, Integer::sum);

            JsonNode page = pagesByNumber(candidateBooks.get(bookId)).get(pageNumber);
            assertNotNull(page, "manifest에 없는 IMAGE 근거: " + imagePageKey);
            assertEquals("EXAMPLE", page.get("contentRole").asText());
            assertTrue(page.get("section").asText().endsWith("관찰 사진"));
            assertTrue(page.get("primaryConcepts").get(0).asText().endsWith("의 시각 사례"));
            assertTrue(page.get("aiAnalysisText").asText().contains("AI 생성 사진"));
            assertEquals(
                    page.get("aiAnalysisInputSha256").asText(),
                    imagePage.get("aiAnalysisInputSha256").asText());
            assertEquals(
                    "Codex 내장 imagegen 도구로 생성한 원본 사진",
                    imagePage.get("sourceMethod").asText());
            assertEquals("HUMAN_REVIEW_COMPLETED", imagePage.get("humanReviewStatus").asText());

            Path imagePath = ROOT.resolve(imagePage.get("imageAssetPath").asText());
            assertTrue(Files.isRegularFile(imagePath), "이미지 파일 누락: " + imagePath);
            assertEquals(
                    ContentBatchConverter.sha256(Files.readAllBytes(imagePath)),
                    imagePage.get("imageAssetSha256").asText());
        }
        assertEquals(candidateBooks.keySet(), imageCounts.keySet());
        assertTrue(imageCounts.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void 소설_10권과_권리_검수_근거는_원본과_manifest에_연결된다() throws IOException {
        JsonNode initialManifest = read(INITIAL_ROOT.resolve("manifest.json"));
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        JsonNode sourceLedger = read(ROOT.resolve("source-ledger.json"));
        JsonNode reviewEvidence = read(ROOT.resolve("review-evidence.json"));
        JsonNode verificationSummary = read(ROOT.resolve("verification-summary.json"));
        Map<Long, JsonNode> initialBooks = booksById(initialManifest.get("books"));
        Map<Long, JsonNode> aiRouteBooks = booksById(manifest.get("books"));
        Map<Long, JsonNode> catalogBooks = catalogBooksById(read(BOOKS_PATH));

        for (long bookId = 1; bookId <= 10; bookId++) {
            JsonNode initialBook = initialBooks.get(bookId);
            JsonNode aiRouteBook = aiRouteBooks.get(bookId);
            assertFalse(aiRouteBook.get("aiRouteCandidate").asBoolean());
            assertEquals(initialBook.get("pdfSha256"), aiRouteBook.get("pdfSha256"));
            assertEquals(initialBook.get("totalPageCount"), aiRouteBook.get("totalPageCount"));
            assertArrayEquals(
                    Files.readAllBytes(INITIAL_ROOT.resolve(initialBook.get("pdfPath").asText())),
                    Files.readAllBytes(ROOT.resolve(aiRouteBook.get("pdfPath").asText())));
        }

        assertEquals(90, sourceLedger.get("books").size());
        Set<Long> sourceBookIds = new HashSet<>();
        Set<Integer> gutenbergIds = new HashSet<>();
        for (JsonNode sourceBook : sourceLedger.get("books")) {
            long bookId = sourceBook.get("bookId").asLong();
            assertTrue(sourceBookIds.add(bookId));
            JsonNode catalogBook = catalogBooks.get(bookId);
            JsonNode fixtureMetadata = sourceBook.get("fixtureMetadata");
            assertEquals(catalogBook.get("title"), fixtureMetadata.get("title"));
            assertEquals(catalogBook.get("author"), fixtureMetadata.get("author"));
            assertEquals(catalogBook.get("category"), fixtureMetadata.get("category"));
            assertEquals(catalogBook.get("description"), fixtureMetadata.get("description"));
            assertTrue(gutenbergIds.add(sourceBook.get("reference").get("gutenbergId").asInt()));
        }
        assertEquals(candidateBooksById(manifest).keySet(), sourceBookIds);

        assertEquals(
                ContentBatchConverter.sha256(Files.readAllBytes(BOOKS_PATH)),
                reviewEvidence.get("sourceMetadataSha256").asText());
        String sourceLedgerSha =
                ContentBatchConverter.sha256(Files.readAllBytes(ROOT.resolve("source-ledger.json")));
        assertEquals(sourceLedgerSha, reviewEvidence.get("sourceLedgerSha256").asText());
        assertEquals(sourceLedgerSha, verificationSummary.get("sourceLedgerSha256").asText());

        Set<Long> reviewedBookIds = new HashSet<>();
        for (JsonNode reviewedFile : reviewEvidence.get("files")) {
            long bookId = reviewedFile.get("bookId").asLong();
            assertTrue(reviewedBookIds.add(bookId));
            JsonNode manifestBook = aiRouteBooks.get(bookId);
            assertEquals(manifestBook.get("pdfPath"), reviewedFile.get("pdfPath"));
            assertEquals(manifestBook.get("pdfSha256"), reviewedFile.get("pdfSha256"));
        }
        assertEquals(aiRouteBooks.keySet(), reviewedBookIds);
    }

    private static JsonNode read(Path path) throws IOException {
        return OBJECT_MAPPER.readTree(path.toFile());
    }

    private static Map<Long, JsonNode> booksById(JsonNode books) {
        Map<Long, JsonNode> result = new HashMap<>();
        for (JsonNode book : books) {
            result.put(book.get("bookId").asLong(), book);
        }
        return result;
    }

    private static Map<Long, JsonNode> catalogBooksById(JsonNode books) {
        Map<Long, JsonNode> result = new HashMap<>();
        for (JsonNode book : books) {
            result.put(book.get("id").asLong(), book);
        }
        return result;
    }

    private static Map<Long, JsonNode> candidateBooksById(JsonNode manifest) {
        Map<Long, JsonNode> result = new HashMap<>();
        for (JsonNode book : manifest.get("books")) {
            if (book.get("aiRouteCandidate").asBoolean()) {
                result.put(book.get("bookId").asLong(), book);
            }
        }
        return result;
    }

    private static Map<Integer, JsonNode> pagesByNumber(JsonNode book) {
        Map<Integer, JsonNode> result = new HashMap<>();
        for (JsonNode page : book.get("pages")) {
            JsonNode previous = result.put(page.get("pageNumber").asInt(), page);
            assertTrue(previous == null, "중복 페이지 번호: " + page.get("pageNumber").asInt());
        }
        return result;
    }

    private static Set<String> textSet(JsonNode array) {
        Set<String> result = new HashSet<>();
        for (JsonNode item : array) {
            result.add(item.asText());
        }
        return result;
    }

    private static Set<Integer> intSet(JsonNode array) {
        Set<Integer> result = new HashSet<>();
        for (JsonNode item : array) {
            result.add(item.asInt());
        }
        return result;
    }

    private static void assertExistingPages(JsonNode pageNumbers, Set<Integer> existingPages) {
        assertTrue(existingPages.containsAll(intSet(pageNumbers)));
    }

    private static void assertAcyclic(
            long bookId,
            Set<Integer> pageNumbers,
            Map<Integer, List<Integer>> outgoingEdges,
            Map<Integer, Integer> indegrees) {
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int pageNumber : pageNumbers) {
            if (indegrees.get(pageNumber) == 0) {
                ready.add(pageNumber);
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            int current = ready.removeFirst();
            visited++;
            for (int dependent : outgoingEdges.getOrDefault(current, List.of())) {
                int remaining = indegrees.compute(dependent, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        assertEquals(pageNumbers.size(), visited, "선수 관계 순환: book=" + bookId);
    }

    private static String scenarioOf(JsonNode evaluationCase) {
        if (evaluationCase.get("owned").asBoolean()) {
            return "OWNED:" + evaluationCase.get("depth").asText();
        }
        String rentalStatus =
                evaluationCase.get("activeRentalPageNumbers").isEmpty() ? "INACTIVE" : "ACTIVE";
        return "UNOWNED:"
                + evaluationCase.get("maxAdditionalInk").asInt()
                + ":"
                + rentalStatus;
    }

}
