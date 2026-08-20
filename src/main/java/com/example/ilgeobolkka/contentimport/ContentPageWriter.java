package com.example.ilgeobolkka.contentimport;

import static com.example.ilgeobolkka.contentimport.manifest.ContentManifest.INITIAL_CONTENT_VERSION;

import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.demo.DemoBookCatalog;
import com.example.ilgeobolkka.demo.DemoBookWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!prod & (content-import | test)")
class ContentPageWriter {

    private static final int INITIAL_BOOK_COUNT = 100;
    private static final int INITIAL_PAGE_COUNT = 400;

    private final JdbcTemplate jdbcTemplate;
    private final DemoBookCatalog demoBookCatalog;
    private final DemoBookWriter demoBookWriter;
    private final ObjectMapper objectMapper;

    ContentPageWriter(
            JdbcTemplate jdbcTemplate,
            DemoBookCatalog demoBookCatalog,
            DemoBookWriter demoBookWriter,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.demoBookCatalog = demoBookCatalog;
        this.demoBookWriter = demoBookWriter;
        this.objectMapper = objectMapper;
    }

    /** 동결된 initial-v1 100권·400페이지 적재 경로. */
    @Transactional
    public void write(ContentBatch batch) {
        if (batch == null || !INITIAL_CONTENT_VERSION.equals(batch.contentVersion())) {
            throw new IllegalStateException("초기 콘텐츠 writer에는 initial-v1 batch가 필요합니다.");
        }
        Map<Long, Integer> expectedPageCounts = expectedPageCounts(batch);
        if (expectedPageCounts.size() != INITIAL_BOOK_COUNT
                || batch.pages().size() != INITIAL_PAGE_COUNT) {
            throw new IllegalStateException("적재 결과에는 정확히 100권·400페이지가 있어야 합니다.");
        }
        for (Long bookId : expectedPageCounts.keySet()) {
            if (bookId < 1 || bookId > INITIAL_BOOK_COUNT) {
                throw new IllegalStateException("적재 결과에 유효하지 않은 도서가 있습니다.");
            }
        }

        demoBookWriter.ensureBooks(demoBookCatalog.books());
        validateInitialBooks(expectedPageCounts);
        Map<PageKey, StoredPage> storedPages = loadStoredPages(expectedPageCounts.keySet());
        validateExistingPages(storedPages, batch);
        writePages(batch, storedPages);
    }

    /** Book·BookPage AI 필드·선수 관계를 한 트랜잭션으로 교체한다. */
    @Transactional
    public void write(AiRouteContentImportCommand command) {
        if (command == null) {
            throw new AiRouteContentImportException("AI 콘텐츠 적재 command가 필요합니다.");
        }
        ContentBatch batch = command.batch();
        Map<Long, Integer> expectedPageCounts = expectedPageCounts(batch);
        ensureAiBooks(booksInLockOrder(command.content()));
        Map<PageKey, StoredPage> storedPages = loadStoredPages(expectedPageCounts.keySet());
        validateExistingPages(storedPages, batch);

        updateAiBooks(command.content());
        writePages(batch, storedPages);
        updateAiPages(command);
        replacePrerequisites(command.content());
    }

    /**
     * manifest 기재 순서 대신 항상 bookId 오름차순으로 {@code book}·{@code ai_route_prerequisite} 행을
     * 잠근다. 겹치는 도서를 다른 순서로 나열한 manifest끼리 동시에 적재해도 잠금 순서가 엇갈리지 않는다.
     */
    private List<ValidatedAiRouteContent.ValidatedBook> booksInLockOrder(
            ValidatedAiRouteContent content) {
        return content.books().stream()
                .sorted(Comparator.comparingLong(ValidatedAiRouteContent.ValidatedBook::bookId))
                .toList();
    }

    private Map<Long, Integer> expectedPageCounts(ContentBatch batch) {
        if (batch == null || batch.books() == null) {
            throw new IllegalStateException("적재할 콘텐츠 batch가 필요합니다.");
        }

        Map<Long, Integer> expectedPageCounts = new HashMap<>();
        Set<PageKey> pageKeys = new HashSet<>();
        for (ConvertedBook book : batch.books()) {
            if (book == null
                    || book.bookId() < 1
                    || book.totalPageCount() < 1
                    || expectedPageCounts.put(book.bookId(), book.totalPageCount()) != null
                    || book.pages() == null
                    || book.pages().size() != book.totalPageCount()) {
                throw new IllegalStateException("적재 결과에 유효하지 않은 도서가 있습니다.");
            }
            for (int index = 0; index < book.pages().size(); index++) {
                ConvertedPage page = book.pages().get(index);
                if (page == null
                        || page.bookId() != book.bookId()
                        || page.pageNumber() != index + 1
                        || !pageKeys.add(new PageKey(page.bookId(), page.pageNumber()))) {
                    throw new IllegalStateException("적재 결과의 페이지 번호가 연속적이지 않습니다.");
                }
            }
        }
        return expectedPageCounts;
    }

