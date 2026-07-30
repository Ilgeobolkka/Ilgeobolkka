package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ContentBatchConverterTest {

    @TempDir Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void PDF_100권을_400개_TEXT_IMAGE_페이지로_변환한다() throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var pdfTool = new FakePdfTool();
        var converter =
                new ContentBatchConverter(
                        manifestPath, outputRoot, objectMapper, pdfTool);

        ContentBatch batch = converter.convert();

        assertEquals(100, batch.books().size());
        assertEquals(400, batch.pages().size());
        assertEquals(
                300,
                batch.pages().stream()
                        .filter(page -> page.contentType() == BookPageContentType.TEXT)
                        .count());
        assertEquals(
                100,
                batch.pages().stream()
                        .filter(page -> page.contentType() == BookPageContentType.IMAGE)
                        .count());
        Path batchDirectory = outputRoot.resolve(batch.manifestSha256());
        assertTrue(Files.isRegularFile(batchDirectory.resolve("manifest.json")));
        assertTrue(
                Files.isRegularFile(
                        batchDirectory.resolve("book-001/page-002.jpg")));
        assertFalse(
                Files.list(outputRoot)
                        .anyMatch(path -> path.getFileName().toString().contains(".staging-")));
    }

    @Test
    void 같은_manifest를_다시_변환하면_동일한_배치_디렉터리를_재사용한다()
            throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        outputRoot,
                        objectMapper,
                        new FakePdfTool());

        ContentBatch first = converter.convert();
        ContentBatch second = converter.convert();

        assertEquals(first.manifestSha256(), second.manifestSha256());
        assertEquals(
                1,
                Files.list(outputRoot)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .count());
    }

    @Test
    void PDF_SHA가_manifest와_다르면_산출물을_남기지_않는다() throws IOException {
        Path manifestPath = createManifest();
        Files.writeString(
                manifestPath.getParent().resolve("pdfs/book-001.pdf"),
                "변조된 PDF");
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        outputRoot,
                        objectMapper,
                        new FakePdfTool());

        assertThrows(IllegalStateException.class, converter::convert);

        assertFalse(Files.exists(outputRoot));
    }

    @Test
    void PDF가_manifest보다_한_페이지_더_있으면_전체_변환을_실패시킨다()
            throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var pdfTool = new FakePdfTool();
        pdfTool.hasUnexpectedPage = true;
        var converter =
                new ContentBatchConverter(
                        manifestPath, outputRoot, objectMapper, pdfTool);

        assertThrows(IllegalStateException.class, converter::convert);

        assertTrue(
                !Files.exists(outputRoot)
                        || Files.list(outputRoot).findAny().isEmpty());
    }

    @Test
    void Poppler_버전이_26050이_아니면_변환을_거부한다() throws IOException {
        Path manifestPath = createManifest();
        var pdfTool = new FakePdfTool();
        pdfTool.pdftotextVersion = "25.12.0";
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        tempDirectory.resolve("output"),
                        objectMapper,
                        pdfTool);

        assertThrows(IllegalStateException.class, converter::convert);
        assertEquals(0, pdfTool.extractCount);
    }

    private Path createManifest() throws IOException {
        Path fixtureDirectory = tempDirectory.resolve("fixtures");
        Path pdfDirectory = fixtureDirectory.resolve("pdfs");
        Files.createDirectories(pdfDirectory);

        List<ManifestBook> books = new ArrayList<>();
        for (long bookId = 1; bookId <= 100; bookId++) {
            String relativePath = "pdfs/book-%03d.pdf".formatted(bookId);
            byte[] content = "fake-pdf-%03d".formatted(bookId).getBytes();
            Files.write(fixtureDirectory.resolve(relativePath), content);
            books.add(
                    new ManifestBook(
                            bookId,
                            relativePath,
                            ContentBatchConverter.sha256(content),
                            4));
        }

        Path manifestPath = fixtureDirectory.resolve("manifest.json");
        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(manifestPath.toFile(), new ContentManifest(books));
        return manifestPath;
    }

    private static class FakePdfTool implements PdfTool {

        private String pdftotextVersion = "26.05.0";
        private boolean hasUnexpectedPage;
        private int extractCount;

        @Override
        public String pdftotextVersion() {
            return pdftotextVersion;
        }

        @Override
        public String pdftoppmVersion() {
            return "26.05.0";
        }

        @Override
        public String extractText(Path pdfPath, int pageNumber) {
            extractCount++;
            if (pageNumber == 2) {
                return "";
            }
            return "%s의 %d페이지".formatted(pdfPath.getFileName(), pageNumber);
        }

        @Override
        public boolean pageExists(Path pdfPath, int pageNumber) {
            return hasUnexpectedPage
                    && pdfPath.getFileName().toString().equals("book-001.pdf");
        }

        @Override
        public void renderJpeg(Path pdfPath, int pageNumber, Path outputPrefix) {
            try {
                Files.writeString(
                        outputPrefix.resolveSibling(
                                outputPrefix.getFileName() + ".jpg"),
                        "image");
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
