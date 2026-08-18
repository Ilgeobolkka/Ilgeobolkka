package com.example.ilgeobolkka.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod & (local | demo | test | content-import)")
public class DemoBookWriter {

    private final JdbcTemplate jdbcTemplate;

    DemoBookWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureBooks(List<DemoBookCatalog.BookSeed> expectedBooks) {
        Map<Long, StoredImmutableBookMetadata> storedBooks = new HashMap<>();
        jdbcTemplate
                .query(
                        """
                        SELECT id, category, author, description, cover_image_path, price_won
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                        """,
                        (resultSet, rowNumber) ->
                                new StoredImmutableBookMetadata(
                                        resultSet.getLong("id"),
                                        resultSet.getString("category"),
                                        resultSet.getString("author"),
                                        resultSet.getString("description"),
                                        resultSet.getString("cover_image_path"),
                                        resultSet.getInt("price_won")))
                .forEach(book -> storedBooks.put(book.id(), book));

        List<DemoBookCatalog.BookSeed> missingBooks = new ArrayList<>();
        for (DemoBookCatalog.BookSeed expectedBook : expectedBooks) {
            StoredImmutableBookMetadata storedBook = storedBooks.get(expectedBook.id());
            if (storedBook == null) {
                missingBooks.add(expectedBook);
                continue;
            }
            if (!storedBook.matchesImmutableMetadata(expectedBook)) {
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

    private record StoredImmutableBookMetadata(
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