    private void validateInitialBooks(Map<Long, Integer> expectedPageCounts) {
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

    private void ensureAiBooks(List<ValidatedAiRouteContent.ValidatedBook> requestedBooks) {
        Map<Long, DemoBookCatalog.BookSeed> seeds = new HashMap<>();
        demoBookCatalog.books().forEach(book -> seeds.put(book.id(), book));

        Map<Long, StoredAiBook> storedBooks = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT id, category, author, description, cover_image_path, price_won
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                        """,
                        (resultSet, rowNumber) ->
                                new StoredAiBook(
                                        resultSet.getLong("id"),
                                        resultSet.getString("category"),
                                        resultSet.getString("author"),
                                        resultSet.getString("description"),
                                        resultSet.getString("cover_image_path"),
                                        resultSet.getInt("price_won")))
                .forEach(book -> storedBooks.put(book.id(), book));

        List<DemoBookCatalog.BookSeed> missingBooks = new ArrayList<>();
        for (ValidatedAiRouteContent.ValidatedBook requested : requestedBooks) {
            DemoBookCatalog.BookSeed seed = seeds.get(requested.bookId());
            if (seed == null) {
                throw new AiRouteContentImportException(
                        "시연 도서 ID가 아닙니다: " + requested.bookId());
            }
            StoredAiBook stored = storedBooks.get(requested.bookId());
            if (stored == null) {
                missingBooks.add(seed);
                continue;
            }
            if (!stored.matchesImmutableMetadata(seed)) {
                throw new AiRouteContentImportException(
                        "시연 도서 ID가 다른 데이터와 충돌합니다: " + requested.bookId());
            }
        }

        int[][] counts =
                jdbcTemplate.batchUpdate(
                        """
                        INSERT INTO book
                            (id, category, title, author, description, cover_image_path,
                             total_page_count, price_won)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        missingBooks,
                        INITIAL_BOOK_COUNT,
                        (statement, book) -> {
                            statement.setLong(1, book.id());
                            statement.setString(2, book.category());
                            statement.setString(3, book.title());
                            statement.setString(4, book.author());
                            statement.setString(5, book.description());
                            statement.setString(6, book.coverImagePath());
                            statement.setInt(7, book.totalPageCount());
                            statement.setInt(8, book.priceWon());
                        });
        for (int[] batchCounts : counts) {
            requireSingleRowChanges(batchCounts, "도서 생성");
        }
    }

    private void updateAiBooks(ValidatedAiRouteContent content) {
        List<Object[]> updates = new ArrayList<>();
        for (ValidatedAiRouteContent.ValidatedBook book : booksInLockOrder(content)) {
            updates.add(
                    new Object[] {
                        book.title(),
                        book.totalPageCount(),
                        content.contentVersion(),
                        book.aiExternalTransferAllowed(),
                        content.dataPolicyVersion(),
                        book.bookId()
                    });
        }
        int[] counts =
                jdbcTemplate.batchUpdate(
                        """
                        UPDATE book
                        SET title = ?,
                            total_page_count = ?,
                            content_version = ?,
                            ai_route_supported = FALSE,
                            ai_external_transfer_allowed = ?,
                            ai_data_policy_version = ?
                        WHERE id = ?
                        """,
                        updates);
        requireSingleRowChanges(counts, "도서 AI 메타데이터");
    }

