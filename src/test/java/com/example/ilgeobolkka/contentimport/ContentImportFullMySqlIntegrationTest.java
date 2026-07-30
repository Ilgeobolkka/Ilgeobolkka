package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class ContentImportFullMySqlIntegrationTest {

    private final ContentPageWriter contentPageWriter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    ContentImportFullMySqlIntegrationTest(
            ContentPageWriter contentPageWriter,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.contentPageWriter = contentPageWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_CONTENT_IMPORT_INTEGRATION",
            matches = "true")
    void 실제_PDF_100권을_Poppler로_변환하고_MySQL에_400페이지를_적재한다()
            throws IOException {
        BookMetadata[] books = loadBooks();
        insertBooks(books);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES (1, 1, 'TEXT', '기존 본문', NULL)
                """);
        Long existingPageId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM book_page
                        WHERE book_id = 1 AND page_number = 1
                        """,
                        Long.class);

        ContentImportProperties properties = new ContentImportProperties();
        properties.setManifest(Path.of("fixtures/content/manifest.json"));
        properties.setOutputRoot(Path.of("var/content/pages"));
        properties.setPdftotextCommand(
                System.getenv().getOrDefault("PDFTOTEXT_COMMAND", "pdftotext"));
        properties.setPdftoppmCommand(
                System.getenv().getOrDefault("PDFTOPPM_COMMAND", "pdftoppm"));
        PdfTool pdfTool = new PopplerPdfTool(properties);
        ContentBatchConverter converter =
                new ContentBatchConverter(
                        properties.manifest(),
                        properties.outputRoot(),
                        objectMapper,
                        pdfTool);

        ContentBatch batch = converter.convert();
        verifySourceContent(batch, books);
        contentPageWriter.write(batch);

        Integer pageCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book_page WHERE book_id BETWEEN 1 AND 100",
                        Integer.class);
        Integer imagePageCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book_page
                        WHERE book_id BETWEEN 1 AND 100
                          AND content_type = 'IMAGE'
                          AND text_content IS NULL
                          AND image_path IS NOT NULL
                        """,
                        Integer.class);
        Integer textPageCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book_page
                        WHERE book_id BETWEEN 1 AND 100
                          AND content_type = 'TEXT'
                          AND text_content IS NOT NULL
                          AND image_path IS NULL
                        """,
                        Integer.class);
        Long updatedPageId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM book_page
                        WHERE book_id = 1 AND page_number = 1
                        """,
                        Long.class);
        assertAll(
                () -> assertEquals(400, pageCount),
                () -> assertEquals(100, imagePageCount),
                () -> assertEquals(300, textPageCount),
                () -> assertEquals(existingPageId, updatedPageId),
                () ->
                        assertTrue(
                                Files.isRegularFile(
                                        Path.of(
                                                "var/content/pages",
                                                batch.manifestSha256(),
                                                "manifest.json"))));
    }

    private void verifySourceContent(ContentBatch batch, BookMetadata[] books) {
        for (ConvertedBook convertedBook : batch.books()) {
            BookMetadata book = books[(int) convertedBook.bookId() - 1];
            for (ConvertedPage page : convertedBook.pages()) {
                if (page.pageNumber() == 2) {
                    assertEquals(BookPageContentType.IMAGE, page.contentType());
                    continue;
                }
                assertEquals(BookPageContentType.TEXT, page.contentType());
                assertEquals(
                        normalizeSpaces(
                                book.title()
                                        + " "
                                        + book.author()
                                        + "이 작성한 가상 본문의 "
                                        + page.pageNumber()
                                        + "페이지입니다. "
                                        + "이 문장은 원본 페이지 번호와 텍스트 순서 검증을 위한 결정적 시드입니다."),
                        normalizeSpaces(page.textContent()));
            }
        }
    }

    private String normalizeSpaces(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    private BookMetadata[] loadBooks() throws IOException {
        try (InputStream inputStream =
                Files.newInputStream(Path.of("src/main/resources/demo/books.json"))) {
            return objectMapper.readValue(inputStream, BookMetadata[].class);
        }
    }

    private void insertBooks(BookMetadata[] books) {
        List<Object[]> rows = new ArrayList<>();
        for (BookMetadata book : books) {
            rows.add(
                    new Object[] {
                        book.id(),
                        book.category(),
                        book.title(),
                        book.author(),
                        book.description(),
                        book.coverImagePath(),
                        book.totalPageCount(),
                        book.priceWon()
                    });
        }
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows);
    }

    private record BookMetadata(
            long id,
            String category,
            String title,
            String author,
            String description,
            String coverImagePath,
            int totalPageCount,
            int priceWon) {}
}
