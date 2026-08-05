package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("content-import")
class ContentBatchConverter {

    private static final int BOOK_COUNT = 100;
    private static final int PAGE_COUNT = 400;
    private static final String POPPLER_VERSION = "26.05.0";

    private final Path manifestPath;
    private final Path outputRoot;
    private final ObjectMapper objectMapper;
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
        this.pdfTool = pdfTool;
    }

    ContentBatch convert() {
        byte[] manifestBytes = readBytes(manifestPath);
        String manifestSha256 = sha256(manifestBytes);
        ContentManifest manifest = readManifest(manifestBytes);
        validateManifest(manifest);
        List<ResolvedBook> books = resolveAndVerifyBooks(manifest);

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
            ContentBatch batch = new ContentBatch(manifestSha256, List.copyOf(convertedBooks));
            validateConvertedBatch(batch);
            writeResultManifest(
                    stagingDirectory,
                    new ContentResultManifest(
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

    private ContentManifest readManifest(byte[] manifestBytes) {
        try {
            return objectMapper.readValue(manifestBytes, ContentManifest.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("콘텐츠 manifest를 읽을 수 없습니다.", exception);
        }
    }

    private void validateManifest(ContentManifest manifest) {
        if (manifest == null || manifest.books() == null || manifest.books().size() != BOOK_COUNT) {
            throw new IllegalStateException("콘텐츠 manifest에는 정확히 100권이 있어야 합니다.");
        }

        Set<Long> ids = new HashSet<>();
        int totalPageCount = 0;
        for (ManifestBook book : manifest.books()) {
            if (book == null
                    || book.bookId() < 1
                    || book.bookId() > BOOK_COUNT
                    || !ids.add(book.bookId())
                    || !expectedPdfPath(book.bookId()).equals(book.pdfPath())
                    || book.pdfSha256() == null
                    || !book.pdfSha256().matches("[0-9a-f]{64}")
                    || book.totalPageCount() < 1) {
                throw new IllegalStateException("콘텐츠 manifest에 유효하지 않은 도서가 있습니다.");
            }
            totalPageCount += book.totalPageCount();
        }
        if (totalPageCount != PAGE_COUNT) {
            throw new IllegalStateException("콘텐츠 manifest의 전체 페이지 수는 400이어야 합니다.");
        }
    }

    private List<ResolvedBook> resolveAndVerifyBooks(ContentManifest manifest) {
        Path manifestDirectory = manifestPath.toAbsolutePath().normalize().getParent();
        if (manifestDirectory == null) {
            throw new IllegalStateException("콘텐츠 manifest 상위 디렉터리를 확인할 수 없습니다.");
        }

        List<ResolvedBook> resolvedBooks = new ArrayList<>(BOOK_COUNT);
        for (ManifestBook book : manifest.books().stream()
                .sorted(Comparator.comparingLong(ManifestBook::bookId))
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
        List<ConvertedBook> convertedBooks = new ArrayList<>(BOOK_COUNT);
        for (ResolvedBook resolvedBook : books) {
            ManifestBook book = resolvedBook.manifest();
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
        if (batch.books().size() != BOOK_COUNT || pages.size() != PAGE_COUNT) {
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
        if (!POPPLER_VERSION.equals(actualVersion)) {
            throw new IllegalStateException(
                    command
                            + " 버전은 "
                            + POPPLER_VERSION
                            + "이어야 합니다: "
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

    private record ResolvedBook(ManifestBook manifest, Path pdfPath) {}

    private record PageKey(long bookId, int pageNumber) {}
}