    private Map<PageKey, StoredPage> loadStoredPages(Set<Long> expectedBookIds) {
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
                .stream()
                .filter(page -> expectedBookIds.contains(page.bookId()))
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

    private void writePages(ContentBatch batch, Map<PageKey, StoredPage> storedPages) {
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
        requireSingleRowChanges(updateCounts, "페이지 본문");
        requireSingleRowChanges(insertCounts, "페이지 본문");
    }

    private void updateAiPages(AiRouteContentImportCommand command) {
        Map<Long, ValidatedAiRouteContent.ValidatedBook> metadataByBookId = new HashMap<>();
        command.content().books().forEach(book -> metadataByBookId.put(book.bookId(), book));

        List<Object[]> updates = new ArrayList<>();
        for (ConvertedBook convertedBook : command.batch().books()) {
            ValidatedAiRouteContent.ValidatedBook metadataBook =
                    metadataByBookId.get(convertedBook.bookId());
            Map<Integer, ValidatedAiRouteContent.ValidatedPage> metadataByPageNumber =
                    new HashMap<>();
            metadataBook.pages().forEach(page -> metadataByPageNumber.put(page.pageNumber(), page));
            for (ConvertedPage convertedPage : convertedBook.pages()) {
                ValidatedAiRouteContent.ValidatedPage metadata =
                        metadataByPageNumber.get(convertedPage.pageNumber());
                if (metadata == null) {
                    updates.add(
                            new Object[] {
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                convertedPage.bookId(),
                                convertedPage.pageNumber()
                            });
                    continue;
                }
                boolean candidate = metadata.aiRouteCandidatePage();
                List<Double> vector =
                        command.embedded()
                                .vectorOf(convertedPage.bookId(), convertedPage.pageNumber());
                updates.add(
                        new Object[] {
                            metadata.analysisText(),
                            metadata.publicGuideTopic(),
                            metadata.estimatedReadingSeconds(),
                            candidate ? command.content().embeddingModel() : null,
                            candidate ? command.content().embeddingDimensions() : null,
                            candidate ? writeJson(vector) : null,
                            writeJson(metadata.duplicateGroupKeys()),
                            candidate,
                            convertedPage.bookId(),
                            convertedPage.pageNumber()
                        });
            }
        }

        int[] counts =
                jdbcTemplate.batchUpdate(
                        """
                        UPDATE book_page
                        SET ai_analysis_text = ?,
                            ai_public_guide_topic = ?,
                            estimated_reading_seconds = ?,
                            embedding_model = ?,
                            embedding_dimensions = ?,
                            embedding_json = ?,
                            duplicate_group_keys = ?,
                            ai_route_candidate = ?
                        WHERE book_id = ? AND page_number = ?
                        """,
                        updates);
        requireSingleRowChanges(counts, "페이지 AI 메타데이터");
    }

    private void replacePrerequisites(ValidatedAiRouteContent content) {
        List<ValidatedAiRouteContent.ValidatedBook> books = booksInLockOrder(content);
        for (ValidatedAiRouteContent.ValidatedBook book : books) {
            jdbcTemplate.update(
                    "DELETE FROM ai_route_prerequisite WHERE book_id = ?", book.bookId());
        }

        List<Object[]> inserts = new ArrayList<>();
        for (ValidatedAiRouteContent.ValidatedBook book : books) {
            for (ValidatedAiRouteContent.PrerequisiteEdge edge : book.prerequisiteEdges()) {
                inserts.add(
                        new Object[] {
                            book.bookId(), edge.beforePageNumber(), edge.afterPageNumber()
                        });
            }
        }
        int[] counts =
                jdbcTemplate.batchUpdate(
                        """
                        INSERT INTO ai_route_prerequisite
                            (book_id, prerequisite_page_number, dependent_page_number)
                        VALUES (?, ?, ?)
                        """,
                        inserts);
        requireSingleRowChanges(counts, "선수 관계");
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private void requireSingleRowChanges(int[] counts, String what) {
        for (int count : counts) {
            if (count != 1) {
                throw new IllegalStateException(
                        what + " 적재 중 예상과 다른 행 수가 변경됐습니다.");
            }
        }
    }

    private record PageKey(long bookId, int pageNumber) {}

    private record StoredPage(long id, long bookId, int pageNumber) {

        PageKey key() {
            return new PageKey(bookId, pageNumber);
        }
    }

    private record StoredAiBook(
            long id,
            String category,
            String author,
            String description,
            String coverImagePath,
            int priceWon) {

        boolean matchesImmutableMetadata(DemoBookCatalog.BookSeed expected) {
            return id == expected.id()
                    && Objects.equals(category, expected.category())
                    && Objects.equals(author, expected.author())
                    && Objects.equals(description, expected.description())
                    && Objects.equals(coverImagePath, expected.coverImagePath())
                    && priceWon == expected.priceWon();
        }
    }
}
