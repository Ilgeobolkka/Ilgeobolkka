package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.global.config.ContentStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ContentBatchConverterTest {

    private static final String INITIAL_CONTENT_VERSION = "initial-v1";

    @TempDir Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 사용자_지정_콘텐츠_루트에_PDF_100권을_400개_TEXT_IMAGE_페이지로_변환한다()
            throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var importProperties = new ContentImportProperties();
        importProperties.setManifest(manifestPath);
        var storageProperties = new ContentStorageProperties();
        storageProperties.setRoot(outputRoot);
        var pdfTool = new FakePdfTool();
        var converter =
                new ContentBatchConverter(
                        importProperties, storageProperties, objectMapper, pdfTool);

        ContentBatch batch = converter.convert();

        assertEquals(INITIAL_CONTENT_VERSION, batch.contentVersion());
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
        ContentResultManifest resultManifest =
                objectMapper.readValue(
                        batchDirectory.resolve("manifest.json").toFile(),
                        ContentResultManifest.class);
        assertEquals(INITIAL_CONTENT_VERSION, resultManifest.contentVersion());
        assertTrue(
                Files.isRegularFile(
                        batchDirectory.resolve("book-001/page-002.jpg")));
        assertFalse(
                Files.list(outputRoot)
                        .anyMatch(path -> path.getFileName().toString().contains(".staging-")));
    }

    @Test
    void 초기_manifest의_contentVersion이_없으면_변환을_거부한다() throws IOException {
        Path manifestPath = createManifest(null);
        var pdfTool = new FakePdfTool();
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        tempDirectory.resolve("output"),
                        objectMapper,
                        pdfTool);

        assertThrows(IllegalStateException.class, converter::convert);
        assertEquals(0, pdfTool.extractCount);
    }

    @Test
    void initial_v1이_아닌_정상_AI_manifest는_전체_사전_검증_연결_전_변환을_거부한다()
            throws IOException {
        Path manifestPath = createAiRouteManifest();
        var pdfTool = new FakePdfTool();
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        tempDirectory.resolve("output"),
                        objectMapper,
                        pdfTool);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, converter::convert);

        assertTrue(exception.getMessage().contains("전체 사전 검증 연결 후"));
        assertEquals(0, pdfTool.extractCount);
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
    void Poppler_버전이_허용_목록에_없으면_변환을_거부한다() throws IOException {
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

    /** 로컬(Homebrew)과 CI(conda-forge)가 서로 다른 버전을 주므로 허용 목록의 어느 쪽이든 변환한다. */
    @Test
    void 허용_목록에_있는_다른_Poppler_버전으로도_변환한다() throws IOException {
        Path manifestPath = createManifest();
        var pdfTool = new FakePdfTool();
        pdfTool.pdftotextVersion = "26.08.0";
        pdfTool.pdftoppmVersion = "26.08.0";
        var converter =
                new ContentBatchConverter(
                        manifestPath, tempDirectory.resolve("output"), objectMapper, pdfTool);

        ContentBatch batch = converter.convert();

        assertEquals(400, batch.pages().size());
    }

    private Path createManifest() throws IOException {
        return createManifest(INITIAL_CONTENT_VERSION);
    }

    private Path createManifest(String contentVersion) throws IOException {
        Path fixtureDirectory = tempDirectory.resolve("fixtures");
        Path pdfDirectory = fixtureDirectory.resolve("pdfs");
        Files.createDirectories(pdfDirectory);

        List<InitialContentManifest.Book> books = new ArrayList<>();
        for (long bookId = 1; bookId <= 100; bookId++) {
            String relativePath = "pdfs/book-%03d.pdf".formatted(bookId);
            byte[] content = "fake-pdf-%03d".formatted(bookId).getBytes();
            Files.write(fixtureDirectory.resolve(relativePath), content);
            books.add(
                    new InitialContentManifest.Book(
                            bookId,
                            relativePath,
                            ContentBatchConverter.sha256(content),
                            4));
        }

        Path manifestPath = fixtureDirectory.resolve("manifest.json");
        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        manifestPath.toFile(),
                        new InitialContentManifest(contentVersion, books));
        return manifestPath;
    }

    private Path createAiRouteManifest() throws IOException {
        Path manifestPath = tempDirectory.resolve("ai-route-v2/manifest.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(
                manifestPath,
                """
                {
                  "contentVersion": "ai-route-v2",
                  "dataPolicyVersion": "OPENAI_DEFAULT_RETENTION_V1",
                  "embeddingModel": "text-embedding-3-small",
                  "embeddingDimensions": 1536,
                  "books": [{
                    "bookId": 1,
                    "title": "도서 제목",
                    "pdfPath": "pdfs/book-001.pdf",
                    "pdfSha256": "%s",
                    "totalPageCount": 1,
                    "aiRouteCandidate": true,
                    "aiExternalTransferAllowed": true,
                    "pages": [{
                      "pageNumber": 1,
                      "chapter": "1장",
                      "section": "1절",
                      "primaryConcepts": ["핵심 개념"],
                      "secondaryConcepts": [],
                      "contentRole": "CORE",
                      "aiRouteCandidatePage": true,
                      "aiAnalysisText": "분석 텍스트",
                      "aiAnalysisInputSha256": "%s",
                      "aiPublicGuideTopic": "공개 주제",
                      "estimatedReadingSeconds": 60,
                      "prerequisitePageNumbers": [],
                      "duplicateGroupKeys": []
                    }]
                  }]
                }
                """.formatted("a".repeat(64), "b".repeat(64)));
        return manifestPath;
    }

    private static class FakePdfTool implements PdfTool {

        private String pdftotextVersion = "26.05.0";
        private String pdftoppmVersion = "26.05.0";
        private boolean hasUnexpectedPage;
        private int extractCount;

        @Override
        public String pdftotextVersion() {
            return pdftotextVersion;
        }

        @Override
        public String pdftoppmVersion() {
            return pdftoppmVersion;
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
