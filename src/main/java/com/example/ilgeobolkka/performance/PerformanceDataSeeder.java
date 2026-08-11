package com.example.ilgeobolkka.performance;

import com.example.ilgeobolkka.auth.dto.LoginAuthRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
final class PerformanceDataSeeder {

    private static final List<String> RESET_TABLES = List.of(
            "ai_route_current",
            "ai_reading_route_item",
            "ai_route_generation_item",
            "ai_route_generation",
            "ai_reading_route",
            "ai_route_prerequisite",
            "ai_route_daily_usage",
            "reading_session",
            "ink_ledger",
            "book_ownership",
            "library_entry",
            "page_rental",
            "ink_purchase",
            "ownership_payment",
            "ink_account",
            "book_page",
            "book",
            "reader");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PerformanceDatabaseGuard databaseGuard;
    private final PerformanceDatasetPlan datasetPlan;

    PerformanceDataSeeder(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            PerformanceDatabaseGuard databaseGuard,
            PerformanceDatasetPlan datasetPlan) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.databaseGuard = databaseGuard;
        this.datasetPlan = datasetPlan;
    }

    SeedSummary resetMvp(String rawPassword) {
        databaseGuard.requirePerformanceDatabase(dataSource);
        validatePassword(rawPassword);

        String passwordHash = reusablePasswordHash(rawPassword);
        PerformanceDatasetPlan.Dataset dataset = datasetPlan.create(passwordHash);

        truncatePerformanceTables();
        insertBooks(dataset.books());
        insertPages(dataset.pages());
        insertReaders(dataset.readers());
        insertInkAccounts(dataset.inkAccounts());
        insertInkPurchases(dataset.inkPurchases());
        insertPageRentals(dataset.pageRentals());
        insertInkLedgers(dataset.inkLedgers());
        insertOwnershipPayments(dataset.ownershipPayments());
        insertBookOwnerships(dataset.bookOwnerships());
        insertLibraryEntries(dataset.libraryEntries());

        return verifyExpectedCounts();
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.isBlank()
                || !new LoginAuthRequest("performance@example.com", rawPassword).isPasswordValid()) {
            throw new IllegalArgumentException("성능 계정 비밀번호가 제품 정책에 맞지 않습니다.");
        }
    }

    private String reusablePasswordHash(String rawPassword) {
        List<String> storedHashes = jdbcTemplate.queryForList(
                """
                SELECT password_hash
                FROM reader
                WHERE email = 'perf-reader-0001@perf.ilgeobolkka.test'
                """,
                String.class);
        if (!storedHashes.isEmpty() && passwordEncoder.matches(rawPassword, storedHashes.getFirst())) {
            return storedHashes.getFirst();
        }
        return passwordEncoder.encode(rawPassword);
    }

    private void truncatePerformanceTables() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            truncateTables(connection);
            return null;
        });
    }

    private void truncateTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : RESET_TABLES) {
                    statement.execute("TRUNCATE TABLE " + table);
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private void insertBooks(List<PerformanceDatasetPlan.BookSeed> books) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won, content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'initial-v1', FALSE, FALSE, NULL)
                """,
                books,
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

    private void insertPages(List<PerformanceDatasetPlan.PageSeed> pages) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, ?, 'TEXT', ?, NULL)
                """,
                pages,
                400,
                (statement, page) -> {
                    statement.setLong(1, page.id());
                    statement.setLong(2, page.bookId());
                    statement.setInt(3, page.pageNumber());
                    statement.setString(4, page.textContent());
                });
    }

    private void insertReaders(List<PerformanceDatasetPlan.ReaderSeed> readers) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, ?, ?)
                """,
                readers,
                500,
                (statement, reader) -> {
                    statement.setLong(1, reader.id());
                    statement.setString(2, reader.email());
                    statement.setString(3, reader.passwordHash());
                    statement.setObject(4, reader.createdAt());
                });
    }

    private void insertInkAccounts(List<PerformanceDatasetPlan.InkAccountSeed> accounts) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO ink_account (id, reader_id, balance) VALUES (?, ?, ?)",
                accounts,
                500,
                (statement, account) -> {
                    statement.setLong(1, account.id());
                    statement.setLong(2, account.readerId());
                    statement.setInt(3, account.balance());
                });
    }

    private void insertInkPurchases(List<PerformanceDatasetPlan.InkPurchaseSeed> purchases) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', 1000, 100, ?, ?)
                """,
                purchases,
                500,
                (statement, purchase) -> {
                    statement.setLong(1, purchase.id());
                    statement.setLong(2, purchase.readerId());
                    statement.setString(3, purchase.paymentId());
                    statement.setObject(4, purchase.createdAt());
                    statement.setObject(5, purchase.createdAt());
                });
    }

    private void insertPageRentals(List<PerformanceDatasetPlan.PageRentalSeed> rentals) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rentals,
                500,
                (statement, rental) -> {
                    statement.setLong(1, rental.id());
                    statement.setLong(2, rental.readerId());
                    statement.setLong(3, rental.bookPageId());
                    statement.setObject(4, rental.rentedAt());
                    statement.setObject(5, rental.expiresAt());
                });
    }

    private void insertInkLedgers(List<PerformanceDatasetPlan.InkLedgerSeed> ledgers) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, page_rental_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ledgers,
                500,
                (statement, ledger) -> {
                    statement.setLong(1, ledger.id());
                    statement.setLong(2, ledger.readerId());
                    statement.setString(3, ledger.type());
                    statement.setInt(4, ledger.amount());
                    statement.setInt(5, ledger.balanceAfter());
                    statement.setObject(6, ledger.inkPurchaseId());
                    statement.setObject(7, ledger.pageRentalId());
                    statement.setObject(8, ledger.occurredAt());
                });
    }

    private void insertOwnershipPayments(
            List<PerformanceDatasetPlan.OwnershipPaymentSeed> payments) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status,
                     amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 10000, ?, ?)
                """,
                payments,
                500,
                (statement, payment) -> {
                    statement.setLong(1, payment.id());
                    statement.setLong(2, payment.readerId());
                    statement.setLong(3, payment.bookId());
                    statement.setString(4, payment.paymentId());
                    statement.setObject(5, payment.createdAt());
                    statement.setObject(6, payment.createdAt());
                });
    }

    private void insertBookOwnerships(
            List<PerformanceDatasetPlan.BookOwnershipSeed> ownerships) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                ownerships,
                500,
                (statement, ownership) -> {
                    statement.setLong(1, ownership.id());
                    statement.setLong(2, ownership.readerId());
                    statement.setLong(3, ownership.bookId());
                    statement.setLong(4, ownership.ownershipPaymentId());
                    statement.setObject(5, ownership.createdAt());
                });
    }

    private void insertLibraryEntries(List<PerformanceDatasetPlan.LibraryEntrySeed> entries) {
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO library_entry
                    (id, reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                entries,
                500,
                (statement, entry) -> {
                    statement.setLong(1, entry.id());
                    statement.setLong(2, entry.readerId());
                    statement.setLong(3, entry.bookId());
                    statement.setInt(4, entry.lastPageNumber());
                    statement.setObject(5, entry.updatedAt());
                });
    }

    private SeedSummary verifyExpectedCounts() {
        Map<String, Integer> expectedCounts = new LinkedHashMap<>();
        expectedCounts.put("book", PerformanceDatasetPlan.BOOK_COUNT);
        expectedCounts.put(
                "book_page",
                PerformanceDatasetPlan.BOOK_COUNT * PerformanceDatasetPlan.PAGES_PER_BOOK);
        expectedCounts.put("reader", PerformanceDatasetPlan.READER_COUNT);
        expectedCounts.put("ink_account", PerformanceDatasetPlan.READER_COUNT);
        expectedCounts.put("ink_purchase", PerformanceDatasetPlan.READER_COUNT);
        expectedCounts.put(
                "ink_ledger",
                PerformanceDatasetPlan.READER_COUNT + PerformanceDatasetPlan.ACTIVE_READER_COUNT);
        expectedCounts.put("page_rental", PerformanceDatasetPlan.ACTIVE_READER_COUNT);
        expectedCounts.put("ownership_payment", PerformanceDatasetPlan.OWNED_READER_COUNT);
        expectedCounts.put("book_ownership", PerformanceDatasetPlan.OWNED_READER_COUNT);
        expectedCounts.put(
                "library_entry",
                PerformanceDatasetPlan.ACTIVE_READER_COUNT
                        + PerformanceDatasetPlan.OWNED_READER_COUNT);
        expectedCounts.put("reading_session", 0);

        Map<String, Integer> actualCounts = new LinkedHashMap<>();
        expectedCounts.forEach((table, expected) -> {
            Integer actual = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table, Integer.class);
            actualCounts.put(table, actual);
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "%s 예상 행 수가 다릅니다: expected=%d, actual=%d"
                                .formatted(table, expected, actual));
            }
        });

        Integer balanceMismatchCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT account.reader_id,
                           account.balance,
                           SUM(CASE ledger.type
                               WHEN 'GRANT' THEN ledger.amount
                               WHEN 'DEDUCTION' THEN -ledger.amount
                               ELSE 0 END) AS ledger_balance
                    FROM ink_account account
                    JOIN ink_ledger ledger ON ledger.reader_id = account.reader_id
                    GROUP BY account.reader_id, account.balance
                ) balance_state
                WHERE balance <> ledger_balance
                """,
                Integer.class);
        if (balanceMismatchCount != 0) {
            throw new IllegalStateException("성능 데이터의 잉크 잔액과 원장이 일치하지 않습니다.");
        }

        return new SeedSummary(Map.copyOf(actualCounts), balanceMismatchCount);
    }

    record SeedSummary(Map<String, Integer> rowCounts, int balanceMismatchCount) {
    }
}
