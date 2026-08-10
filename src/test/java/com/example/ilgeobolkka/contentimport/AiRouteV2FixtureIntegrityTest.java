package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
                    "aiRouteSearchEligible",
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
    private static final Set<String> EVALUATION_STOP_WORDS =
            Set.of(
                    "경우",
                    "과정",
                    "관련",
                    "기준",
                    "방법",
                    "변화",
                    "사례",
                    "상황",
                    "순서",
                    "원리",
                    "조건",
                    "핵심",
                    "확인");
    private static final List<String> KOREAN_PARTICLES =
            List.of(
                    "에서",
                    "으로",
                    "에게",
                    "부터",
                    "까지",
                    "처럼",
                    "보다",
                    "과",
                    "와",
                    "이",
                    "가",
                    "은",
                    "는",
                    "을",
                    "를",
                    "의",
                    "에",
                    "로");

    @Test
    void manifest는_페이지_구조_분석_해시와_DAG_계약을_지킨다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        assertEquals(MANIFEST_FIELDS, Set.copyOf(manifest.propertyNames()));
        assertEquals("ai-route-v2", manifest.get("contentVersion").asText());

        Set<Long> bookIds = new HashSet<>();
        Set<Integer> candidatePageCounts = new HashSet<>();
        List<String> searchAnalysisTexts = new ArrayList<>();
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
                boolean searchEligible = page.get("aiRouteSearchEligible").asBoolean();
                assertEquals(
                        chapter.matches("[0-9]+장 .+"),
                        searchEligible,
                        "본문이 아닌 검색 후보 또는 본문 검색 누락: book="
                                + bookId
                                + ", page="
                                + pageNumber);
                if (chapter.matches("[0-9]+장 .+")) {
                    chapters.add(chapter);
                }

                String analysisText = page.get("aiAnalysisText").asText();
                assertFalse(analysisText.isBlank());
                if (searchEligible) {
                    searchAnalysisTexts.add(analysisText);
                }
                assertEquals(
                        ContentBatchConverter.sha256(analysisText.getBytes()),
                        page.get("aiAnalysisInputSha256").asText());
                assertFalse(analysisText.contains("관계을"));
                assertFalse(analysisText.contains("한계을"));

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
        assertAnalysisDiversity(searchAnalysisTexts);
    }

    @Test
    void 평가_90건은_manifest_개념_페이지_선수_중복_그룹과_연결된다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        JsonNode evaluation = read(ROOT.resolve("evaluation.json"));
        JsonNode verificationSummary = read(ROOT.resolve("verification-summary.json"));
        Map<Long, JsonNode> candidateBooks = candidateBooksById(manifest);
        Set<Long> caseBookIds = new HashSet<>();
        Set<String> caseIds = new HashSet<>();
        Set<String> purposes = new HashSet<>();
        Set<String> scenarios = new HashSet<>();
        Set<List<Integer>> referencePageSets = new HashSet<>();
        Set<Integer> requiredConceptPageNumbers = new HashSet<>();
        Map<Long, JsonNode> catalogBooks = catalogBooksById(read(BOOKS_PATH));

        assertEquals("ai-route-v2", evaluation.get("contentVersion").asText());
        assertEquals(90, evaluation.get("cases").size());
        for (JsonNode evaluationCase : evaluation.get("cases")) {
            assertEquals(CASE_FIELDS, Set.copyOf(evaluationCase.propertyNames()));
            String caseId = evaluationCase.get("caseId").asText();
            long bookId = evaluationCase.get("bookId").asLong();
            String purpose = evaluationCase.get("purpose").asText();
            assertFalse(purpose.contains(catalogBooks.get(bookId).get("title").asText()));
            assertTrue(purposes.add(purpose), "중복 평가 목적: " + purpose);
            assertTrue(caseIds.add(caseId), "중복 caseId: " + caseId);
            assertTrue(caseBookIds.add(bookId), "도서별 평가가 1건이 아님: " + bookId);
            JsonNode book = candidateBooks.get(bookId);
            assertNotNull(book, "후보가 아닌 도서의 평가: " + bookId);
            Map<Integer, JsonNode> pages = pagesByNumber(book);

            Set<String> primaryConcepts = new HashSet<>();
            Map<String, Set<Integer>> duplicateGroups = new HashMap<>();
            for (JsonNode page : book.get("pages")) {
                primaryConcepts.addAll(textSet(page.get("primaryConcepts")));
                assertFalse(
                        page.get("aiAnalysisText").asText().contains(purpose),
                        "평가 목적이 분석 텍스트에 축자 포함됨: " + caseId);
                for (JsonNode key : page.get("duplicateGroupKeys")) {
                    duplicateGroups
                            .computeIfAbsent(key.asText(), ignored -> new HashSet<>())
                            .add(page.get("pageNumber").asInt());
                }
            }
            Set<String> requiredConcepts = textSet(evaluationCase.get("requiredConcepts"));
            assertTrue(primaryConcepts.containsAll(requiredConcepts));
            assertTrue(primaryConcepts.containsAll(textSet(evaluationCase.get("helpfulConcepts"))));
            for (String requiredConcept : requiredConcepts) {
                Set<String> conceptTokens = meaningfulTokens(requiredConcept);
                assertFalse(purpose.contains(requiredConcept));
                assertTrue(
                        Collections.disjoint(meaningfulTokens(purpose), conceptTokens),
                        "목적과 정답 개념의 어휘 누출: " + caseId + " / " + requiredConcept);
                assertTrue(
                        conceptTokens.stream().noneMatch(purpose::contains),
                        "목적과 정답 개념의 부분 어휘 누출: " + caseId + " / " + requiredConcept);
                for (JsonNode page : book.get("pages")) {
                    assertFalse(
                            page.get("aiAnalysisText").asText().contains(requiredConcept),
                            "정답 개념이 분석 텍스트에 축자 포함됨: "
                                    + caseId
                                    + " / "
                                    + requiredConcept);
                    if (textSet(page.get("primaryConcepts")).contains(requiredConcept)) {
                        requiredConceptPageNumbers.add(page.get("pageNumber").asInt());
                    }
                }
            }

            assertExistingPages(evaluationCase.get("activeRentalPageNumbers"), pages.keySet());
            assertExistingPages(evaluationCase.get("irrelevantPageNumbers"), pages.keySet());
            assertExistingPages(evaluationCase.get("referencePageNumbers"), pages.keySet());
            assertExistingPages(evaluationCase.get("allowedAlternativePageNumbers"), pages.keySet());

            Set<Integer> irrelevantPageNumbers = intSet(evaluationCase.get("irrelevantPageNumbers"));
            Set<String> referenceRoles = new HashSet<>();
            List<Integer> referencePageNumbers = new ArrayList<>();
            for (JsonNode page : book.get("pages")) {
                int pageNumber = page.get("pageNumber").asInt();
                if (!page.get("aiRouteSearchEligible").asBoolean()) {
                    assertTrue(
                            irrelevantPageNumbers.contains(pageNumber),
                            "검색 제외 페이지가 무관 정답에 없음: book="
                                    + bookId
                                    + ", page="
                                    + pageNumber);
                }
            }
            for (JsonNode referencePageNumber : evaluationCase.get("referencePageNumbers")) {
                int pageNumber = referencePageNumber.asInt();
                referencePageNumbers.add(pageNumber);
                JsonNode page = pages.get(pageNumber);
                assertTrue(page.get("aiRouteSearchEligible").asBoolean());
                referenceRoles.add(page.get("contentRole").asText());
            }
            assertEquals(CONTENT_ROLES, referenceRoles, "평가 역할 누락: " + caseId);
            assertTrue(
                    referencePageSets.add(List.copyOf(referencePageNumbers)),
                    "중복 평가 위치: " + referencePageNumbers);
            for (int index = 1; index < referencePageNumbers.size(); index++) {
                assertTrue(
                        referencePageNumbers.get(index) - referencePageNumbers.get(index - 1) > 1,
                        "연속된 평가 정답 페이지: " + caseId + " / " + referencePageNumbers);
            }

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
        assertEquals(90, referencePageSets.size());
        assertTrue(requiredConceptPageNumbers.size() >= 30);
        assertEquals(
                ContentBatchConverter.sha256(Files.readAllBytes(ROOT.resolve("evaluation.json"))),
                verificationSummary.get("evaluationSha256").asText());
    }

    @Test
    void AI_생성_사진과_자동_검증은_사람_검수_완료로_과장하지_않는다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        JsonNode evidence = read(ROOT.resolve("review-evidence.json"));
        JsonNode qualitySamples = read(ROOT.resolve("quality-samples.json"));
        JsonNode verificationSummary = read(ROOT.resolve("verification-summary.json"));
        Map<Long, JsonNode> candidateBooks = candidateBooksById(manifest);
        Set<String> imagePageKeys = new HashSet<>();
        Map<Long, Integer> imageCounts = new HashMap<>();

        assertEquals(
                "PENDING_HUMAN_REVIEW",
                evidence.get("humanReviewGate").get("status").asText());
        assertEquals(
                "PASS",
                evidence.get("automatedChecks").get("bodyVerticalCentering").asText());
        assertEquals(
                "PASS",
                evidence.get("automatedChecks").get("bodySafeAreaMargins").asText());
        assertEquals(
                "PASS",
                evidence
                        .get("automatedChecks")
                        .get("relatedContextKoreanParticles")
                        .asText());
        assertEquals(
                "PASS",
                evidence
                        .get("automatedChecks")
                        .get("endOfChapterReflectionPromptAbsence")
                        .asText());
        assertEquals(
                "PASS",
                evidence
                        .get("automatedChecks")
                        .get("endOfChapterPromptBoxGraphicAbsence")
                        .asText());
        assertEquals(
                "PASS",
                evidence.get("automatedChecks").get("analysisTemplateDiversity").asText());
        assertEquals(
                "PASS",
                evidence
                        .get("automatedChecks")
                        .get("evaluationLexicalLeakageAbsence")
                        .asText());
        assertEquals(
                "PASS",
                evidence
                        .get("automatedChecks")
                        .get("searchIneligibleFrontAndBackMatter")
                        .asText());
        assertEquals(
                "PASS",
                evidence.get("automatedChecks").get("counterpointRoleCoverage").asText());
        assertEquals("PENDING_HUMAN_REVIEW", qualitySamples.get("humanReviewStatus").asText());
        assertEquals(
                "PASS",
                qualitySamples.get("checks").get("sampledNearDuplicateAppearance").asText());
        assertEquals(
                "PASS",
                qualitySamples
                        .get("checks")
                        .get("endOfChapterReflectionPromptAbsence")
                        .asText());
        assertEquals(
                "PASS",
                qualitySamples
                        .get("checks")
                        .get("endOfChapterPromptBoxGraphicAbsence")
                        .asText());
        assertEquals(
                "PASS",
                qualitySamples
                        .get("checks")
                        .get("relatedContextKoreanParticles")
                        .asText());
        assertTrue(
                textSet(verificationSummary.get("automatedChecks"))
                        .contains("RELATED_CONTEXT_KOREAN_PARTICLES"));
        assertEquals("PENDING_HUMAN_REVIEW", verificationSummary.get("humanReviewStatus").asText());
        for (JsonNode file : evidence.get("files")) {
            if (candidateBooks.containsKey(file.get("bookId").asLong())) {
                assertEquals("PENDING_HUMAN_REVIEW", file.get("humanReviewStatus").asText());
            }
        }

        for (JsonNode book : candidateBooks.values()) {
            for (JsonNode page : book.get("pages")) {
                assertFalse(page.get("section").asText().contains("네 가지 확인"));
                assertFalse(page.get("section").asText().contains("반대 관점과 한계"));
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
            assertEquals("PENDING_HUMAN_REVIEW", imagePage.get("humanReviewStatus").asText());

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
    void 렌더링_표본은_10개_카테고리와_PDF_크기에_연결된다() throws IOException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        JsonNode qualitySamples = read(ROOT.resolve("quality-samples.json"));
        Map<Long, JsonNode> manifestBooks = booksById(manifest.get("books"));
        Map<Long, JsonNode> catalogBooks = catalogBooksById(read(BOOKS_PATH));
        Set<Long> sampledBookIds = new HashSet<>();
        Set<String> sampledCategories = new HashSet<>();

        assertEquals(10, qualitySamples.get("sampledBooks").size());
        for (JsonNode sampledBook : qualitySamples.get("sampledBooks")) {
            long bookId = sampledBook.get("bookId").asLong();
            JsonNode manifestBook = manifestBooks.get(bookId);
            JsonNode catalogBook = catalogBooks.get(bookId);
            Set<Integer> pageNumbers = intSet(sampledBook.get("pageNumbers"));

            assertTrue(sampledBookIds.add(bookId), "중복 렌더링 표본: " + bookId);
            assertNotNull(manifestBook, "manifest에 없는 렌더링 표본: " + bookId);
            assertNotNull(catalogBook, "catalog에 없는 렌더링 표본: " + bookId);
            assertEquals(catalogBook.get("category").asText(), sampledBook.get("category").asText());
            assertTrue(sampledCategories.add(sampledBook.get("category").asText()));

            Path pdfPath = ROOT.resolve(manifestBook.get("pdfPath").asText());
            assertEquals(Files.size(pdfPath), sampledBook.get("pdfSizeBytes").asLong());
            assertTrue(pageNumbers.contains(1), "첫 페이지 표본 누락: " + bookId);
            assertTrue(
                    pageNumbers.contains(manifestBook.get("totalPageCount").asInt()),
                    "마지막 페이지 표본 누락: " + bookId);
            assertTrue(
                    pageNumbers.stream()
                            .allMatch(
                                    pageNumber ->
                                            pageNumber >= 1
                                                    && pageNumber
                                                            <= manifestBook
                                                                    .get("totalPageCount")
                                                                    .asInt()),
                    "범위 밖 렌더링 표본: " + bookId);

            if ("소설".equals(sampledBook.get("category").asText())) {
                assertFalse(manifestBook.get("aiRouteCandidate").asBoolean());
                assertEquals(Set.of(1, 2, 3, 4), pageNumbers);
            }
        }

        assertEquals(
                Set.of("소설", "에세이", "과학", "역사", "경제", "철학", "예술", "기술", "여행", "자기계발"),
                sampledCategories);
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_AI_ROUTE_V2_PDF_INTEGRITY",
            matches = "true")
    void 비소설_90권의_실제_PDF는_구조와_관련_맥락_조사를_지킨다()
            throws IOException, InterruptedException {
        JsonNode manifest = read(ROOT.resolve("manifest.json"));
        String pdftotextCommand =
                System.getenv().getOrDefault("PDFTOTEXT_COMMAND", "pdftotext");

        for (JsonNode book : manifest.get("books")) {
            if (!book.get("aiRouteCandidate").asBoolean()) {
                continue;
            }
            Path pdfPath = ROOT.resolve(book.get("pdfPath").asText());
            String visibleText = extractAllText(pdftotextCommand, pdfPath);
            List<String> visiblePages = List.of(visibleText.split("\f", -1));

            assertTrue(visibleText.contains("이 장을 여는 문장"), pdfPath.toString());
            assertFalse(visibleText.contains("생각을 이어 가는 문장"), pdfPath.toString());
            assertFalse(visibleText.contains("생각할 질문:"), pdfPath.toString());
            assertFalse(visibleText.contains("남기는 질문"), pdfPath.toString());
            assertFalse(visibleText.contains("반대 관점과 한계"), pdfPath.toString());
            assertTrue(visibleText.contains("관련 맥락"), pdfPath.toString());

            for (JsonNode page : book.get("pages")) {
                if (!page.get("section").asText().endsWith("관련 맥락")) {
                    continue;
                }
                int pageNumber = page.get("pageNumber").asInt();
                String pageText = normalizeWhitespace(visiblePages.get(pageNumber - 1));
                String chapter = page.get("chapter").asText();
                String topic = page.get("secondaryConcepts").get(0).asText();
                String companion = page.get("secondaryConcepts").get(1).asText();
                String first = page.get("secondaryConcepts").get(2).asText();
                String second = page.get("secondaryConcepts").get(3).asText();
                String location = pdfPath + ":" + pageNumber;

                assertTrue(
                        pageText.contains(
                                withJosa(topic, "은", "는")
                                        + " "
                                        + chapter
                                        + "의 내용을 이어 주는 중심 용어입니다."),
                        location);
                assertTrue(
                        pageText.contains(
                                withJosa(topic, "과", "와")
                                        + " "
                                        + withJosa(companion, "은", "는")
                                        + " 서로 떨어진 항목이 아니라"),
                        location);
                assertTrue(
                        pageText.contains(
                                withJosa(first, "과", "와")
                                        + " "
                                        + withJosa(second, "은", "는")
                                        + " 두 개념이"),
                        location);
                assertTrue(
                        pageText.contains(
                                companion
                                        + withDirectionJosa(companion)
                                        + " 범위를 넓히고"),
                        location);
                assertTrue(
                        pageText.contains(
                                withJosa(first, "과", "와") + " " + second + "의 관계를"),
                        location);
                assertTrue(
                        pageText.contains(withJosa(topic, "이", "가") + " 단독 설명이 아니라"),
                        location);
                assertTrue(
                        pageText.contains(
                                companion
                                        + ", "
                                        + first
                                        + ", "
                                        + withJosa(second, "과", "와")
                                        + " 함께 전개되는"),
                        location);
            }
        }
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

    private static String extractAllText(String command, Path pdfPath)
            throws IOException, InterruptedException {
        Process process =
                new ProcessBuilder(command, "-enc", "UTF-8", pdfPath.toString(), "-")
                        .redirectErrorStream(true)
                        .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "pdftotext 실패: " + pdfPath + "\n" + output);
        return output;
    }

    private static String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String withJosa(String word, String withFinal, String withoutFinal) {
        return word + (hasFinalConsonant(word) ? withFinal : withoutFinal);
    }

    private static String withDirectionJosa(String word) {
        int finalConsonant = finalConsonantIndex(word);
        return finalConsonant == 0 || finalConsonant == 8 ? "로" : "으로";
    }

    private static boolean hasFinalConsonant(String word) {
        return finalConsonantIndex(word) != 0;
    }

    private static int finalConsonantIndex(String word) {
        int lastCharacter = word.codePointBefore(word.length());
        if (lastCharacter < '가' || lastCharacter > '힣') {
            throw new IllegalArgumentException("한글 음절로 끝나지 않는 개념: " + word);
        }
        return (lastCharacter - '가') % 28;
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

    private static Set<String> meaningfulTokens(String text) {
        Set<String> result = new HashSet<>();
        for (String rawToken : text.split("[^0-9A-Za-z가-힣]+")) {
            String token = stripKoreanParticle(rawToken);
            if (token.length() >= 2 && !EVALUATION_STOP_WORDS.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private static String stripKoreanParticle(String token) {
        for (String particle : KOREAN_PARTICLES) {
            if (token.length() > particle.length() + 1 && token.endsWith(particle)) {
                return token.substring(0, token.length() - particle.length());
            }
        }
        return token;
    }

    private static void assertAnalysisDiversity(List<String> analysisTexts) {
        Map<String, Integer> sentenceFrequencies = new HashMap<>();
        Map<String, Integer> shingleFrequencies = new HashMap<>();
        List<Set<String>> pageShingles = new ArrayList<>();

        for (String analysisText : analysisTexts) {
            assertFalse(
                    analysisText.contains("핵심 논지:")
                            && analysisText.contains("구조:")
                            && analysisText.contains("선수 개념:")
                            && analysisText.contains("연결 개념:")
                            && analysisText.contains("적용 조건:")
                            && analysisText.contains("한계:"),
                    "고정 6슬롯 분석 템플릿: " + analysisText);

            for (String sentence : analysisText.split("(?<=[.!?])\\s+")) {
                String normalized = sentence.replaceAll("\\s+", " ").trim();
                if (normalized.length() >= 30) {
                    sentenceFrequencies.merge(normalized, 1, Integer::sum);
                }
            }

            List<String> tokens = List.of(analysisText.split("[^0-9A-Za-z가-힣]+"));
            Set<String> shingles = new HashSet<>();
            for (int index = 0; index + 4 < tokens.size(); index++) {
                shingles.add(String.join(" ", tokens.subList(index, index + 5)));
            }
            pageShingles.add(shingles);
            for (String shingle : shingles) {
                shingleFrequencies.merge(shingle, 1, Integer::sum);
            }
        }

        int maximumSentenceFrequency =
                sentenceFrequencies.values().stream().max(Integer::compareTo).orElse(0);
        assertTrue(maximumSentenceFrequency <= 5, "반복 분석 문장 빈도: " + maximumSentenceFrequency);

        List<Double> repeatedShingleRatios = new ArrayList<>();
        for (Set<String> shingles : pageShingles) {
            long repeated =
                    shingles.stream()
                            .filter(shingle -> shingleFrequencies.get(shingle) >= 20)
                            .count();
            repeatedShingleRatios.add(shingles.isEmpty() ? 0.0 : (double) repeated / shingles.size());
        }
        repeatedShingleRatios.sort(Double::compareTo);
        double median = repeatedShingleRatios.get(repeatedShingleRatios.size() / 2);
        assertTrue(median <= 0.10, "과잉 반복 5-gram 중앙값: " + median);
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
