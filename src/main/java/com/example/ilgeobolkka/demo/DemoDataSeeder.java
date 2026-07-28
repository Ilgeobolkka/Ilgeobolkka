package com.example.ilgeobolkka.demo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "demo", "test"})
class DemoDataSeeder {

    static final String RENTAL_READER_EMAIL = "reader-a@demo.ilgeobolkka.test";
    static final String EMPTY_READER_EMAIL = "reader-b@demo.ilgeobolkka.test";
    static final String OWNERSHIP_READER_EMAIL = "reader-c@demo.ilgeobolkka.test";

    private static final String INK_PAYMENT_ID = "00000000-0000-0000-0000-000000000101";
    private static final String OWNERSHIP_PAYMENT_ID =
            "00000000-0000-0000-0000-000000000201";
    private static final Instant SEED_TIME = Instant.parse("2026-07-28T00:00:00Z");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DemoBookCatalog demoBookCatalog;

    DemoDataSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            DemoBookCatalog demoBookCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.demoBookCatalog = demoBookCatalog;
    }

    @Transactional
    public void seed(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("시연 데이터 비밀번호는 비어 있을 수 없습니다.");
        }

        List<DemoBookCatalog.BookSeed> books = demoBookCatalog.books();
        ensureBooks(books);
        ensureBookPages(books);

        Map<String, StoredReader> readers = findDemoReaders();
        if (readers.isEmpty()) {
            createDemoReaders(rawPassword, books.getFirst());
            return;
        }
        if (readers.size() != 3) {
            throw new IllegalStateException("시연 계정이 일부만 존재하여 자동 시드를 중단합니다.");
        }

        verifyExistingSeedState(readers);
        updatePasswords(readers, rawPassword);
    }

    private void ensureBooks(List<DemoBookCatalog.BookSeed> expectedBooks) {
        Map<Long, StoredBook> storedBooks = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT id, category, title, author, description, cover_image_path,
                               total_page_count, price_won
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                        """,
                        (resultSet, rowNumber) ->
                                new StoredBook(
                                        resultSet.getLong("id"),
                                        resultSet.getString("category"),
                                        resultSet.getString("title"),
                                        resultSet.getString("author"),
                                        resultSet.getString("description"),
                                        resultSet.getString("cover_image_path"),
                                        resultSet.getInt("total_page_count"),
                                        resultSet.getInt("price_won")))
                .forEach(book -> storedBooks.put(book.id(), book));

        List<DemoBookCatalog.BookSeed> missingBooks = new ArrayList<>();
        for (DemoBookCatalog.BookSeed expectedBook : expectedBooks) {
            StoredBook storedBook = storedBooks.get(expectedBook.id());
            if (storedBook == null) {
                missingBooks.add(expectedBook);
                continue;
            }
            if (!storedBook.matches(expectedBook)) {
                throw new IllegalStateException(
                        "시연 도서 ID가 다른 데이터와 충돌합니다: " + expectedBook.id());
            }
        }

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                missingBooks,
                100,
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
    }

    private void ensureBookPages(List<DemoBookCatalog.BookSeed> books) {
        Map<BookPageKey, StoredBookPage> storedPages = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT book_id, page_number, content_type, text_content, image_path
                        FROM book_page
                        WHERE book_id BETWEEN 1 AND 100
                        """,
                        (resultSet, rowNumber) ->
                                new StoredBookPage(
                                        resultSet.getLong("book_id"),
                                        resultSet.getInt("page_number"),
                                        resultSet.getString("content_type"),
                                        resultSet.getString("text_content"),
                                        resultSet.getString("image_path")))
                .forEach(page -> storedPages.put(page.key(), page));

        Map<BookPageKey, ExpectedBookPage> expectedPages = new HashMap<>();
        for (DemoBookCatalog.BookSeed book : books) {
            for (DemoBookCatalog.PageSeed page : book.pages()) {
                ExpectedBookPage expectedPage = new ExpectedBookPage(book.id(), page);
                expectedPages.put(expectedPage.key(), expectedPage);
            }
        }

        for (StoredBookPage storedPage : storedPages.values()) {
            ExpectedBookPage expectedPage = expectedPages.get(storedPage.key());
            if (expectedPage == null || !storedPage.matches(expectedPage)) {
                throw new IllegalStateException(
                        "시연 도서 페이지가 다른 데이터와 충돌합니다: "
                                + storedPage.bookId()
                                + "-"
                                + storedPage.pageNumber());
            }
        }

        List<ExpectedBookPage> missingPages =
                expectedPages.values().stream()
                        .filter(page -> !storedPages.containsKey(page.key()))
                        .toList();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, ?, ?, ?)
                """,
                missingPages,
                100,
                (statement, page) -> {
                    statement.setLong(1, page.bookId());
                    statement.setInt(2, page.page().pageNumber());
                    statement.setString(3, page.page().contentType());
                    statement.setString(4, page.page().textContent());
                    statement.setString(5, page.page().imagePath());
                });
    }

    private Map<String, StoredReader> findDemoReaders() {
        Map<String, StoredReader> readers = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT id, email, password_hash
                        FROM reader
                        WHERE email IN (?, ?, ?)
                        """,
                        (resultSet, rowNumber) ->
                                new StoredReader(
                                        resultSet.getLong("id"),
                                        resultSet.getString("email"),
                                        resultSet.getString("password_hash")),
                        RENTAL_READER_EMAIL,
                        EMPTY_READER_EMAIL,
                        OWNERSHIP_READER_EMAIL)
                .forEach(reader -> readers.put(reader.email(), reader));
        return readers;
    }

    private void createDemoReaders(String rawPassword, DemoBookCatalog.BookSeed ownershipBook) {
        long rentalReaderId = createReader(RENTAL_READER_EMAIL, rawPassword);
        long emptyReaderId = createReader(EMPTY_READER_EMAIL, rawPassword);
        long ownershipReaderId = createReader(OWNERSHIP_READER_EMAIL, rawPassword);

        createInkAccount(rentalReaderId, 100);
        createInkAccount(emptyReaderId, 0);
        createInkAccount(ownershipReaderId, 0);
        createInkGrant(rentalReaderId);
        createOwnership(ownershipReaderId, ownershipBook);
    }

    private long createReader(String email, String rawPassword) {
        jdbcTemplate.update(
                "INSERT INTO reader (email, password_hash, created_at) VALUES (?, ?, ?)",
                email,
                passwordEncoder.encode(rawPassword),
                Timestamp.from(SEED_TIME));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM reader WHERE email = ?", Long.class, email);
    }

    private void createInkAccount(long readerId, int balance) {
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, ?)", readerId, balance);
    }

    private void createInkGrant(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (reader_id, payment_id, status, amount_won, granted_ink, created_at, paid_at)
                VALUES (?, ?, 'PAID', 1000, 100, ?, ?)
                """,
                readerId,
                INK_PAYMENT_ID,
                Timestamp.from(SEED_TIME),
                Timestamp.from(SEED_TIME));
        long inkPurchaseId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ink_purchase WHERE payment_id = ?",
                        Long.class,
                        INK_PAYMENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (reader_id, type, amount, balance_after, ink_purchase_id, occurred_at)
                VALUES (?, 'GRANT', 100, 100, ?, ?)
                """,
                readerId,
                inkPurchaseId,
                Timestamp.from(SEED_TIME));
    }

    private void createOwnership(long readerId, DemoBookCatalog.BookSeed book) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', ?, ?, ?)
                """,
                readerId,
                book.id(),
                OWNERSHIP_PAYMENT_ID,
                book.priceWon(),
                Timestamp.from(SEED_TIME),
                Timestamp.from(SEED_TIME));
        long ownershipPaymentId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ownership_payment WHERE payment_id = ?",
                        Long.class,
                        OWNERSHIP_PAYMENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?)
                """,
                readerId,
                book.id(),
                ownershipPaymentId,
                Timestamp.from(SEED_TIME));
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, 1, ?)
                """,
                readerId,
                book.id(),
                Timestamp.from(SEED_TIME));
    }

    private void verifyExistingSeedState(Map<String, StoredReader> readers) {
        int inkAccountCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_account
                        WHERE reader_id IN (?, ?, ?)
                        """,
                        Integer.class,
                        readers.get(RENTAL_READER_EMAIL).id(),
                        readers.get(EMPTY_READER_EMAIL).id(),
                        readers.get(OWNERSHIP_READER_EMAIL).id());
        int grantCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_ledger il
                        JOIN ink_purchase ip ON ip.id = il.ink_purchase_id
                        WHERE ip.payment_id = ? AND il.type = 'GRANT'
                        """,
                        Integer.class,
                        INK_PAYMENT_ID);
        int ownershipCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book_ownership bo
                        JOIN ownership_payment op ON op.id = bo.ownership_payment_id
                        WHERE op.payment_id = ? AND bo.book_id = 1
                        """,
                        Integer.class,
                        OWNERSHIP_PAYMENT_ID);
        int libraryCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM library_entry
                        WHERE reader_id = ? AND book_id = 1
                        """,
                        Integer.class,
                        readers.get(OWNERSHIP_READER_EMAIL).id());

        if (inkAccountCount != 3
                || grantCount != 1
                || ownershipCount != 1
                || libraryCount != 1) {
            throw new IllegalStateException("기존 시연 데이터가 불완전하여 자동 시드를 중단합니다.");
        }
    }

    private void updatePasswords(Map<String, StoredReader> readers, String rawPassword) {
        for (StoredReader reader : readers.values()) {
            if (passwordEncoder.matches(rawPassword, reader.passwordHash())) {
                continue;
            }
            jdbcTemplate.update(
                    "UPDATE reader SET password_hash = ? WHERE id = ?",
                    passwordEncoder.encode(rawPassword),
                    reader.id());
        }
    }

    private record StoredBook(
            long id,
            String category,
            String title,
            String author,
            String description,
            String coverImagePath,
            int totalPageCount,
            int priceWon) {

        boolean matches(DemoBookCatalog.BookSeed expected) {
            return id == expected.id()
                    && Objects.equals(category, expected.category())
                    && Objects.equals(title, expected.title())
                    && Objects.equals(author, expected.author())
                    && Objects.equals(description, expected.description())
                    && Objects.equals(coverImagePath, expected.coverImagePath())
                    && totalPageCount == expected.totalPageCount()
                    && priceWon == expected.priceWon();
        }
    }

    private record StoredBookPage(
            long bookId,
            int pageNumber,
            String contentType,
            String textContent,
            String imagePath) {

        BookPageKey key() {
            return new BookPageKey(bookId, pageNumber);
        }

        boolean matches(ExpectedBookPage expected) {
            return Objects.equals(contentType, expected.page().contentType())
                    && Objects.equals(textContent, expected.page().textContent())
                    && Objects.equals(imagePath, expected.page().imagePath());
        }
    }

    private record ExpectedBookPage(long bookId, DemoBookCatalog.PageSeed page) {

        BookPageKey key() {
            return new BookPageKey(bookId, page.pageNumber());
        }
    }

    private record BookPageKey(long bookId, int pageNumber) {}

    private record StoredReader(long id, String email, String passwordHash) {}
}
