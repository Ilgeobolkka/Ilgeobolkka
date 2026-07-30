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
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class ContentFixtureIntegrityTest {

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

        assertEquals(100, manifest.books().size());
        assertEquals(100, bookIds.size());
        assertEquals(400, totalPageCount);
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
