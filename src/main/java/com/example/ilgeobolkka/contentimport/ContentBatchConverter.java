package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.global.config.ContentStorageProperties;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Profile("content-import")
class ContentBatchConverter {

    // initial-v1에만 적용한다. ai-route-v2는 manifest의 도서·페이지 합계를 그대로 쓴다.
    private static final int INITIAL_BOOK_COUNT = 100;
    private static final int INITIAL_PAGE_COUNT = 400;
    /**
     * 산출물이 바이트 단위로 같음을 확인한 버전만 넣는다. 목록을 늘리려면 같은 표본을 다시 변환해
     * 비교하고 근거를 남긴 뒤에 넣는다.
     *
     * <p>한 값으로 고정하지 않는 이유와 목록에 드는 조건은 ADR-0015에 있다.
     */
    private static final List<String> POPPLER_VERSIONS = List.of("26.05.0", "26.08.0");

    /**
     * 배치 재사용 판정에서 제외하는 결과 manifest 필드. 허용 목록의 버전들은 같은 산출물을 내므로
     * 기록한 버전 문자열이 달라도 같은 배치다.
     */
    private static final List<String> POPPLER_VERSION_FIELDS =
            List.of("pdftotextVersion", "pdftoppmVersion");

    private static final ConcurrentMap<Path, ReentrantLock> LOCAL_PUBLICATION_LOCKS =
            new ConcurrentHashMap<>();

    private final Path manifestPath;
    private final Path outputRoot;
    private final ObjectMapper objectMapper;
    private final ContentManifestParser manifestParser;
    private final PdfTool pdfTool;

    @Autowired
    ContentBatchConverter(
            ContentImportProperties properties,
            ContentStorageProperties storageProperties,
            ObjectMapper objectMapper,
            PdfTool pdfTool) {
        this(properties.manifest(), storageProperties.root(), objectMapper, pdfTool);
    }

    ContentBatchConverter(
            Path manifestPath, Path outputRoot, ObjectMapper objectMapper, PdfTool pdfTool) {
        this.manifestPath = manifestPath;
        this.outputRoot = outputRoot;
        this.objectMapper = objectMapper;
        this.manifestParser = new ContentManifestParser(objectMapper);
        this.pdfTool = pdfTool;
    }

    /** 기존 initial-v1 단독 변환 API. 실제 적재 orchestration은 {@link #prepare()}를 사용한다. */
    ContentBatch convert() {
        try (PreparedBatch prepared = prepare()) {
            if (!(prepared.manifest() instanceof InitialContentManifest)) {
                throw new IllegalStateException(
                        "ai-route-v2는 검증·embedding을 연결한 content-import 서비스로 실행해야 합니다.");
            }
            prepared.publish();
            return prepared.batch();
        }
    }

    /** 모든 파일을 숨김 staging에 완성하지만 공개 경로로 이동하지는 않는다. */
    PreparedBatch prepare() {
        byte[] manifestBytes = readBytes(manifestPath);
        String manifestSha256 = sha256(manifestBytes);
        ContentManifest manifest = manifestParser.parseManifest(manifestBytes);
        List<SourceBook> sourceBooks = sourceBooks(manifest);
        if (manifest instanceof InitialContentManifest initialManifest) {
            validateInitialManifest(initialManifest);
        }
        List<ResolvedBook> books = resolveAndVerifyBooks(sourceBooks);

        String pdftotextVersion = pdfTool.pdftotextVersion();
        String pdftoppmVersion = pdfTool.pdftoppmVersion();
        validatePopplerVersions(pdftotextVersion, pdftoppmVersion);

        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        Path finalDirectory = normalizedOutputRoot.resolve(manifestSha256);
        Path stagingDirectory =
                normalizedOutputRoot.resolve(
                        "." + manifestSha256 + ".staging-" + UUID.randomUUID());
        try {
            Files.createDirectories(stagingDirectory);
            List<ConvertedBook> convertedBooks =
                    convertBooks(books, manifestSha256, stagingDirectory);
            ContentBatch batch =
                    new ContentBatch(
                            manifest.contentVersion(),
                            manifestSha256,
                            List.copyOf(convertedBooks));
            validateConvertedBatch(batch, sourceBooks, manifest instanceof InitialContentManifest);
            writeResultManifest(
                    stagingDirectory,
                    new ContentResultManifest(
                            batch.contentVersion(),
                            manifestSha256,
                            pdftotextVersion,
                            pdftoppmVersion,
                            new ImageConversion("JPEG", 150, 85),
                            batch.books()));
            return new PreparedBatch(
                    manifest,
                    batch,
                    pdftotextVersion,
                    pdftoppmVersion,
                    stagingDirectory,
                    finalDirectory);
        } catch (IOException exception) {
            deleteAfterFailure(stagingDirectory, exception);
            throw new IllegalStateException("콘텐츠 변환 산출물을 준비할 수 없습니다.", exception);
        } catch (RuntimeException | Error exception) {
            deleteAfterFailure(stagingDirectory, exception);
            throw exception;
        }
    }

