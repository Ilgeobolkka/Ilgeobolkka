package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationEngine;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=test-project",
            "openai.api-key=test-key",
            "openai.data-policy-version=policy-v1",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                    + "com.example.ilgeobolkka.airoute.evaluation."
                    + "AiRouteEvaluationMySqlIntegrationTest$SqlRecorder"
        })
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteEvaluationMySqlIntegrationTest.TestBeans.class)
class AiRouteEvaluationMySqlIntegrationTest {

    private static final long BOOK_ID = 469_101L;
    private static final long PAGE_ID = 469_201L;
    private static final Set<String> FORBIDDEN_TABLES = Set.of(
            "reader",
            "ink_account",
            "ink_ledger",
            "ink_purchase",
            "page_rental",
            "book_ownership",
            "ownership_payment",
            "reading_session",
            "library_entry",
            "ai_route_generation",
            "ai_route_generation_item",
            "ai_route_daily_usage",
            "ai_reading_route",
            "ai_reading_route_item",
            "ai_route_current");

    private final AiRouteGenerationEngine engine;
    private final com.example.ilgeobolkka.book.repository.BookPageRepository bookPageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    AiRouteEvaluationMySqlIntegrationTest(
            AiRouteGenerationEngine engine,
            com.example.ilgeobolkka.book.repository.BookPageRepository bookPageRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.engine = engine;
        this.bookPageRepository = bookPageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        cleanup();
        insertUnsupportedBookAndPage();
    }

    @AfterEach
    void tearDown() {
        SqlRecorder.stop();
        cleanup();
    }

    @Test
    void 평가_실행은_지원_false_도서의_운영_콘텐츠만_읽고_사용자와_경로_테이블은_읽거나_쓰지_않는다() {
        Map<String, Integer> before = forbiddenRowCounts();
        AiRouteEvaluationService service = new AiRouteEvaluationService(
                engine,
                new AiRouteEvaluationPageReader(bookPageRepository),
                System::nanoTime,
                clock);

        SqlRecorder.start();
        AiRouteEvaluationResult result = service.evaluate(plan());
        List<String> statements = SqlRecorder.stop();
        Map<String, Integer> after = forbiddenRowCounts();

        assertThat(result.successful()).isTrue();
        assertThat(result.completedCases()).singleElement().satisfies(completed ->
                assertThat(completed.routePages().getFirst().pageNumber()).isEqualTo(1));
        assertThat(after).isEqualTo(before);
        assertThat(statements).anyMatch(sql -> sql.contains(" from book "));
        assertThat(statements).anyMatch(sql -> sql.contains(" from book_page "));
        for (String table : FORBIDDEN_TABLES) {
            assertThat(statements)
                    .as("금지 테이블 SQL: %s", table)
                    .noneMatch(sql -> containsTable(sql, table));
        }
    }

    private boolean containsTable(String sql, String table) {
        return sql.matches("(?s).*(from|join|into|update|delete\\s+from)\\s+`?"
                + table
                + "`?(\\s|$).*");
    }

    private AiRouteEvaluationPlan plan() {
        AiRouteEvaluationPlan.Case evaluationCase = new AiRouteEvaluationPlan.Case(
                new AiRouteEvaluationPlan.Input(
                        "case-mysql", BOOK_ID, "운영 경로 검증", false, 5, null, List.of()),
                new AiRouteEvaluationPlan.Reference(
                        List.of("개념"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(1),
                        List.of(),
                        Map.of(1, List.of("개념"))));
        return new AiRouteEvaluationPlan(
                "ai-route-v2",
                "a".repeat(64),
                "manifest-revision",
                "evaluation-revision",
                List.of(evaluationCase));
    }

    private void insertUnsupportedBookAndPage() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won,
                     content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, '인문', 'SCRUM-469 테스트 도서', '테스트 저자', 1, 10000,
                        'ai-route-v2', FALSE, TRUE, 'policy-v1')
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content,
                     ai_analysis_text, ai_public_guide_topic, estimated_reading_seconds,
                     embedding_model, embedding_dimensions, embedding_json,
                     duplicate_group_keys, ai_route_candidate)
                VALUES (?, ?, 1, 'TEXT', '테스트 본문',
                        '비공개 분석', '공개 주제', 60,
                        'embedding-v1', 2, '[1.0, 0.0]', JSON_ARRAY(), TRUE)
                """,
                PAGE_ID,
                BOOK_ID);
    }

    private Map<String, Integer> forbiddenRowCounts() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String table : FORBIDDEN_TABLES.stream().sorted().toList()) {
            counts.put(
                    table,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + table, Integer.class));
        }
        return Map.copyOf(counts);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM ai_route_prerequisite WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }

    public static class SqlRecorder implements StatementInspector {

        private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();
        private static volatile boolean recording;

        static void start() {
            STATEMENTS.clear();
            recording = true;
        }

        static List<String> stop() {
            recording = false;
            return List.copyOf(STATEMENTS);
        }

        @Override
        public String inspect(String sql) {
            if (recording) {
                STATEMENTS.add(sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
            }
            return sql;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        OpenAiEmbeddingGateway embeddingGateway() {
            return new OpenAiEmbeddingGateway() {
                @Override
                public Embedding embedPurpose(
                        PurposeInput input, String model, int dimensions) {
                    return new Embedding(List.of(1.0, 0.0), model, dimensions);
                }

                @Override
                public Embedding embedPageAnalysis(
                        PageAnalysisInput input, String model, int dimensions) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        @Primary
        OpenAiRouteGateway routeGateway() {
            return new OpenAiRouteGateway() {
                @Override
                public RouteContract routeContract() {
                    return new RouteContract("route-v1", "prompt-v1", "schema-v1");
                }

                @Override
                public RouteGatewayResult proposeRoute(RouteInput input) {
                    ModelRouteItem item = new ModelRouteItem(
                            1, Relevance.HIGH, false, Role.CORE);
                    return new RouteGatewayResult(
                            new ModelRouteProposal(List.of(item)), "prompt-v1", "schema-v1");
                }
            };
        }
    }
}
