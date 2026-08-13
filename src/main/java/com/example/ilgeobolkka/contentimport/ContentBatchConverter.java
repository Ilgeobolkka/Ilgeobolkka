package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.global.config.ContentStorageProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("content-import")
class ContentBatchConverter {

    // 초기 코퍼스(initial-v1) 전용 계약이다. 이후 버전은 이 제한을 풀지 않고 버전별 검증 경로를 추가한다.
    private static final int BOOK_COUNT = 100;
    private static final int PAGE_COUNT = 400;
    /**
     * 산출물이 바이트 단위로 같음을 확인한 버전만 넣는다. 목록을 늘리려면 같은 표본을 다시 변환해
     * 비교하고 근거를 남긴 뒤에 넣는다.
     *
     * <p>한 값으로 고정하지 않는 이유와 목록에 드는 조건은 ADR-0015에 있다.
     */
    private static final List<String> POPPLER_VERSIONS = List.of("26.05.0", "26.08.0");

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
        this(
                properties.manifest(),
                storageProperties.root(),
                objectMapper,
                pdfTool);
    }

    ContentBatchConverter(
            Path manifestPath, Path outputRoot, ObjectMapper objectMapper, PdfTool pdfTool) {
        this.manifestPath = manifestPath;
        this.outputRoot = outputRoot;
        this.objectMapper = objectMapper;
        this.manifestParser = new ContentManifestParser(objectMapper);
        this.pdfTool = pdfTool;
    }

    ContentBatch convert() {
        byte[] manifestBytes = readBytes(manifestPath);
        String manifestSha256 = sha256(manifestBytes);
        ContentManifest manifest = manifestParser.parseManifest(manifestBytes);
        List<SourceBook> sourceBooks = validateAndCollect(manifest);
        List<ResolvedBook> books = resolveAndVerifyBooks(sourceBooks);

        String pdftotextVersion = pdfTool.pdftotextVersion();
        String pdftoppmVersion = pdfTool.pdftoppmVersion();
        validatePopplerVersion("pdftotext", pdftotextVersion);
        validatePopplerVersion("pdftoppm", pdftoppmVersion);

        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        Path finalDirectory = normalizedOutputRoot.resolve(manifestSha256);
        Path stagingDirectory =
                normalizedOutputRoot.resolve(
                        "." + manifestSha256 + ".staging-" + UUID.randomUUID());
        try {
            Files.createDirectories(stagingDirectory);
            List<ConvertedBook> convertedBooks =
                    convertBooks(
                            books,
                            manifestSha256,
                            stagingDirectory);
            ContentBatch batch =
                    new ContentBatch(
                            manifest.contentVersion(),
                            manifestSha256,
                            List.copyOf(convertedBooks));
            validateConvertedBatch(batch);
            writeResultManifest(
                    stagingDirectory,
                    new ContentResultManifest(
                            batch.contentVersion(),
                            manifestSha256,
                            pdftotextVersion,
                            pdftoppmVersion,
                            new ImageConversion("JPEG", 150, 85),
                            batch.books()));
            publish(stagingDirectory, finalDirectory, batch);
            return batch;
        } catch (IOException exception) {
            throw new IllegalStateException("콘텐츠 변환 산출물을 준비할 수 없습니다.", exception);
        } finally {
            deleteRecursively(stagingDirectory);
        }
    }

    /**
     * 콘텐츠 버전별 manifest 계약을 검사하고 변환에 필요한 값만 뽑는다.
     *
     * <p>변환은 도서마다 PDF 경로·해시·페이지 수만 있으면 되므로, 버전에 따라 다른 것은 계약 검사와
     * 이 값을 꺼내는 방법뿐이다.
     */
    private List<SourceBook> validateAndCollect(ContentManifest manifest) {
        return switch (manifest) {
            case InitialContentManifest initial -> collectInitial(initial);
            case AiRouteContentManifest aiRoute -> collectAiRoute(aiRoute);
        };
    }

    /** 초기 fixture는 100권·400페이지와 고정 PDF 경로가 계약이다. */
    private List<SourceBook> collectInitial(InitialContentManifest manifest) {
        if (manifest.books().size() != BOOK_COUNT) {
            throw new IllegalStateException("콘텐츠 manifest에는 정확히 100권이 있어야 합니다.");
        }

        List<SourceBook> books = new ArrayList<>(BOOK_COUNT);
        int totalPageCount = 0;
        for (InitialContentManifest.Book book : manifest.books()) {
            if (book.bookId() < 1
                    || book.bookId() > BOOK_COUNT
                    || !expectedPdfPath(book.bookId()).equals(book.pdfPath())) {
                throw new IllegalStateException("콘텐츠 manifest에 유효하지 않은 도서가 있습니다.");
            }
            totalPageCount += book.totalPageCount();
            books.add(
                    new SourceBook(
                            book.bookId(),
                            book.pdfPath(),
                            book.pdfSha256(),
                            book.totalPageCount()));
        }
        if (totalPageCount != PAGE_COUNT) {
            throw new IllegalStateException("콘텐츠 manifest의 전체 페이지 수는 400이어야 합니다.");
        }
        return books;
    }

    /**
     * `ai-route-v2`는 권수를 세지 않는다. 확장 중의 부분 집합도 변환할 수 있어야 하므로 manifest에
     * 든 도서를 그대로 가져간다.
     *
     * <p>도서마다의 페이지 수·중복 bookId는 {@code ContentManifestFormatValidator}가 파싱 단계에서
     * 이미 거부하고, 페이지 메타데이터·선수 그래프·평가 연결은 C02 검증기가 본다. 여기서 다시 보지
     * 않는다. 변환이 직접 확인할 것은 변환할 도서가 하나라도 있다는 것뿐이다.
     */
    private List<SourceBook> collectAiRoute(AiRouteContentManifest manifest) {
        if (manifest.books().isEmpty()) {
            throw new IllegalStateException("AI 경로 manifest에 도서가 없습니다.");
        }
        List<SourceBook> books = new ArrayList<>(manifest.books().size());
        for (AiRouteContentManifest.Book book : manifest.books()) {
            books.add(
                    new SourceBook(
                            book.bookId(),
                            book.pdfPath(),
                            book.pdfSha256(),
                            book.totalPageCount()));
        }
        return books;
    }

    private List<ResolvedBook> resolveAndVerifyBooks(List<SourceBook> sourceBooks) {
        Path manifestDirectory = manifestPath.toAbsolutePath().normalize().getParent();
        if (manifestDirectory == null) {
            throw new IllegalStateException("콘텐츠 manifest 상위 디렉터리를 확인할 수 없습니다.");
        }

        List<ResolvedBook> resolvedBooks = new ArrayList<>(sourceBooks.size());
        for (SourceBook book : sourceBooks.stream()
                .sorted(Comparator.comparingLong(SourceBook::bookId))
                .toList()) {
            Path pdfPath = manifestDirectory.resolve(book.pdfPath()).normalize();
            if (!pdfPath.startsWith(manifestDirectory) || !Files.isRegularFile(pdfPath)) {
                throw new IllegalStateException(
                        "콘텐츠 PDF 파일을 찾을 수 없습니다: " + book.pdfPath());
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
            List<ResolvedBook> books,
            String manifestSha256,
            Path stagingDirectory)
            throws IOException {
        List<ConvertedBook> convertedBooks = new ArrayList<>(books.size());
        for (ResolvedBook resolvedBook : books) {
            SourceBook book = resolvedBook.manifest();
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
                        bookStagingDirectory.resolve(
                                "page-%03d".formatted(pageNumber));
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
                                logicalImagePath(
                                        manifestSha256,
                                        book.bookId(),
                                        pageNumber),
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

    private void validateConvertedBatch(ContentBatch batch) {
        List<ConvertedPage> pages = batch.pages();
        // 권수·전체 페이지 수 계약은 초기 fixture에만 있다. ai-route-v2는 확장 중의 부분 집합도
        // 변환해야 하므로 도서별 페이지 수만 아래에서 검사한다.
        if (ContentManifest.INITIAL_CONTENT_VERSION.equals(batch.contentVersion())
                && (batch.books().size() != BOOK_COUNT || pages.size() != PAGE_COUNT)) {
            throw new IllegalStateException("변환 결과는 100권·400페이지여야 합니다.");
        }

        Set<PageKey> keys = new HashSet<>();
        for (ConvertedBook book : batch.books()) {
            if (book.pages().size() != book.totalPageCount()) {
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

    private void writeResultManifest(
            Path stagingDirectory, ContentResultManifest resultManifest)
            throws IOException {
        byte[] result =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(resultManifest);
        Files.write(stagingDirectory.resolve("manifest.json"), result);
    }

    private void publish(
            Path stagingDirectory, Path finalDirectory, ContentBatch batch)
            throws IOException {
        if (Files.exists(finalDirectory)) {
            verifyExistingBatch(stagingDirectory, finalDirectory, batch);
            return;
        }
        try {
            Files.move(stagingDirectory, finalDirectory, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(stagingDirectory, finalDirectory);
        }
    }

    private void verifyExistingBatch(
            Path stagingDirectory, Path finalDirectory, ContentBatch batch)
            throws IOException {
        Path expectedManifest = stagingDirectory.resolve("manifest.json");
        Path existingManifest = finalDirectory.resolve("manifest.json");
        if (!Files.isRegularFile(existingManifest)
                || Files.mismatch(expectedManifest, existingManifest) != -1) {
            throw new IllegalStateException(
                    "같은 manifest 배치 디렉터리에 다른 결과가 있습니다: "
                            + finalDirectory);
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
                        "같은 manifest 배치 디렉터리에 다른 이미지가 있습니다: "
                                + relativeImage);
            }
        }
    }

    private String logicalImagePath(
            String manifestSha256, long bookId, int pageNumber) {
        return outputRoot
                .resolve(manifestSha256)
                .resolve("book-%03d".formatted(bookId))
                .resolve("page-%03d.jpg".formatted(pageNumber))
                .normalize()
                .toString()
                .replace(File.separatorChar, '/');
    }

    private void validatePopplerVersion(String command, String actualVersion) {
        if (!POPPLER_VERSIONS.contains(actualVersion)) {
            throw new IllegalStateException(
                    command
                            + " 버전은 "
                            + String.join("·", POPPLER_VERSIONS)
                            + " 중 하나여야 합니다: "
                            + actualVersion);
        }
    }

    private String expectedPdfPath(long bookId) {
        return "pdfs/book-%03d.pdf".formatted(bookId);
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
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
                    "임시 변환 산출물을 확인할 수 없습니다: " + directory,
                    exception);
        }
    }

    /** 콘텐츠 버전과 무관하게 변환에 필요한 값만 담는다. */
    private record SourceBook(
            long bookId, String pdfPath, String pdfSha256, int totalPageCount) {}

    private record ResolvedBook(SourceBook manifest, Path pdfPath) {}

    private record PageKey(long bookId, int pageNumber) {}
}