    private List<SourceBook> sourceBooks(ContentManifest manifest) {
        if (manifest instanceof InitialContentManifest initialManifest) {
            return initialManifest.books().stream()
                    .map(
                            book ->
                                    new SourceBook(
                                            book.bookId(),
                                            book.pdfPath(),
                                            book.pdfSha256(),
                                            book.totalPageCount()))
                    .sorted(Comparator.comparingLong(SourceBook::bookId))
                    .toList();
        }
        if (manifest instanceof AiRouteContentManifest aiManifest) {
            return aiManifest.books().stream()
                    .map(
                            book ->
                                    new SourceBook(
                                            book.bookId(),
                                            book.pdfPath(),
                                            book.pdfSha256(),
                                            book.totalPageCount()))
                    .sorted(Comparator.comparingLong(SourceBook::bookId))
                    .toList();
        }
        throw new IllegalStateException("지원하지 않는 콘텐츠 manifest 타입입니다.");
    }

    private void validateInitialManifest(InitialContentManifest manifest) {
        if (manifest.books().size() != INITIAL_BOOK_COUNT) {
            throw new IllegalStateException("콘텐츠 manifest에는 정확히 100권이 있어야 합니다.");
        }

        int totalPageCount = 0;
        for (InitialContentManifest.Book book : manifest.books()) {
            if (book.bookId() < 1
                    || book.bookId() > INITIAL_BOOK_COUNT
                    || !expectedPdfPath(book.bookId()).equals(book.pdfPath())) {
                throw new IllegalStateException("콘텐츠 manifest에 유효하지 않은 도서가 있습니다.");
            }
            totalPageCount += book.totalPageCount();
        }
        if (totalPageCount != INITIAL_PAGE_COUNT) {
            throw new IllegalStateException("콘텐츠 manifest의 전체 페이지 수는 400이어야 합니다.");
        }
    }

    private List<ResolvedBook> resolveAndVerifyBooks(List<SourceBook> sourceBooks) {
        Path manifestDirectory = manifestPath.toAbsolutePath().normalize().getParent();
        if (manifestDirectory == null) {
            throw new IllegalStateException("콘텐츠 manifest 상위 디렉터리를 확인할 수 없습니다.");
        }

        List<ResolvedBook> resolvedBooks = new ArrayList<>(sourceBooks.size());
        for (SourceBook book : sourceBooks) {
            Path pdfPath = manifestDirectory.resolve(book.pdfPath()).normalize();
            if (!pdfPath.startsWith(manifestDirectory) || !Files.isRegularFile(pdfPath)) {
                throw new IllegalStateException("콘텐츠 PDF 파일을 찾을 수 없습니다: " + book.pdfPath());
            }
            String actualSha256 = sha256(readBytes(pdfPath));
            if (!actualSha256.equals(book.pdfSha256())) {
                throw new IllegalStateException(
                        "콘텐츠 PDF SHA-256이 manifest와 다릅니다: " + book.pdfPath());
            }
            resolvedBooks.add(new ResolvedBook(book, pdfPath));
        }
        return resolvedBooks;
    }

