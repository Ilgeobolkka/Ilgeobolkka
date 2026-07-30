package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.demo.DemoBookCatalog;
import com.example.ilgeobolkka.demo.DemoBookWriter;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ContentPageWriterMySqlIntegrationTest {

    private final ContentPageWriter contentPageWriter;
    private final DemoBookCatalog demoBookCatalog;
    private final DemoBookWriter demoBookWriter;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ContentPageWriterMySqlIntegrationTest(
            ContentPageWriter contentPageWriter,
            DemoBookCatalog demoBookCatalog,
            DemoBookWriter demoBookWriter,
            JdbcTemplate jdbcTemplate) {
        this.contentPageWriter = contentPageWriter;
        this.demoBookCatalog = demoBookCatalog;
        this.demoBookWriter = demoBookWriter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        deleteCommittedFixture();
        demoBookWriter.ensureBooks(demoBookCatalog.books());
    }

    @AfterEach
    void tearDown() {
        deleteCommittedFixture();
    }

    @Test
    void 기존_페이지_ID를_보존해_갱신하고_없는_페이지를_추가한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES (1, 1, 'TEXT', '기존 본문', NULL)
                """);
        Long existingId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM book_page
                        WHERE book_id = 1 AND page_number = 1
                        """,
                        Long.class);

        contentPageWriter.write(ContentBatchTestFixture.demoPageCountBatch());

        Long updatedId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM book_page
                        WHERE book_id = 1 AND page_number = 1
                        """,
                        Long.class);
        String updatedText =
                jdbcTemplate.queryForObject(
                        """
                        SELECT text_content
                        FROM book_page
                        WHERE book_id = 1 AND page_number = 1
                        """,
                        String.class);
        Integer pageCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book_page WHERE book_id BETWEEN 1 AND 100",
                        Integer.class);
        assertAll(
                () -> assertEquals(existingId, updatedId),
                () -> assertEquals("도서 1의 1페이지", updatedText),
                () -> assertEquals(400, pageCount));
    }

    @Test
    void 예상하지_않은_기존_페이지가_있으면_삭제하거나_변경하지_않는다() {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES (1, 5, 'TEXT', '보존할 기존 본문', NULL)
                """);

        assertThrows(
                IllegalStateException.class,
                () -> contentPageWriter.write(ContentBatchTestFixture.demoPageCountBatch()));

        String textContent =
                jdbcTemplate.queryForObject(
                        """
                        SELECT text_content
                        FROM book_page
                        WHERE book_id = 1 AND page_number = 5
                        """,
                        String.class);
        Integer pageCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book_page WHERE book_id BETWEEN 1 AND 100",
                        Integer.class);
        assertAll(
                () -> assertEquals("보존할 기존 본문", textContent),
                () -> assertEquals(1, pageCount));
    }

    @Test
    void DB_적재_중_한_페이지라도_실패하면_전체_변경을_롤백한다() {
        ContentBatch invalidBatch = batchWithTooLongImagePath();

        assertThrows(
                DataAccessException.class,
                () -> contentPageWriter.write(invalidBatch));

        Integer pageCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book_page WHERE book_id BETWEEN 1 AND 100",
                        Integer.class);
        assertEquals(0, pageCount);
    }

    private ContentBatch batchWithTooLongImagePath() {
        ContentBatch validBatch = ContentBatchTestFixture.demoPageCountBatch();
        List<ConvertedBook> books = new ArrayList<>(validBatch.books());
        ConvertedBook lastBook = books.getLast();
        List<ConvertedPage> pages = new ArrayList<>(lastBook.pages());
        pages.set(
                3,
                new ConvertedPage(
                        100,
                        4,
                        BookPageContentType.IMAGE,
                        null,
                        "x".repeat(501),
                        10L));
        books.set(
                books.size() - 1,
                new ConvertedBook(
                        lastBook.bookId(),
                        lastBook.sourceSha256(),
                        lastBook.totalPageCount(),
                        List.copyOf(pages)));
        return new ContentBatch(validBatch.manifestSha256(), List.copyOf(books));
    }

    private void deleteCommittedFixture() {
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id BETWEEN 1 AND 100");
        jdbcTemplate.update("DELETE FROM book WHERE id BETWEEN 1 AND 100");
    }
}
