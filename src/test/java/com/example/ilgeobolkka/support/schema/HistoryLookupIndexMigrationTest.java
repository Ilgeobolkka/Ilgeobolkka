package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class HistoryLookupIndexMigrationTest {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    HistoryLookupIndexMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 이력_조회_인덱스는_필터와_최신순_정렬을_지원한다() {
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "reader_id:A",
                                        "book_page_id:A",
                                        "rented_at:D",
                                        "id:D"),
                                인덱스_컬럼을_조회한다(
                                        "page_rental", "idx_page_rental_reader_page_latest")),
                () ->
                        assertEquals(
                                List.of("reader_id:A", "occurred_at:D", "id:D"),
                                인덱스_컬럼을_조회한다(
                                        "ink_ledger", "idx_ink_ledger_reader_occurred")));
    }

    private List<String> 인덱스_컬럼을_조회한다(String tableName, String indexName) {
        return jdbcTemplate.queryForList(
                """
                SELECT CONCAT(column_name, ':', collation)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                ORDER BY seq_in_index
                """,
                String.class,
                tableName,
                indexName);
    }
}