    private List<ConvertedBook> convertBooks(
            List<ResolvedBook> books, String manifestSha256, Path stagingDirectory)
            throws IOException {
        List<ConvertedBook> convertedBooks = new ArrayList<>(books.size());
        for (ResolvedBook resolvedBook : books) {
            SourceBook book = resolvedBook.source();
            Path bookStagingDirectory =
                    stagingDirectory.resolve("book-%03d".formatted(book.bookId()));
            Files.createDirectories(bookStagingDirectory);

            List<ConvertedPage> pages = new ArrayList<>(book.totalPageCount());
            for (int pageNumber = 1; pageNumber <= book.totalPageCount(); pageNumber++) {
                String text = pdfTool.extractText(resolvedBook.pdfPath(), pageNumber);
                if (!text.isEmpty()) {
                    pages.add(
                            new ConvertedPage(
                                    book.bookId(),
                                    pageNumber,
                                    BookPageContentType.TEXT,
                                    text,
                                    null,
                                    null));
                    continue;
                }

                String imageFileName = "page-%03d.jpg".formatted(pageNumber);
                Path outputPrefix =
                        bookStagingDirectory.resolve("page-%03d".formatted(pageNumber));
                pdfTool.renderJpeg(resolvedBook.pdfPath(), pageNumber, outputPrefix);
                Path imageFile = bookStagingDirectory.resolve(imageFileName);
                long imageBytes = Files.size(imageFile);
                if (!Files.isRegularFile(imageFile) || imageBytes < 1) {
                    throw new IllegalStateException(
                            "IMAGE 페이지 산출물이 비어 있습니다: "
                                    + book.bookId()
                                    + "-"
                                    + pageNumber);
                }
                pages.add(
                        new ConvertedPage(
                                book.bookId(),
                                pageNumber,
                                BookPageContentType.IMAGE,
                                null,
                                logicalImagePath(manifestSha256, book.bookId(), pageNumber),
                                imageBytes));
            }

            if (pdfTool.pageExists(resolvedBook.pdfPath(), book.totalPageCount() + 1)) {
                throw new IllegalStateException(
                        "PDF 페이지 수가 manifest보다 많습니다: " + book.pdfPath());
            }
            convertedBooks.add(
                    new ConvertedBook(
                            book.bookId(),
                            book.pdfSha256(),
                            book.totalPageCount(),
                            List.copyOf(pages)));
        }
        return convertedBooks;
    }

    private void validateConvertedBatch(
            ContentBatch batch, List<SourceBook> sourceBooks, boolean initialContent) {
        Map<Long, Integer> expectedPageCounts = new HashMap<>();
        int expectedTotalPageCount = 0;
        for (SourceBook book : sourceBooks) {
            expectedPageCounts.put(book.bookId(), book.totalPageCount());
            expectedTotalPageCount += book.totalPageCount();
        }
        if (batch.books().size() != sourceBooks.size()
                || batch.pages().size() != expectedTotalPageCount
                || (initialContent
                        && (batch.books().size() != INITIAL_BOOK_COUNT
                                || batch.pages().size() != INITIAL_PAGE_COUNT))) {
            throw new IllegalStateException("변환 결과의 도서·페이지 수가 manifest와 다릅니다.");
        }

        Set<PageKey> keys = new HashSet<>();
        for (ConvertedBook book : batch.books()) {
            Integer expectedPageCount = expectedPageCounts.get(book.bookId());
            if (expectedPageCount == null
                    || expectedPageCount != book.totalPageCount()
                    || book.pages().size() != book.totalPageCount()) {
                throw new IllegalStateException("변환 페이지 수가 PDF 페이지 수와 다릅니다.");
            }
            for (int index = 0; index < book.pages().size(); index++) {
                ConvertedPage page = book.pages().get(index);
                boolean validText =
                        page.contentType() == BookPageContentType.TEXT
                                && page.textContent() != null
                                && !page.textContent().isBlank()
                                && page.imagePath() == null
                                && page.imageBytes() == null;
                boolean validImage =
                        page.contentType() == BookPageContentType.IMAGE
                                && page.textContent() == null
                                && page.imagePath() != null
                                && !page.imagePath().isBlank()
                                && page.imageBytes() != null
                                && page.imageBytes() > 0;
                if (page.bookId() != book.bookId()
                        || page.pageNumber() != index + 1
                        || !keys.add(new PageKey(page.bookId(), page.pageNumber()))
                        || (!validText && !validImage)) {
                    throw new IllegalStateException("변환 페이지 계약이 올바르지 않습니다.");
                }
            }
        }
    }

