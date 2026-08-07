package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
class AiRouteSchemaMigrationTest {

    private static final MigrationVersion AI_ROUTE_MIGRATION_VERSION =
            MigrationVersion.fromVersion("2");
    private static final long READER_ID = 51_000L;
    private static final long SECOND_READER_ID = 51_001L;
    private static final long BOOK_ID = 52_000L;
    private static final long SECOND_BOOK_ID = 52_001L;
    private static final long FIRST_PAGE_ID = 53_000L;
    private static final long SECOND_PAGE_ID = 53_001L;
    private static final String GENERATION_ID = "00000000-0000-0000-0000-000000051000";
    private static final String SECOND_GENERATION_ID = "00000000-0000-0000-0000-000000051001";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AiRouteSchemaMigrationTest(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void 테스트_스키마를_초기화한다() {
        최신_스키마로_복구한다();
    }

    @AfterEach
    void 테스트_스키마를_복구한다() {
        최신_스키마로_복구한다();
    }

    @Test
    void 빈_스키마에는_V1과_V2가_순서대로_적용된다() {
        Flyway flyway = 새_Flyway를_생성한다(AI_ROUTE_MIGRATION_VERSION);

        try {
            flyway.clean();

            int migrationCount = flyway.migrate().migrationsExecuted;

            assertAll(
                    () -> assertEquals(2, migrationCount),
                    () -> assertEquals(List.of("1", "2"), 적용된_버전을_조회한다()),
                    () -> assertEquals(7, AI_경로_테이블_수를_조회한다()));
        } finally {
            최신_스키마로_복구한다();
        }
    }

    @Test
    void V1_기존_도서는_V2에서_initial_v1로_backfill되고_기존_writer_기본값을_유지한다() {
        Flyway v1Flyway = 새_Flyway를_생성한다(MigrationVersion.fromVersion("1"));

        try {
            v1Flyway.clean();
            v1Flyway.migrate();
            도서를_생성한다(BOOK_ID);
            도서를_생성한다(SECOND_BOOK_ID);

            새_Flyway를_생성한다(AI_ROUTE_MIGRATION_VERSION).migrate();

            List<String> versions =
                    jdbcTemplate.queryForList(
                            "SELECT content_version FROM book ORDER BY id", String.class);
            List<Boolean> supportFlags =
                    jdbcTemplate.queryForList(
                            "SELECT ai_route_supported FROM book ORDER BY id", Boolean.class);
            List<Boolean> transferFlags =
                    jdbcTemplate.queryForList(
                            "SELECT ai_external_transfer_allowed FROM book ORDER BY id",
                            Boolean.class);

            도서를_생성한다(BOOK_ID + 2);

            assertAll(
                    () -> assertEquals(List.of("initial-v1", "initial-v1"), versions),
                    () -> assertEquals(List.of(false, false), supportFlags),
                    () -> assertEquals(List.of(false, false), transferFlags),
                    () ->
                            assertEquals(
                                    "initial-v1",
                                    jdbcTemplate.queryForObject(
                                            "SELECT content_version FROM book WHERE id = ?",
                                            String.class,
                                            BOOK_ID + 2)),
                    () ->
                            assertFalse(
                                    jdbcTemplate.queryForObject(
                                            "SELECT ai_route_supported FROM book WHERE id = ?",
                                            Boolean.class,
                                            BOOK_ID + 2)),
                    () ->
                            assertFalse(
                                    jdbcTemplate.queryForObject(
                                            "SELECT ai_external_transfer_allowed FROM book WHERE id = ?",
                                            Boolean.class,
                                            BOOK_ID + 2)));
        } finally {
            최신_스키마로_복구한다();
        }
    }

    @Test
    void AI_목표_컬럼의_물리_타입_NULL_collation이_ERD와_일치한다() {
        List<String> actualContracts =
                jdbcTemplate.queryForList(
                        """
                        SELECT CONCAT(
                            table_name, '.', column_name, '|', column_type, '|', is_nullable, '|',
                            COALESCE(collation_name, '-'), '|', COALESCE(NULLIF(extra, ''), '-'))
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND (
                              (table_name = 'book' AND column_name IN (
                                  'content_version', 'ai_route_supported',
                                  'ai_external_transfer_allowed', 'ai_data_policy_version'
                              ))
                              OR
                              (table_name = 'book_page' AND column_name IN (
                                  'ai_analysis_text', 'ai_public_guide_topic',
                                  'estimated_reading_seconds', 'embedding_model',
                                  'embedding_dimensions', 'embedding_json', 'duplicate_group_keys'
                              ))
                              OR table_name IN (
                                  'ai_route_prerequisite', 'ai_route_generation',
                                  'ai_route_generation_item', 'ai_reading_route',
                                  'ai_reading_route_item', 'ai_route_current',
                                  'ai_route_daily_usage'
                              )
                          )
                        ORDER BY table_name, ordinal_position
                        """,
                        String.class);

        assertEquals(
                List.of(
                        "ai_reading_route.id|bigint|NO|-|auto_increment",
                        "ai_reading_route.generation_id|char(36)|NO|ascii_bin|-",
                        "ai_reading_route.reader_id|bigint|NO|-|-",
                        "ai_reading_route.book_id|bigint|NO|-|-",
                        "ai_reading_route.content_version|varchar(100)|NO|ascii_bin|-",
                        "ai_reading_route.normalized_purpose|varchar(200)|NO|utf8mb4_0900_ai_ci|-",
                        "ai_reading_route.request_type|varchar(20)|NO|ascii_bin|-",
                        "ai_reading_route.max_additional_ink|int|YES|-|-",
                        "ai_reading_route.depth|varchar(20)|YES|ascii_bin|-",
                        "ai_reading_route.completed_at|datetime(6)|YES|-|-",
                        "ai_reading_route.feedback|varchar(20)|YES|ascii_bin|-",
                        "ai_reading_route.feedback_at|datetime(6)|YES|-|-",
                        "ai_reading_route.created_at|datetime(6)|NO|-|-",
                        "ai_reading_route_item.id|bigint|NO|-|auto_increment",
                        "ai_reading_route_item.route_id|bigint|NO|-|-",
                        "ai_reading_route_item.book_page_id|bigint|NO|-|-",
                        "ai_reading_route_item.position|int|NO|-|-",
                        "ai_reading_route_item.relevance|varchar(20)|NO|ascii_bin|-",
                        "ai_reading_route_item.prerequisite|tinyint(1)|NO|-|-",
                        "ai_reading_route_item.role|varchar(20)|NO|ascii_bin|-",
                        "ai_reading_route_item.opened_at|datetime(6)|YES|-|-",
                        "ai_route_current.reader_id|bigint|NO|-|-",
                        "ai_route_current.book_id|bigint|NO|-|-",
                        "ai_route_current.route_id|bigint|NO|-|-",
                        "ai_route_current.updated_at|datetime(6)|NO|-|-",
                        "ai_route_daily_usage.reader_id|bigint|NO|-|-",
                        "ai_route_daily_usage.usage_date|date|NO|-|-",
                        "ai_route_daily_usage.generation_count|int|NO|-|-",
                        "ai_route_generation.generation_id|char(36)|NO|ascii_bin|-",
                        "ai_route_generation.reader_id|bigint|NO|-|-",
                        "ai_route_generation.book_id|bigint|NO|-|-",
                        "ai_route_generation.content_version|varchar(100)|NO|ascii_bin|-",
                        "ai_route_generation.idempotency_key|char(36)|NO|ascii_bin|-",
                        "ai_route_generation.request_fingerprint|char(64)|NO|ascii_bin|-",
                        "ai_route_generation.normalized_purpose|varchar(200)|YES|utf8mb4_0900_ai_ci|-",
                        "ai_route_generation.request_type|varchar(20)|YES|ascii_bin|-",
                        "ai_route_generation.max_additional_ink|int|YES|-|-",
                        "ai_route_generation.depth|varchar(20)|YES|ascii_bin|-",
                        "ai_route_generation.status|varchar(20)|NO|ascii_bin|-",
                        "ai_route_generation.no_route_reason|varchar(40)|YES|ascii_bin|-",
                        "ai_route_generation.minimum_required_ink|int|YES|-|-",
                        "ai_route_generation.failure_code|varchar(100)|YES|ascii_bin|-",
                        "ai_route_generation.saved_route_id|bigint|YES|-|-",
                        "ai_route_generation.created_at|datetime(6)|NO|-|-",
                        "ai_route_generation.completed_at|datetime(6)|YES|-|-",
                        "ai_route_generation.expires_at|datetime(6)|YES|-|-",
                        "ai_route_generation_item.id|bigint|NO|-|auto_increment",
                        "ai_route_generation_item.generation_id|char(36)|NO|ascii_bin|-",
                        "ai_route_generation_item.book_page_id|bigint|NO|-|-",
                        "ai_route_generation_item.position|int|NO|-|-",
                        "ai_route_generation_item.relevance|varchar(20)|NO|ascii_bin|-",
                        "ai_route_generation_item.prerequisite|tinyint(1)|NO|-|-",
                        "ai_route_generation_item.role|varchar(20)|NO|ascii_bin|-",
                        "ai_route_prerequisite.id|bigint|NO|-|auto_increment",
                        "ai_route_prerequisite.book_id|bigint|NO|-|-",
                        "ai_route_prerequisite.prerequisite_page_number|int|NO|-|-",
                        "ai_route_prerequisite.dependent_page_number|int|NO|-|-",
                        "book.content_version|varchar(100)|NO|ascii_bin|-",
                        "book.ai_route_supported|tinyint(1)|NO|-|-",
                        "book.ai_external_transfer_allowed|tinyint(1)|NO|-|-",
                        "book.ai_data_policy_version|varchar(100)|YES|ascii_bin|-",
                        "book_page.ai_analysis_text|mediumtext|YES|utf8mb4_0900_ai_ci|-",
                        "book_page.ai_public_guide_topic|varchar(500)|YES|utf8mb4_0900_ai_ci|-",
                        "book_page.estimated_reading_seconds|int|YES|-|-",
                        "book_page.embedding_model|varchar(100)|YES|ascii_bin|-",
                        "book_page.embedding_dimensions|int|YES|-|-",
                        "book_page.embedding_json|json|YES|-|-",
                        "book_page.duplicate_group_keys|json|YES|-|-"),
                actualContracts);
    }

    @Test
    void AI_목표_고유키와_외래키는_이름과_컬럼_순서까지_고정한다() {
        List<String> actualIndexes =
                jdbcTemplate.queryForList(
                        """
                        SELECT CONCAT(
                            table_name, '.', index_name, '(',
                            GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','), ')')
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND non_unique = 0
                          AND table_name IN (
                              'ai_route_prerequisite', 'ai_route_generation',
                              'ai_route_generation_item', 'ai_reading_route',
                              'ai_reading_route_item', 'ai_route_current',
                              'ai_route_daily_usage'
                          )
                        GROUP BY table_name, index_name
                        ORDER BY table_name, index_name
                        """,
                        String.class);
        List<String> actualForeignKeys =
                jdbcTemplate.queryForList(
                        """
                        SELECT CONCAT(
                            table_name, '.', constraint_name, '(',
                            GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ','), ')->',
                            referenced_table_name, '(',
                            GROUP_CONCAT(referenced_column_name ORDER BY ordinal_position SEPARATOR ','),
                            ')')
                        FROM information_schema.key_column_usage
                        WHERE table_schema = DATABASE()
                          AND referenced_table_name IS NOT NULL
                          AND table_name IN (
                              'ai_route_prerequisite', 'ai_route_generation',
                              'ai_route_generation_item', 'ai_reading_route',
                              'ai_reading_route_item', 'ai_route_current',
                              'ai_route_daily_usage'
                          )
                        GROUP BY table_name, constraint_name, referenced_table_name
                        ORDER BY table_name, constraint_name
                        """,
                        String.class);

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "ai_reading_route.PRIMARY(id)",
                                        "ai_reading_route.uk_ai_reading_route_generation(generation_id)",
                                        "ai_reading_route.uk_ai_reading_route_owner(reader_id,book_id,id)",
                                        "ai_reading_route_item.PRIMARY(id)",
                                        "ai_reading_route_item.uk_ai_reading_route_item_page(route_id,book_page_id)",
                                        "ai_reading_route_item.uk_ai_reading_route_item_position(route_id,position)",
                                        "ai_route_current.PRIMARY(reader_id,book_id)",
                                        "ai_route_current.uk_ai_route_current_route(route_id)",
                                        "ai_route_daily_usage.PRIMARY(reader_id,usage_date)",
                                        "ai_route_generation.PRIMARY(generation_id)",
                                        "ai_route_generation.uk_ai_route_generation_reader_idempotency(reader_id,idempotency_key)",
                                        "ai_route_generation.uk_ai_route_generation_saved_route(saved_route_id)",
                                        "ai_route_generation_item.PRIMARY(id)",
                                        "ai_route_generation_item.uk_ai_route_generation_item_page(generation_id,book_page_id)",
                                        "ai_route_generation_item.uk_ai_route_generation_item_position(generation_id,position)",
                                        "ai_route_prerequisite.PRIMARY(id)",
                                        "ai_route_prerequisite.uk_ai_route_prerequisite_edge(book_id,prerequisite_page_number,dependent_page_number)"),
                                actualIndexes),
                () ->
                        assertEquals(
                                List.of(
                                        "ai_reading_route.fk_ai_reading_route_book(book_id)->book(id)",
                                        "ai_reading_route.fk_ai_reading_route_reader(reader_id)->reader(id)",
                                        "ai_reading_route_item.fk_ai_reading_route_item_page(book_page_id)->book_page(id)",
                                        "ai_reading_route_item.fk_ai_reading_route_item_route(route_id)->ai_reading_route(id)",
                                        "ai_route_current.fk_ai_route_current_route(reader_id,book_id,route_id)->ai_reading_route(reader_id,book_id,id)",
                                        "ai_route_daily_usage.fk_ai_route_daily_usage_reader(reader_id)->reader(id)",
                                        "ai_route_generation.fk_ai_route_generation_book(book_id)->book(id)",
                                        "ai_route_generation.fk_ai_route_generation_reader(reader_id)->reader(id)",
                                        "ai_route_generation.fk_ai_route_generation_saved_route(saved_route_id)->ai_reading_route(id)",
                                        "ai_route_generation_item.fk_ai_route_generation_item_generation(generation_id)->ai_route_generation(generation_id)",
                                        "ai_route_generation_item.fk_ai_route_generation_item_page(book_page_id)->book_page(id)",
                                        "ai_route_prerequisite.fk_ai_route_prerequisite_dependent_page(book_id,dependent_page_number)->book_page(book_id,page_number)",
                                        "ai_route_prerequisite.fk_ai_route_prerequisite_prerequisite_page(book_id,prerequisite_page_number)->book_page(book_id,page_number)"),
                                actualForeignKeys));
    }

    @Test
    void AI_목표_CHECK_제약은_이름까지_고정한다() {
        List<String> actualCheckConstraints =
                jdbcTemplate.queryForList(
                        """
                        SELECT CONCAT(table_name, '.', constraint_name)
                        FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE()
                          AND constraint_type = 'CHECK'
                          AND (
                              constraint_name LIKE 'ck_ai_%'
                              OR constraint_name IN (
                                  'ck_book_ai_flags_boolean',
                                  'ck_book_ai_route_support',
                                  'ck_book_page_ai_reading_seconds_positive',
                                  'ck_book_page_embedding_shape',
                                  'ck_book_page_duplicate_groups_array'
                              )
                          )
                        ORDER BY table_name, constraint_name
                        """,
                        String.class);

        assertEquals(
                List.of(
                        "ai_reading_route.ck_ai_reading_route_depth",
                        "ai_reading_route.ck_ai_reading_route_feedback",
                        "ai_reading_route.ck_ai_reading_route_feedback_shape",
                        "ai_reading_route.ck_ai_reading_route_request_shape",
                        "ai_reading_route.ck_ai_reading_route_request_type",
                        "ai_reading_route_item.ck_ai_reading_route_item_position_positive",
                        "ai_reading_route_item.ck_ai_reading_route_item_prerequisite_boolean",
                        "ai_reading_route_item.ck_ai_reading_route_item_relevance",
                        "ai_reading_route_item.ck_ai_reading_route_item_role",
                        "ai_route_daily_usage.ck_ai_route_daily_usage_count_non_negative",
                        "ai_route_generation.ck_ai_route_generation_completion_shape",
                        "ai_route_generation.ck_ai_route_generation_depth",
                        "ai_route_generation.ck_ai_route_generation_failure_shape",
                        "ai_route_generation.ck_ai_route_generation_no_route_reason",
                        "ai_route_generation.ck_ai_route_generation_no_route_shape",
                        "ai_route_generation.ck_ai_route_generation_request_shape",
                        "ai_route_generation.ck_ai_route_generation_request_type",
                        "ai_route_generation.ck_ai_route_generation_saved_shape",
                        "ai_route_generation.ck_ai_route_generation_status",
                        "ai_route_generation_item.ck_ai_route_generation_item_position_positive",
                        "ai_route_generation_item.ck_ai_route_generation_item_prerequisite_boolean",
                        "ai_route_generation_item.ck_ai_route_generation_item_relevance",
                        "ai_route_generation_item.ck_ai_route_generation_item_role",
                        "ai_route_prerequisite.ck_ai_route_prerequisite_distinct_pages",
                        "book.ck_book_ai_flags_boolean",
                        "book.ck_book_ai_route_support",
                        "book_page.ck_book_page_ai_reading_seconds_positive",
                        "book_page.ck_book_page_duplicate_groups_array",
                        "book_page.ck_book_page_embedding_shape"),
                actualCheckConstraints);
    }

    @Test
    void AI_목표_CHECK_제약은_잘못된_지원상태_메타데이터_생성상태_사용량을_거부한다() {
        기본_독자_도서_페이지를_생성한다();

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                UPDATE book
                                                SET ai_route_supported = TRUE
                                                WHERE id = ?
                                                """,
                                                BOOK_ID)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                UPDATE book_page
                                                SET estimated_reading_seconds = 0
                                                WHERE id = ?
                                                """,
                                                FIRST_PAGE_ID)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                UPDATE book_page
                                                SET embedding_model = 'text-embedding-test',
                                                    embedding_dimensions = NULL,
                                                    embedding_json = JSON_ARRAY(0.1)
                                                WHERE id = ?
                                                """,
                                                FIRST_PAGE_ID)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO ai_route_generation
                                                    (generation_id, reader_id, book_id,
                                                     content_version, idempotency_key,
                                                     request_fingerprint, normalized_purpose,
                                                     status, created_at)
                                                VALUES (?, ?, ?, 'initial-v1', ?, ?,
                                                        '테스트 목적', 'GENERATING',
                                                        '2026-08-07 00:00:00.000000')
                                                """,
                                                GENERATION_ID,
                                                READER_ID,
                                                BOOK_ID,
                                                "00000000-0000-0000-0000-000000001000",
                                                "a".repeat(64))),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO ai_route_daily_usage
                                                    (reader_id, usage_date, generation_count)
                                                VALUES (?, '2026-08-07', -1)
                                                """,
                                                READER_ID)));
    }

    @Test
    void 중복_멱등키와_generation_항목의_중복_position_page를_거부한다() {
        기본_독자_도서_페이지를_생성한다();
        생성을_생성한다(GENERATION_ID, "00000000-0000-0000-0000-000000001000");
        생성_항목을_생성한다(54_000L, GENERATION_ID, FIRST_PAGE_ID, 1);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        생성을_생성한다(
                                                SECOND_GENERATION_ID,
                                                "00000000-0000-0000-0000-000000001000")),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        생성_항목을_생성한다(
                                                54_001L, GENERATION_ID, SECOND_PAGE_ID, 1)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        생성_항목을_생성한다(
                                                54_002L, GENERATION_ID, FIRST_PAGE_ID, 2)));
    }

    @Test
    void 저장_route_항목의_중복_position_page를_거부한다() {
        기본_독자_도서_페이지를_생성한다();
        저장_경로를_생성한다(55_000L, GENERATION_ID, READER_ID, BOOK_ID);
        저장_경로_항목을_생성한다(56_000L, 55_000L, FIRST_PAGE_ID, 1);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        저장_경로_항목을_생성한다(
                                                56_001L, 55_000L, SECOND_PAGE_ID, 1)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        저장_경로_항목을_생성한다(
                                                56_002L, 55_000L, FIRST_PAGE_ID, 2)));
    }

    @Test
    void 현재_route는_독자와_도서마다_하나만_지정할_수_있다() {
        기본_독자_도서_페이지를_생성한다();
        저장_경로를_생성한다(55_000L, GENERATION_ID, READER_ID, BOOK_ID);
        저장_경로를_생성한다(55_001L, SECOND_GENERATION_ID, READER_ID, BOOK_ID);
        현재_경로를_생성한다(READER_ID, BOOK_ID, 55_000L);

        assertThrows(
                DataAccessException.class,
                () -> 현재_경로를_생성한다(READER_ID, BOOK_ID, 55_001L));
    }

    @Test
    void 선수_관계와_현재_route의_복합_FK가_다른_도서_연결을_거부한다() {
        기본_독자_도서_페이지를_생성한다();
        독자를_생성한다(SECOND_READER_ID);
        도서를_생성한다(SECOND_BOOK_ID);
        페이지를_생성한다(SECOND_PAGE_ID + 1, SECOND_BOOK_ID, 3);
        저장_경로를_생성한다(55_000L, GENERATION_ID, READER_ID, BOOK_ID);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO ai_route_prerequisite
                                                    (book_id, prerequisite_page_number,
                                                     dependent_page_number)
                                                VALUES (?, 1, 3)
                                                """,
                                                BOOK_ID)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 현재_경로를_생성한다(SECOND_READER_ID, BOOK_ID, 55_000L)));
    }

    @Test
    void 저장_route를_삭제해도_대여_잉크_세션_서재는_보존한다() {
        기본_독자_도서_페이지를_생성한다();
        핵심_사용자_기록을_생성한다();
        저장_경로를_생성한다(55_000L, GENERATION_ID, READER_ID, BOOK_ID);
        저장_경로_항목을_생성한다(56_000L, 55_000L, FIRST_PAGE_ID, 1);
        현재_경로를_생성한다(READER_ID, BOOK_ID, 55_000L);

        jdbcTemplate.update("DELETE FROM ai_route_current WHERE route_id = ?", 55_000L);
        jdbcTemplate.update("DELETE FROM ai_reading_route_item WHERE route_id = ?", 55_000L);
        jdbcTemplate.update("DELETE FROM ai_reading_route WHERE id = ?", 55_000L);

        assertAll(
                () -> assertEquals(1, 행_수를_조회한다("page_rental")),
                () -> assertEquals(1, 행_수를_조회한다("ink_ledger")),
                () -> assertEquals(1, 행_수를_조회한다("library_entry")),
                () -> assertEquals(1, 행_수를_조회한다("reading_session")));
    }

    private Flyway 새_Flyway를_생성한다(MigrationVersion target) {
        var configuration =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void 최신_스키마로_복구한다() {
        Flyway flyway = 새_Flyway를_생성한다(null);
        flyway.clean();
        flyway.migrate();
    }

    private List<String> 적용된_버전을_조회한다() {
        return jdbcTemplate.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = 1 AND type = 'SQL'
                ORDER BY installed_rank
                """,
                String.class);
    }

    private int AI_경로_테이블_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'ai_route_prerequisite', 'ai_route_generation',
                      'ai_route_generation_item', 'ai_reading_route',
                      'ai_reading_route_item', 'ai_route_current',
                      'ai_route_daily_usage'
                  )
                """,
                Integer.class);
    }

    private void 기본_독자_도서_페이지를_생성한다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID);
        페이지를_생성한다(FIRST_PAGE_ID, BOOK_ID, 1);
        페이지를_생성한다(SECOND_PAGE_ID, BOOK_ID, 2);
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'encoded-password', '2026-08-07 00:00:00.000000')
                """,
                readerId,
                "reader-" + readerId + "@example.com");
    }

    private void 도서를_생성한다(long bookId) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '개발', ?, '저자', 2, 10000)
                """,
                bookId,
                "도서-" + bookId);
    }

    private void 페이지를_생성한다(long pageId, long bookId, int pageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, ?, 'TEXT', '본문', NULL)
                """,
                pageId,
                bookId,
                pageNumber);
    }

    private void 생성을_생성한다(String generationId, String idempotencyKey) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_generation
                    (generation_id, reader_id, book_id, content_version,
                     idempotency_key, request_fingerprint, normalized_purpose,
                     request_type, max_additional_ink, status, created_at)
                VALUES (?, ?, ?, 'initial-v1', ?, ?, '테스트 목적',
                        'INK_BUDGET', 3, 'GENERATING',
                        '2026-08-07 00:00:00.000000')
                """,
                generationId,
                READER_ID,
                BOOK_ID,
                idempotencyKey,
                "a".repeat(64));
    }

    private void 생성_항목을_생성한다(
            long itemId, String generationId, long pageId, int position) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_generation_item
                    (id, generation_id, book_page_id, position,
                     relevance, prerequisite, role)
                VALUES (?, ?, ?, ?, 'HIGH', FALSE, 'CORE')
                """,
                itemId,
                generationId,
                pageId,
                position);
    }

    private void 저장_경로를_생성한다(
            long routeId, String generationId, long readerId, long bookId) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route
                    (id, generation_id, reader_id, book_id, content_version,
                     normalized_purpose, request_type, max_additional_ink, created_at)
                VALUES (?, ?, ?, ?, 'initial-v1', '테스트 목적',
                        'INK_BUDGET', 3, '2026-08-07 00:00:00.000000')
                """,
                routeId,
                generationId,
                readerId,
                bookId);
    }

    private void 저장_경로_항목을_생성한다(
            long itemId, long routeId, long pageId, int position) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_reading_route_item
                    (id, route_id, book_page_id, position,
                     relevance, prerequisite, role)
                VALUES (?, ?, ?, ?, 'HIGH', FALSE, 'CORE')
                """,
                itemId,
                routeId,
                pageId,
                position);
    }

    private void 현재_경로를_생성한다(long readerId, long bookId, long routeId) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_current (reader_id, book_id, route_id, updated_at)
                VALUES (?, ?, ?, '2026-08-07 00:00:00.000000')
                """,
                readerId,
                bookId,
                routeId);
    }

    private void 핵심_사용자_기록을_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (57000, ?, ?, '2026-08-07 00:00:00.000000',
                        '2026-09-06 00:00:00.000000')
                """,
                READER_ID,
                FIRST_PAGE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     page_rental_id, occurred_at)
                VALUES (58000, ?, 'DEDUCTION', 1, 99, 57000,
                        '2026-08-07 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (id, reader_id, book_id, current_page_number,
                     viewer_session_id, updated_at)
                VALUES (59000, ?, ?, 1,
                        '00000000-0000-0000-0000-000000059000',
                        '2026-08-07 00:00:00.000000')
                """,
                READER_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (id, reader_id, book_id, last_page_number, updated_at)
                VALUES (60000, ?, ?, 1, '2026-08-07 00:00:00.000000')
                """,
                READER_ID,
                BOOK_ID);
    }

    private int 행_수를_조회한다(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
