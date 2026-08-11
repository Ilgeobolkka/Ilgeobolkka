package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class ContentFixtureIntegrityTest {

    private static final String INITIAL_MANIFEST_SHA256 =
            "e91609452a5a85baec4f61464cd127e6e79d2223336a28867027f58c57924408";

    @Test
    void 고정_PDF_100권과_manifest_SHA_페이지_계약이_일치한다() throws IOException {
        Path fixtureDirectory = Path.of("fixtures/content");
        Path manifestPath = fixtureDirectory.resolve("manifest.json");
        InitialContentManifest manifest =
                new ObjectMapper()
                        .readValue(manifestPath.toFile(), InitialContentManifest.class);
        Set<Long> bookIds = new HashSet<>();
        int totalPageCount = 0;

        for (InitialContentManifest.Book book : manifest.books()) {
            Path pdfPath = fixtureDirectory.resolve(book.pdfPath());
            assertAll(
                    () -> assertTrue(bookIds.add(book.bookId())),
                    () -> assertTrue(Files.isRegularFile(pdfPath)),
                    () ->
                            assertEquals(
                                    book.pdfSha256(),
                                    ContentBatchConverter.sha256(
                                            Files.readAllBytes(pdfPath))),
                    () ->
                            assertTrue(
                                    new String(
                                                    Files.readAllBytes(pdfPath),
                                                    0,
                                                    5,
                                                    StandardCharsets.US_ASCII)
                                            .startsWith("%PDF-")));
            totalPageCount += book.totalPageCount();
        }

        assertEquals(
                INITIAL_MANIFEST_SHA256,
                ContentBatchConverter.sha256(Files.readAllBytes(manifestPath)));
        assertEquals("initial-v1", manifest.contentVersion());
        assertEquals(100, manifest.books().size());
        assertEquals(100, bookIds.size());
        assertEquals(400, totalPageCount);
    }

    @Test
    void ai_route_v2_정본_fixture가_C01_parser로_읽힌다() throws IOException {
        // 정본 fixture를 실제 parser로 읽는 유일한 검사다. 나머지 parser 테스트는 인라인 JSON을 쓰기
        // 때문에, manifest에 새 필드를 넣고 parser에 반영하지 않아도 드러나지 않는다.
        Path fixtureDirectory = Path.of("fixtures/content/ai-route-v2");
        ContentManifestParser parser = new ContentManifestParser(new ObjectMapper());

        AiRouteContentManifest manifest =
                assertInstanceOf(
                        AiRouteContentManifest.class,
                        parser.parseManifest(
                                Files.readString(fixtureDirectory.resolve("manifest.json"))));
        AiRouteEvaluationDataset evaluation =
                parser.parseEvaluation(
                        Files.readString(fixtureDirectory.resolve("evaluation.json")));

        assertFalse(manifest.books().isEmpty());
        assertFalse(evaluation.cases().isEmpty());
        for (AiRouteContentManifest.Book book : manifest.books()) {
            assertAll(
                    () -> assertFalse(book.title().isBlank()),
                    () ->
                            assertTrue(
                                    book.pages().stream()
                                            .noneMatch(
                                                    page ->
                                                            page.contentRole()
                                                                            == AiRouteContentManifest
                                                                                    .ContentRole
                                                                                    .FRONT_MATTER
                                                                    && page.aiRouteCandidatePage())));
        }
    }

    @Test
    void PDF_manifest와_기존_페이지_PNG는_런타임_classpath에_없다() {
        assertAll(
                () ->
                        assertFalse(
                                new ClassPathResource("fixtures/content/manifest.json")
                                        .exists()),
                () ->
                        assertFalse(
                                new ClassPathResource(
                                                "demo/book-pages/category-01.png")
                                        .exists()));
    }
}