    private void writeResultManifest(Path stagingDirectory, Object resultManifest)
            throws IOException {
        byte[] result =
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(resultManifest);
        Files.write(stagingDirectory.resolve("manifest.json"), result);
    }

    private boolean publish(
            Path stagingDirectory, Path finalDirectory, ContentBatch batch) throws IOException {
        if (Files.exists(finalDirectory)) {
            verifyExistingBatch(stagingDirectory, finalDirectory, batch);
            return false;
        }
        try {
            Files.move(stagingDirectory, finalDirectory, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(stagingDirectory, finalDirectory);
            } catch (FileAlreadyExistsException concurrentPublication) {
                verifyExistingBatch(stagingDirectory, finalDirectory, batch);
                return false;
            }
        } catch (FileAlreadyExistsException concurrentPublication) {
            verifyExistingBatch(stagingDirectory, finalDirectory, batch);
            return false;
        }
        return true;
    }

    private void verifyExistingBatch(
            Path stagingDirectory, Path finalDirectory, ContentBatch batch) throws IOException {
        Path expectedManifest = stagingDirectory.resolve("manifest.json");
        Path existingManifest = finalDirectory.resolve("manifest.json");
        if (!Files.isRegularFile(existingManifest)) {
            throw new IllegalStateException(
                    "같은 manifest 배치 디렉터리에 다른 결과가 있습니다: " + finalDirectory);
        }
        ObjectNode expectedResult = readResultManifest(expectedManifest);
        ObjectNode existingResult = readResultManifest(existingManifest);
        // 기존 배치도 같은 허용 목록 버전으로 만든 것이어야 버전 필드를 빼고 비교할 수 있다.
        String existingPdftotextVersion = existingResult.path("pdftotextVersion").asString("");
        String existingPdftoppmVersion = existingResult.path("pdftoppmVersion").asString("");
        if (!POPPLER_VERSIONS.contains(existingPdftotextVersion)
                || !existingPdftotextVersion.equals(existingPdftoppmVersion)) {
            throw new IllegalStateException(
                    "기존 배치의 Poppler 버전이 허용 목록에 없습니다: "
                            + finalDirectory
                            + " ("
                            + existingPdftotextVersion
                            + ", "
                            + existingPdftoppmVersion
                            + ")");
        }
        POPPLER_VERSION_FIELDS.forEach(
                field -> {
                    expectedResult.remove(field);
                    existingResult.remove(field);
                });
        if (!expectedResult.equals(existingResult)) {
            throw new IllegalStateException(
                    "같은 manifest 배치 디렉터리에 다른 결과가 있습니다: " + finalDirectory);
        }

        for (ConvertedPage page : batch.pages()) {
            if (page.contentType() != BookPageContentType.IMAGE) {
                continue;
            }
            Path relativeImage =
                    Path.of(
                            "book-%03d".formatted(page.bookId()),
                            "page-%03d.jpg".formatted(page.pageNumber()));
            Path expectedImage = stagingDirectory.resolve(relativeImage);
            Path existingImage = finalDirectory.resolve(relativeImage);
            if (!Files.isRegularFile(existingImage)
                    || Files.mismatch(expectedImage, existingImage) != -1) {
                throw new IllegalStateException(
                        "같은 manifest 배치 디렉터리에 다른 이미지가 있습니다: " + relativeImage);
            }
        }
    }

