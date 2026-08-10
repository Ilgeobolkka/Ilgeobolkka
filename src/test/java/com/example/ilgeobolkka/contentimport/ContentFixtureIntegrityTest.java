package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ContentFixtureIntegrityTest {

    private static final String INITIAL_MANIFEST_SHA256 =
            "e91609452a5a85baec4f61464cd127e6e79d2223336a28867027f58c57924408";
    private static final String AI_ROUTE_MANIFEST_SHA256 =
            "c2515fa4daa90b5417010af2f1530b7f452272368aad5f85e200737ddc6e62fd";
    private static final Pattern PDF_PAGE_PATTERN = Pattern.compile("/Type\\s*/Page\\b");

    @Test
    void 고정_PDF_100권과_manifest_SHA_페이지_계약이_일치한다() throws IOException {
        Path fixtureDirectory = Path.of("fixtures/content");
        Path manifestPath = fixtureDirectory.resolve("manifest.json");
        ContentManifest manifest =
                new ObjectMapper().readValue(manifestPath.toFile(), ContentManifest.class);
        Set<Long> bookIds = new HashSet<>();
        int totalPageCount = 0;

        for (ManifestBook book : manifest.books()) {
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
    void AI_경로_PDF_100권과_manifest_SHA_페이지_계약이_일치한다() throws IOException {
        Path fixtureDirectory = Path.of("fixtures/content/ai-route-v2");
        Path manifestPath = fixtureDirectory.resolve("manifest.json");
        byte[] manifestBytes = Files.readAllBytes(manifestPath);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode manifest = objectMapper.readTree(manifestBytes);
        JsonNode books = manifest.get("books");
        Set<Long> bookIds = new HashSet<>();
        int totalPageCount = 0;

        for (JsonNode book : books) {
            long bookId = book.get("bookId").asLong();
            int pageCount = book.get("totalPageCount").asInt();
            Path pdfPath = fixtureDirectory.resolve(book.get("pdfPath").asText());
            byte[] pdfBytes = Files.readAllBytes(pdfPath);

            assertAll(
                    () -> assertTrue(bookIds.add(bookId)),
                    () -> assertTrue(Files.isRegularFile(pdfPath)),
                    () ->
                            assertEquals(
                                    book.get("pdfSha256").asText(),
                                    ContentBatchConverter.sha256(pdfBytes)),
                    () -> assertEquals(pageCount, countPdfPages(pdfBytes)),
                    () ->
                            assertTrue(
                                    new String(
                                                    pdfBytes,
                                                    0,
                                                    5,
                                                    StandardCharsets.US_ASCII)
                                            .startsWith("%PDF-")));
            totalPageCount += pageCount;
        }

        String actualManifestSha256 = ContentBatchConverter.sha256(manifestBytes);
        JsonNode verificationSummary =
                objectMapper.readTree(
                        fixtureDirectory.resolve("verification-summary.json").toFile());

        assertEquals(AI_ROUTE_MANIFEST_SHA256, actualManifestSha256);
        assertEquals(actualManifestSha256, verificationSummary.get("manifestSha256").asText());
        assertEquals("ai-route-v2", manifest.get("contentVersion").asText());
        assertEquals(100, books.size());
        assertEquals(100, bookIds.size());
        assertEquals(5485, totalPageCount);
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

    private static int countPdfPages(byte[] pdfBytes) {
        Matcher matcher =
                PDF_PAGE_PATTERN.matcher(new String(pdfBytes, StandardCharsets.ISO_8859_1));
        int pageCount = 0;
        while (matcher.find()) {
            pageCount++;
        }
        return pageCount;
    }
}
