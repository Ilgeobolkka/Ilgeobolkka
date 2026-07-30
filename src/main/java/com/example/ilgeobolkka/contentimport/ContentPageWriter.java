package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.demo.DemoBookCatalog;
import com.example.ilgeobolkka.demo.DemoBookWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!prod & (content-import | test)")
class ContentPageWriter {

    private static final int BOOK_COUNT = 100;
    private static final int PAGE_COUNT = 400;

    private final JdbcTemplate jdbcTemplate;
    private final DemoBookCatalog demoBookCatalog;
    private final DemoBookWriter demoBookWriter;

    ContentPageWriter(
            JdbcTemplate jdbcTemplate,
            DemoBookCatalog demoBookCatalog,
            DemoBookWriter demoBookWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.demoBookCatalog = demoBookCatalog;
        this.demoBookWriter = demoBookWriter;
    }

    @Transactional
    public void write(ContentBatch batch) {
        Map<Long, Integer> expectedPageCounts = expectedPageCounts(batch);
        demoBookWriter.ensureBooks(demoBookCatalog.books());
        validateBooks(expectedPageCounts);

        Map<PageKey, StoredPage> storedPages = loadStoredPages();
        validateExistingPages(storedPages, batch);

        List<Object[]> updates = new ArrayList<>();
        List<Object[]> inserts = new ArrayList<>();
        for (ConvertedPage page : batch.pages()) {
            StoredPage storedPage = storedPages.get(new PageKey(page.bookId(), page.pageNumber()));
            if (storedPage == null) {
                inserts.add(
                        new Object[] {
                            page.bookId(),
                            page.pageNumber(),
                            page.contentType().name(),
                            page.textContent(),
                            page.imagePath()
                        });
                continue;
            }
            updates.add(
                    new Object[] {
                        page.contentType().name(),
                        page.textContent(),
                        page.imagePath(),
                        storedPage.id()
                    });
        }

        int[] updateCounts =
                jdbcTemplate.batchUpdate(
                        """
                        UPDATE book_page
                        SET content_type = ?, text_content = ?, image_path = ?
                        WHERE id = ?
                        """,
                        updates);
        int[] insertCounts =
                jdbcTemplate.batchUpdate(
                        """
                        INSERT INTO book_page
                            (book_id, page_number, content_type, text_content, image_path)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        inserts);
        requireSingleRowChanges(updateCounts);
        requireSingleRowChanges(insertCounts);
    }

    private Map<Long, Integer> expectedPageCounts(ContentBatch batch) {
        if (batch == null || batch.books() == null || batch.books().size() != BOOK_COUNT) {
            throw new IllegalStateException("적재 결과에는 정확히 100권이 있어야 합니다.");
        }

        Map<Long, Integer> expectedPageCounts = new HashMap<>();
        Set<PageKey> pageKeys = new HashSet<>();
        int totalPageCount = 0;
        for (ConvertedBook book : batch.books()) {
            if (book.bookId() < 1
                    || book.bookId() > BOOK_COUNT
                    || expectedPageCounts.put(book.bookId(), book.totalPageCount()) != null
                    || book.pages() == null
                    || book.pages().size() != book.totalPageCount()) {
                throw new IllegalStateException("적재 결과에 유효하지 않은 도서가 있습니다.");
            }
            totalPageCount += book.totalPageCount();
            for (int index = 0; index < book.pages().size(); index++) {
                ConvertedPage page = book.pages().get(index);
                if (page.bookId() != book.bookId()
                        || page.pageNumber() != index + 1
                        || !pageKeys.add(new PageKey(page.bookId(), page.pageNumber()))) {
                    throw new IllegalStateException("적재 결과의 페이지 번호가 연속적이지 않습니다.");
                }
            }
        }
        if (totalPageCount != PAGE_COUNT || pageKeys.size() != PAGE_COUNT) {
            throw new IllegalStateException("적재 결과에는 정확히 400페이지가 있어야 합니다.");
        }
        return expectedPageCounts;
    }

    private void validateBooks(Map<Long, Integer> expectedPageCounts) {
        Map<Long, Integer> storedPageCounts = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT id, total_page_count
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                        """,
                        (resultSet, rowNumber) ->
                                Map.entry(
                                        resultSet.getLong("id"),
                                        resultSet.getInt("total_page_count")))
                .forEach(entry -> storedPageCounts.put(entry.getKey(), entry.getValue()));
        if (!storedPageCounts.equals(expectedPageCounts)) {
            throw new IllegalStateException(
                    "DB 도서와 콘텐츠 manifest의 도서·페이지 수가 일치하지 않습니다.");
        }
    }

    private Map<PageKey, StoredPage> loadStoredPages() {
        Map<PageKey, StoredPage> storedPages = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT id, book_id, page_number
                        FROM book_page
                        WHERE book_id BETWEEN 1 AND 100
                        """,
                        (resultSet, rowNumber) ->
                                new StoredPage(
                                        resultSet.getLong("id"),
                                        resultSet.getLong("book_id"),
                                        resultSet.getInt("page_number")))
                .forEach(page -> storedPages.put(page.key(), page));
        return storedPages;
    }

    private void validateExistingPages(
            Map<PageKey, StoredPage> storedPages, ContentBatch batch) {
        Set<PageKey> expectedKeys = new HashSet<>();
        batch.pages()
                .forEach(
                        page ->
                                expectedKeys.add(
                                        new PageKey(page.bookId(), page.pageNumber())));
        for (PageKey storedKey : storedPages.keySet()) {
            if (!expectedKeys.contains(storedKey)) {
                throw new IllegalStateException(
                        "예상하지 않은 기존 페이지가 있어 적재를 중단합니다: "
                                + storedKey.bookId()
                                + "-"
                                + storedKey.pageNumber());
            }
        }
    }

    private void requireSingleRowChanges(int[] updateCounts) {
        for (int updateCount : updateCounts) {
            if (updateCount != 1) {
                throw new IllegalStateException("페이지 적재 중 예상과 다른 행 수가 변경됐습니다.");
            }
        }
    }

    private record PageKey(long bookId, int pageNumber) {}

    private record StoredPage(long id, long bookId, int pageNumber) {

        PageKey key() {
            return new PageKey(bookId, pageNumber);
        }
    }
}