    private ObjectNode readResultManifest(Path manifestFile) throws IOException {
        if (!(objectMapper.readTree(Files.readAllBytes(manifestFile))
                instanceof ObjectNode resultManifest)) {
            throw new IllegalStateException("결과 manifest를 읽을 수 없습니다: " + manifestFile);
        }
        return resultManifest;
    }

    private String logicalImagePath(String manifestSha256, long bookId, int pageNumber) {
        return outputRoot
                .resolve(manifestSha256)
                .resolve("book-%03d".formatted(bookId))
                .resolve("page-%03d.jpg".formatted(pageNumber))
                .normalize()
                .toString()
                .replace(File.separatorChar, '/');
    }

    private void validatePopplerVersions(String pdftotextVersion, String pdftoppmVersion) {
        validatePopplerVersion("pdftotext", pdftotextVersion);
        validatePopplerVersion("pdftoppm", pdftoppmVersion);
        // 산출물 동일성은 같은 버전끼리 확인했으므로 한 배치는 한 툴체인으로 변환한다.
        if (!pdftotextVersion.equals(pdftoppmVersion)) {
            throw new IllegalStateException(
                    "pdftotext와 pdftoppm은 같은 Poppler 버전이어야 합니다: "
                            + pdftotextVersion
                            + ", "
                            + pdftoppmVersion);
        }
    }

    private void validatePopplerVersion(String command, String actualVersion) {
        if (!POPPLER_VERSIONS.contains(actualVersion)) {
            throw new IllegalStateException(
                    command
                            + " 버전은 "
                            + String.join(", ", POPPLER_VERSIONS)
                            + " 중 하나여야 합니다: "
                            + actualVersion);
        }
    }

