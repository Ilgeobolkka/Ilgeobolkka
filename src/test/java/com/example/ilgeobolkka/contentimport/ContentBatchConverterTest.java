package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.global.config.ContentStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
    void ai_route_v2는_manifest_합계로_변환하고_DB_성공_전에는_staging만_둔다()
            throws IOException {
        Path manifestPath = createAiRouteManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var pdfTool = new FakePdfTool();
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        outputRoot,
                        objectMapper,
                        pdfTool);

        try (ContentBatchConverter.PreparedBatch prepared = converter.prepare()) {
            ContentBatch batch = prepared.batch();

            assertInstanceOf(AiRouteContentManifest.class, prepared.manifest());
            assertEquals(1, batch.books().size());
            assertEquals(1, batch.pages().size());
            assertFalse(Files.exists(outputRoot.resolve(batch.manifestSha256())));
            assertTrue(
                    Files.list(outputRoot)
                            .anyMatch(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .contains(".staging-")));
        }

        assertFalse(
                Files.exists(outputRoot)
                        && Files.list(outputRoot).findAny().isPresent());
    }

    @Test
    void ai_route_v2_결과_manifest에_정책과_페이지별_분석_vector_SHA를_기록한다()
            throws IOException {
        Path manifestPath = createAiRouteManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        outputRoot,
                        objectMapper,
                        new FakePdfTool());

        try (ContentBatchConverter.PreparedBatch prepared = converter.prepare()) {
            AiRouteContentImportCommand command = aiCommand(prepared.batch());

            prepared.writeAiResultManifest(command);
            prepared.publish();

            Path resultPath =
                    outputRoot.resolve(prepared.batch().manifestSha256()).resolve("manifest.json");
            AiRouteContentResultManifest result =
                    objectMapper.readValue(resultPath.toFile(), AiRouteContentResultManifest.class);
            assertEquals("OPENAI_DEFAULT_RETENTION_V1", result.dataPolicyVersion());
            assertEquals("text-embedding-3-small", result.embeddingModel());
            assertEquals(3, result.embeddingDimensions());
            assertEquals(1, result.aiPages().size());
            assertEquals("c".repeat(64), result.aiPages().getFirst().analysisInputSha256());
            assertEquals(
                    ContentBatchConverter.sha256(
                            objectMapper.writeValueAsBytes(List.of(0.1, 0.2, 0.3))),
                    result.aiPages().getFirst().embeddingSha256());

            prepared.rollbackPublication();
            assertFalse(Files.exists(resultPath.getParent()));
        }
    }

    @Test
    void ai_route_v2를_같은_결과로_다시_게시하면_배치_디렉터리를_재사용한다() throws IOException {
        Path manifestPath = createAiRouteManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(
                        manifestPath, outputRoot, objectMapper, new FakePdfTool());

        String first = publishAiRoute(converter, List.of(0.1, 0.2, 0.3));
        String second = publishAiRoute(converter, List.of(0.1, 0.2, 0.3));

        assertEquals(first, second);
        assertEquals(
                1,
                Files.list(outputRoot)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .count());
    }

    @Test
    void ai_route_v2_결과가_이전_게시와_다르면_기존_배치를_덮어쓰지_않는다() throws IOException {
        Path manifestPath = createAiRouteManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(
                        manifestPath, outputRoot, objectMapper, new FakePdfTool());
        String manifestSha256 = publishAiRoute(converter, List.of(0.1, 0.2, 0.3));

        assertThrows(
                IllegalStateException.class,
                () -> publishAiRoute(converter, List.of(0.4, 0.5, 0.6)));

        AiRouteContentResultManifest result =
                objectMapper.readValue(
                        outputRoot.resolve(manifestSha256).resolve("manifest.json").toFile(),
                        AiRouteContentResultManifest.class);
        assertEquals(
                ContentBatchConverter.sha256(
                        objectMapper.writeValueAsBytes(List.of(0.1, 0.2, 0.3))),
                result.aiPages().getFirst().embeddingSha256());
        assertFalse(
                Files.list(outputRoot)
                        .anyMatch(path -> path.getFileName().toString().contains(".staging-")));
    }

    /**
     * 정본 코퍼스를 실제 Poppler로 변환한다. 나머지 변환 테스트는 {@code FakePdfTool}을 쓰므로,
     * manifest가 선언한 페이지 수·구성이 실제 PDF와 어긋나도 드러나지 않는다. 도서를 추가할 때 PDF만
     * 넣고 manifest를 고치지 않는 실수를 잡는 것이 이 테스트다.
     *
     * <p>기본은 건너뛴다. Poppler 실행 파일과 수십 초의 변환이 필요해 CI에서만 켠다.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_CONTENT_IMPORT_INTEGRATION", matches = "true")
    void 정본_ai_route_v2_코퍼스를_실제_Poppler로_변환한다() throws IOException {
        var importProperties = new ContentImportProperties();
        importProperties.setManifest(Path.of("fixtures/content/ai-route-v2/manifest.json"));
        // CI는 Poppler를 PATH가 아니라 별도 경로에 설치하므로 앱과 같은 환경 변수를 따른다.
        importProperties.setPdftotextCommand(
                System.getenv().getOrDefault("PDFTOTEXT_COMMAND", "pdftotext"));
        importProperties.setPdftoppmCommand(
                System.getenv().getOrDefault("PDFTOPPM_COMMAND", "pdftoppm"));
        var storageProperties = new ContentStorageProperties();
        storageProperties.setRoot(tempDirectory.resolve("output"));
        var converter =
                new ContentBatchConverter(
                        importProperties,
                        storageProperties,
                        objectMapper,
                        new PopplerPdfTool(importProperties));

        try (ContentBatchConverter.PreparedBatch prepared = converter.prepare()) {
            ContentBatch batch = prepared.batch();

            assertEquals("ai-route-v2", batch.contentVersion());
            assertEquals(47, batch.books().size());
            assertEquals(1945, batch.pages().size());
            // 비소설 37권의 도표 213페이지 + 소설 10권이 initial-v1에서 그대로 쓰는 이미지 10페이지
            assertEquals(
                    223,
                    batch.pages().stream()
                            .filter(page -> page.contentType() == BookPageContentType.IMAGE)
                            .count());
        }
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
    void 같은_manifest의_게시와_rollback은_잠금으로_직렬화해_다른_실행의_파일을_지우지_않는다()
            throws Exception {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(
                        manifestPath,
                        outputRoot,
                        objectMapper,
                        new FakePdfTool());
        ContentBatchConverter.PreparedBatch first = converter.prepare();
        ContentBatchConverter.PreparedBatch second = converter.prepare();
        CountDownLatch firstPublished = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (first; second) {
            Future<?> firstRun =
                    executor.submit(
                            () ->
                                    first.withPublicationLock(
                                            () -> {
                                                first.publishWhileLocked();
                                                firstPublished.countDown();
                                                await(releaseFirst);
                                                first.rollbackPublicationWhileLocked();
                                            }));
            assertTrue(firstPublished.await(1, TimeUnit.SECONDS));

            Future<?> secondRun =
                    executor.submit(
                            () -> {
                                secondAttempted.countDown();
                                second.withPublicationLock(
                                        () -> {
                                            secondEntered.countDown();
                                            second.publishWhileLocked();
                                        });
                            });
            assertTrue(secondAttempted.await(1, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            firstRun.get(1, TimeUnit.SECONDS);
            secondRun.get(1, TimeUnit.SECONDS);

            Path finalDirectory = outputRoot.resolve(first.batch().manifestSha256());
            assertTrue(Files.isRegularFile(finalDirectory.resolve("manifest.json")));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
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
        Path outputRoot = tempDirectory.resolve("output");
        var pdfTool = new FakePdfTool();
        pdfTool.pdftotextVersion = "26.08.0";
        pdfTool.pdftoppmVersion = "26.08.0";
        var converter =
                new ContentBatchConverter(manifestPath, outputRoot, objectMapper, pdfTool);

        ContentBatch batch = converter.convert();

        assertEquals(400, batch.pages().size());
        ContentResultManifest resultManifest = readResultManifest(outputRoot, batch);
        assertEquals("26.08.0", resultManifest.pdftotextVersion());
        assertEquals("26.08.0", resultManifest.pdftoppmVersion());
    }

    @Test
    void pdftotext와_pdftoppm_버전이_다르면_변환을_거부한다() throws IOException {
        Path manifestPath = createManifest();
        var pdfTool = new FakePdfTool();
        pdfTool.pdftotextVersion = "26.05.0";
        pdfTool.pdftoppmVersion = "26.08.0";
        var converter =
                new ContentBatchConverter(
                        manifestPath, tempDirectory.resolve("output"), objectMapper, pdfTool);

        assertThrows(IllegalStateException.class, converter::convert);
        assertEquals(0, pdfTool.extractCount);
    }

    /** 로컬 26.08.0과 CI 26.05.0이 같은 배치 디렉터리를 두고 부딪히는 경우다. */
    @Test
    void 허용_목록의_다른_버전으로_다시_변환해도_같은_배치_디렉터리를_재사용한다() throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var pdfTool = new FakePdfTool();
        var converter =
                new ContentBatchConverter(manifestPath, outputRoot, objectMapper, pdfTool);
        ContentBatch first = converter.convert();

        pdfTool.pdftotextVersion = "26.08.0";
        pdfTool.pdftoppmVersion = "26.08.0";
        ContentBatch second = converter.convert();

        assertEquals(first.manifestSha256(), second.manifestSha256());
        assertEquals(
                1,
                Files.list(outputRoot)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .count());
        // 재사용이므로 먼저 게시한 배치의 기록을 그대로 둔다.
        assertEquals("26.05.0", readResultManifest(outputRoot, second).pdftoppmVersion());
    }

    @Test
    void 기존_배치가_허용_목록_밖_버전으로_기록돼_있으면_재사용하지_않는다() throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(manifestPath, outputRoot, objectMapper, new FakePdfTool());
        ContentBatch batch = converter.convert();
        Path resultPath = outputRoot.resolve(batch.manifestSha256()).resolve("manifest.json");
        Files.writeString(
                resultPath, Files.readString(resultPath).replace("26.05.0", "25.12.0"));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, converter::convert);
        assertTrue(
                exception.getMessage().contains("허용 목록에 없습니다"),
                "원인과 다른 메시지: " + exception.getMessage());
    }

    /** 두 명령의 기록 버전이 서로 다르면 둘 다 목록 안이어도 재사용하지 않는다. */
    @Test
    void 기존_배치의_두_명령_버전이_서로_다르면_재사용하지_않는다() throws IOException {
        Path manifestPath = createManifest();
        Path outputRoot = tempDirectory.resolve("output");
        var converter =
                new ContentBatchConverter(manifestPath, outputRoot, objectMapper, new FakePdfTool());
        ContentBatch batch = converter.convert();
        Path resultPath = outputRoot.resolve(batch.manifestSha256()).resolve("manifest.json");
        Files.writeString(
                resultPath,
                Files.readString(resultPath)
                        .replace(
                                "\"pdftoppmVersion\" : \"26.05.0\"",
                                "\"pdftoppmVersion\" : \"26.08.0\""));

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, converter::convert);
        assertTrue(
                exception.getMessage().contains("pdftotext와 pdftoppm 버전이 다릅니다"),
                "원인과 다른 메시지: " + exception.getMessage());
    }

    private ContentResultManifest readResultManifest(Path outputRoot, ContentBatch batch) {
        return objectMapper.readValue(
                outputRoot.resolve(batch.manifestSha256()).resolve("manifest.json").toFile(),
                ContentResultManifest.class);
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
        byte[] pdf = "fake-ai-pdf".getBytes();
        Files.createDirectories(manifestPath.getParent().resolve("pdfs"));
        Files.write(manifestPath.getParent().resolve("pdfs/book-001.pdf"), pdf);
        Files.writeString(
                manifestPath,
                """
                {
                  "contentVersion": "ai-route-v2",
                  "dataPolicyVersion": "OPENAI_DEFAULT_RETENTION_V1",
                  "embeddingModel": "text-embedding-3-small",
                  "embeddingDimensions": 3,
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
                """.formatted(ContentBatchConverter.sha256(pdf), "c".repeat(64)));
        return manifestPath;
    }

    /** 게시까지 끝낸 뒤 배치 디렉터리 이름을 돌려준다. */
    private String publishAiRoute(ContentBatchConverter converter, List<Double> vector) {
        try (ContentBatchConverter.PreparedBatch prepared = converter.prepare()) {
            prepared.writeAiResultManifest(aiCommand(prepared.batch(), vector));
            prepared.publish();
            return prepared.batch().manifestSha256();
        }
    }

    private AiRouteContentImportCommand aiCommand(ContentBatch batch) {
        return aiCommand(batch, List.of(0.1, 0.2, 0.3));
    }

    private AiRouteContentImportCommand aiCommand(ContentBatch batch, List<Double> vector) {
        ValidatedAiRouteContent content =
                new ValidatedAiRouteContent(
                        "ai-route-v2",
                        "OPENAI_DEFAULT_RETENTION_V1",
                        "text-embedding-3-small",
                        3,
                        List.of(
                                new ValidatedAiRouteContent.ValidatedBook(
                                        1,
                                        "도서 제목",
                                        1,
                                        true,
                                        true,
                                        List.of(
                                                new ValidatedAiRouteContent.ValidatedPage(
                                                        1,
                                                        true,
                                                        "분석 텍스트",
                                                        "공개 주제",
                                                        60,
                                                        List.of())),
                                        List.of())));
        EmbeddedAiRouteContent embedded =
                new EmbeddedAiRouteContent(
                        "ai-route-v2",
                        "text-embedding-3-small",
                        3,
                        java.util.Map.of(
                                new EmbeddedAiRouteContent.PageKey(1, 1, "ai-route-v2"), vector));
        return AiRouteContentImportCommand.create(batch, content, embedded);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 대기 중 interrupt됐습니다.", exception);
        }
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