    private String expectedPdfPath(long bookId) {
        return "pdfs/book-%03d.pdf".formatted(bookId);
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("파일을 읽을 수 없습니다: " + path, exception);
        }
    }

    private void deleteAfterFailure(Path directory, Throwable original) {
        try {
            deleteRecursively(directory);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException exception) {
                                    throw new IllegalStateException(
                                            "임시 변환 산출물을 정리할 수 없습니다: " + path,
                                            exception);
                                }
                            });
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "임시 변환 산출물을 확인할 수 없습니다: " + directory, exception);
        }
    }

    final class PreparedBatch implements AutoCloseable {

        private final ContentManifest manifest;
        private final ContentBatch batch;
        private final String pdftotextVersion;
        private final String pdftoppmVersion;
        private final Path stagingDirectory;
        private final Path finalDirectory;
        private final Path publicationLockFile;
        private boolean publishedByThisRun;
        private boolean publishedOrReused;
        private Thread publicationLockOwner;

        private PreparedBatch(
                ContentManifest manifest,
                ContentBatch batch,
                String pdftotextVersion,
                String pdftoppmVersion,
                Path stagingDirectory,
                Path finalDirectory) {
            this.manifest = manifest;
            this.batch = batch;
            this.pdftotextVersion = pdftotextVersion;
            this.pdftoppmVersion = pdftoppmVersion;
            this.stagingDirectory = stagingDirectory;
            this.finalDirectory = finalDirectory;
            this.publicationLockFile =
                    finalDirectory.resolveSibling(
                            "." + finalDirectory.getFileName() + ".lock");
        }

        ContentManifest manifest() {
            return manifest;
        }

        ContentBatch batch() {
            return batch;
        }

        Path finalDirectory() {
            return finalDirectory;
        }

        void writeAiResultManifest(AiRouteContentImportCommand command) {
            if (!(manifest instanceof AiRouteContentManifest aiManifest)
                    || !batch.equals(command.batch())
                    || publishedOrReused) {
                throw new IllegalStateException("AI 결과 manifest를 기록할 수 없는 상태입니다.");
            }
            if (!aiManifest.dataPolicyVersion().equals(command.content().dataPolicyVersion())
                    || !aiManifest.embeddingModel().equals(command.content().embeddingModel())
                    || aiManifest.embeddingDimensions()
                            != command.content().embeddingDimensions()) {
                throw new AiRouteContentImportException(
                        "AI manifest와 검증 결과의 정책·모델·차원이 다릅니다.");
            }

            List<AiRoutePageResult> aiPages = new ArrayList<>();
            for (AiRouteContentManifest.Book book : aiManifest.books()) {
                for (AiRouteContentManifest.Page page : book.pages()) {
                    List<Double> vector =
                            command.embedded().vectorOf(book.bookId(), page.pageNumber());
                    String embeddingSha256 =
                            vector == null ? null : sha256(objectMapper.writeValueAsBytes(vector));
                    aiPages.add(
                            new AiRoutePageResult(
                                    book.bookId(),
                                    page.pageNumber(),
                                    page.aiRouteCandidatePage(),
                                    page.aiAnalysisInputSha256(),
                                    embeddingSha256));
                }
            }
            try {
                writeResultManifest(
                        stagingDirectory,
                        new AiRouteContentResultManifest(
                                batch.contentVersion(),
                                batch.manifestSha256(),
                                pdftotextVersion,
                                pdftoppmVersion,
                                new ImageConversion("JPEG", 150, 85),
                                command.content().dataPolicyVersion(),
                                command.content().embeddingModel(),
                                command.content().embeddingDimensions(),
                                batch.books(),
                                List.copyOf(aiPages)));
            } catch (IOException exception) {
                throw new IllegalStateException("AI 변환 결과 manifest를 기록할 수 없습니다.", exception);
            }
        }

        void publish() {
            withPublicationLock(this::publishWhileLocked);
        }

        void publishWhileLocked() {
            requirePublicationLock();
            if (publishedOrReused) {
                return;
            }
            try {
                publishedByThisRun =
                        ContentBatchConverter.this.publish(
                                stagingDirectory, finalDirectory, batch);
                publishedOrReused = true;
                if (!publishedByThisRun) {
                    deleteRecursively(stagingDirectory);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("콘텐츠 변환 산출물을 공개할 수 없습니다.", exception);
            }
        }

        void rollbackPublication() {
            withPublicationLock(this::rollbackPublicationWhileLocked);
        }

        void rollbackPublicationWhileLocked() {
            requirePublicationLock();
            if (!publishedByThisRun) {
                return;
            }
            deleteRecursively(finalDirectory);
            publishedByThisRun = false;
            publishedOrReused = false;
        }

        /** 같은 manifest의 게시·DB commit·보상 삭제를 JVM과 프로세스 사이에서 직렬화한다. */
        void withPublicationLock(Runnable action) {
            if (action == null) {
                throw new IllegalArgumentException("콘텐츠 게시 잠금 안에서 실행할 작업이 필요합니다.");
            }
            if (publicationLockOwner == Thread.currentThread()) {
                action.run();
                return;
            }

            ReentrantLock localLock =
                    LOCAL_PUBLICATION_LOCKS.computeIfAbsent(
                            publicationLockFile, ignored -> new ReentrantLock());
            localLock.lock();
            try {
                Files.createDirectories(publicationLockFile.getParent());
                try (FileChannel channel =
                                FileChannel.open(
                                        publicationLockFile,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE);
                        FileLock ignored = channel.lock()) {
                    publicationLockOwner = Thread.currentThread();
                    try {
                        action.run();
                    } finally {
                        publicationLockOwner = null;
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "콘텐츠 게시 잠금을 획득할 수 없습니다: " + publicationLockFile,
                        exception);
            } finally {
                localLock.unlock();
            }
        }

        private void requirePublicationLock() {
            if (publicationLockOwner != Thread.currentThread()) {
                throw new IllegalStateException("콘텐츠 게시 잠금 안에서만 파일 상태를 바꿀 수 있습니다.");
            }
        }

        @Override
        public void close() {
            deleteRecursively(stagingDirectory);
        }
    }

    private record SourceBook(
            long bookId, String pdfPath, String pdfSha256, int totalPageCount) {}

    private record ResolvedBook(SourceBook source, Path pdfPath) {}

    private record PageKey(long bookId, int pageNumber) {}
}
