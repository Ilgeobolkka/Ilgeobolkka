package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "openai.data-policy-version=policy-v1")
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class AiRouteSupportActivationMySqlIntegrationTest {

    private static final long FIRST_BOOK_ID = 475_101L;
    private static final long NOVEL_BOOK_ID = 475_999L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String DATA_POLICY_VERSION = "policy-v1";
    private static final String FAILURE_CHECK = "ck_scrum_475_activation_failure";
    private static final AiRouteEvaluationResult.Versions VERSIONS =
            new AiRouteEvaluationResult.Versions(
                    "embedding-v1", "route-v1", "candidate-v1", "prompt-v1", "schema-v1");

    private final AiRouteSupportActivationService service;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @TempDir
    Path tempDirectory;

    @Autowired
    AiRouteSupportActivationMySqlIntegrationTest(
            AiRouteSupportActivationService service,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void 통과_report의_지원_도서_전체만_한_트랜잭션으로_활성화하고_소설은_false로_둔다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        insertBook(FIRST_BOOK_ID + 1, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "경제");
        insertBook(NOVEL_BOOK_ID, CONTENT_VERSION, null, false, false, "소설");

        AiRouteSupportActivationService.Activation activation =
                service.activate(report(List.of(FIRST_BOOK_ID, FIRST_BOOK_ID + 1), true));

        assertThat(activation.activatedBookIds())
                .containsExactly(FIRST_BOOK_ID, FIRST_BOOK_ID + 1);
        assertThat(supported(FIRST_BOOK_ID)).isTrue();
        assertThat(supported(FIRST_BOOK_ID + 1)).isTrue();
        assertThat(supported(NOVEL_BOOK_ID)).isFalse();
    }

    @Test
    void 지원_도서가_10권이어도_권수_조건_없이_전부_활성화한다() {
        List<Long> bookIds = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            long bookId = FIRST_BOOK_ID + index;
            bookIds.add(bookId);
            insertBook(bookId, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        }

        service.activate(report(bookIds, true));

        assertThat(bookIds).allSatisfy(bookId -> assertThat(supported(bookId)).isTrue());
    }

    @Test
    void report에서_DB_지원_도서가_누락되면_전체를_변경하지_않는다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        insertBook(FIRST_BOOK_ID + 1, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "경제");

        assertThatThrownBy(() -> service.activate(report(List.of(FIRST_BOOK_ID), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성화 대상 전체");

        assertThat(supported(FIRST_BOOK_ID)).isFalse();
        assertThat(supported(FIRST_BOOK_ID + 1)).isFalse();
    }

    @Test
    void 대상이_누락되거나_version_profile_권리_조건이_다르면_변경_0건으로_실패한다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        assertThatThrownBy(() -> service.activate(
                        report(List.of(FIRST_BOOK_ID, FIRST_BOOK_ID + 1), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("누락");
        assertThat(supported(FIRST_BOOK_ID)).isFalse();

        cleanup();
        insertBook(FIRST_BOOK_ID, "other-version", DATA_POLICY_VERSION, true, false, "인문");
        assertThatThrownBy(() -> service.activate(report(List.of(FIRST_BOOK_ID), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contentVersion");
        assertThat(supported(FIRST_BOOK_ID)).isFalse();

        cleanup();
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, "other-policy", true, false, "인문");
        assertThatThrownBy(() -> service.activate(report(List.of(FIRST_BOOK_ID), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("데이터 정책");
        assertThat(supported(FIRST_BOOK_ID)).isFalse();

        cleanup();
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, false, false, "인문");
        assertThatThrownBy(() -> service.activate(report(List.of(FIRST_BOOK_ID), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("외부 전송");
        assertThat(supported(FIRST_BOOK_ID)).isFalse();
    }

    @Test
    void report와_환경의_데이터_정책_프로필이_다르면_DB_변경_전에_실패한다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");

        assertThatThrownBy(() -> service.activate(
                        report(List.of(FIRST_BOOK_ID), true, "other-policy")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("report와 환경");
        assertThat(supported(FIRST_BOOK_ID)).isFalse();
    }

    @Test
    void 활성화_대상에_소설이_포함되면_지원_도서까지_변경_0건으로_실패한다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        insertBook(NOVEL_BOOK_ID, CONTENT_VERSION, null, false, false, "소설");

        assertThatThrownBy(() -> service.activate(
                        report(List.of(FIRST_BOOK_ID, NOVEL_BOOK_ID), true)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(supported(FIRST_BOOK_ID)).isFalse();
        assertThat(supported(NOVEL_BOOK_ID)).isFalse();
    }

    @Test
    void 대상_중_하나라도_이미_true면_기존_값을_그대로_두고_전체_실패한다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        insertBook(FIRST_BOOK_ID + 1, CONTENT_VERSION, DATA_POLICY_VERSION, true, true, "경제");

        assertThatThrownBy(() -> service.activate(
                        report(List.of(FIRST_BOOK_ID, FIRST_BOOK_ID + 1), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 활성화");

        assertThat(supported(FIRST_BOOK_ID)).isFalse();
        assertThat(supported(FIRST_BOOK_ID + 1)).isTrue();
    }

    @Test
    void 중간_SQL이_실패하면_앞서_실행된_update도_rollback한다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        insertBook(FIRST_BOOK_ID + 1, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "경제");
        jdbcTemplate.execute(
                "ALTER TABLE book ADD CONSTRAINT " + FAILURE_CHECK
                        + " CHECK (title <> 'SCRUM-475 테스트 도서 " + (FIRST_BOOK_ID + 1) + "'"
                        + " OR ai_route_supported = FALSE)");

        assertThatThrownBy(() -> service.activate(
                        report(List.of(FIRST_BOOK_ID, FIRST_BOOK_ID + 1), true)))
                .isInstanceOf(RuntimeException.class);

        dropFailureCheckIfExists();
        assertThat(supported(FIRST_BOOK_ID)).isFalse();
        assertThat(supported(FIRST_BOOK_ID + 1)).isFalse();
    }

    @Test
    void 같은_대상을_동시에_활성화하면_한_요청만_성공한다() throws Exception {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
        AiRouteEvaluationReport report = report(List.of(FIRST_BOOK_ID), true);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> activateAfter(start, report));
            Future<Boolean> second = executor.submit(() -> activateAfter(start, report));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(supported(FIRST_BOOK_ID)).isTrue();
    }

    @Test
    void 품질_기준을_통과하지_못한_report는_DB를_조회하기_전에_거부한다() {
        insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");

        assertThatThrownBy(() -> service.activate(report(List.of(FIRST_BOOK_ID), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("통과");
        assertThat(supported(FIRST_BOOK_ID)).isFalse();
    }

    @Test
    void 현재_Q01_결과가_실패하거나_재현_정보가_바뀌면_DB_변경_0건으로_실패한다() throws Exception {
        AiRouteEvaluationResult passingResult = result(
                CONTENT_VERSION, "a".repeat(64), "evaluation-git", VERSIONS);
        AiRouteEvaluationReport report = AiRouteEvaluationReport.create(
                passingResult,
                List.of(new AiRouteHumanJudgment("case-1", true)),
                DATA_POLICY_VERSION);
        List<ActivationScenario> scenarios = List.of(
                new ActivationScenario("실패 결과", failedResult()),
                new ActivationScenario(
                        "manifest SHA",
                        result(CONTENT_VERSION, "b".repeat(64), "evaluation-git", VERSIONS)),
                new ActivationScenario(
                        "evaluation revision",
                        result(CONTENT_VERSION, "a".repeat(64), "evaluation-git-v2", VERSIONS)),
                new ActivationScenario(
                        "embedding model",
                        result(
                                CONTENT_VERSION,
                                "a".repeat(64),
                                "evaluation-git",
                                versions("embedding-v2", "route-v1", "candidate-v1", "prompt-v1", "schema-v1"))),
                new ActivationScenario(
                        "route model",
                        result(
                                CONTENT_VERSION,
                                "a".repeat(64),
                                "evaluation-git",
                                versions("embedding-v1", "route-v2", "candidate-v1", "prompt-v1", "schema-v1"))),
                new ActivationScenario(
                        "candidate policy",
                        result(
                                CONTENT_VERSION,
                                "a".repeat(64),
                                "evaluation-git",
                                versions("embedding-v1", "route-v1", "candidate-v2", "prompt-v1", "schema-v1"))),
                new ActivationScenario(
                        "prompt version",
                        result(
                                CONTENT_VERSION,
                                "a".repeat(64),
                                "evaluation-git",
                                versions("embedding-v1", "route-v1", "candidate-v1", "prompt-v2", "schema-v1"))),
                new ActivationScenario(
                        "schema version",
                        result(
                                CONTENT_VERSION,
                                "a".repeat(64),
                                "evaluation-git",
                                versions("embedding-v1", "route-v1", "candidate-v1", "prompt-v1", "schema-v2"))));

        AiRouteEvaluationProperties properties = new AiRouteEvaluationProperties();
        properties.setOutput(tempDirectory.resolve("result.json"));
        properties.setReportOutput(tempDirectory.resolve("report.json"));
        Files.write(properties.reportOutput(), objectMapper.writeValueAsBytes(report));
        AiRouteSupportActivationRunner runner = new AiRouteSupportActivationRunner(
                properties, new AiRouteEvaluationArtifactReader(objectMapper), service);

        for (ActivationScenario scenario : scenarios) {
            cleanup();
            insertBook(FIRST_BOOK_ID, CONTENT_VERSION, DATA_POLICY_VERSION, true, false, "인문");
            Files.write(properties.output(), objectMapper.writeValueAsBytes(scenario.result()));

            assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                    .as(scenario.name())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재사용");
            assertThat(supported(FIRST_BOOK_ID)).as(scenario.name()).isFalse();
        }
    }

    private boolean activateAfter(CountDownLatch start, AiRouteEvaluationReport report)
            throws InterruptedException {
        start.await();
        try {
            service.activate(report);
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private AiRouteEvaluationReport report(List<Long> bookIds, boolean useful) {
        return report(bookIds, useful, DATA_POLICY_VERSION);
    }

    private AiRouteEvaluationReport report(
            List<Long> bookIds, boolean useful, String dataPolicyVersion) {
        List<AiRouteEvaluationResult.CompletedCase> cases = new ArrayList<>();
        List<AiRouteHumanJudgment> judgments = new ArrayList<>();
        for (int index = 0; index < bookIds.size(); index++) {
            String caseId = "case-" + (index + 1);
            cases.add(new AiRouteEvaluationResult.CompletedCase(
                    caseId,
                    bookIds.get(index),
                    Duration.ofSeconds(1).toNanos(),
                    List.of(new AiRouteEvaluationResult.RoutePage(1, List.of("개념-" + index))),
                    new AiRouteEvaluationResult.Comparison(
                            List.of("개념-" + index),
                            List.of(),
                            List.of(new AiRouteEvaluationResult.Prerequisite(1, 2)),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of()),
                    VERSIONS));
            judgments.add(new AiRouteHumanJudgment(caseId, useful));
        }
        AiRouteEvaluationResult result = new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.SUCCESS,
                CONTENT_VERSION,
                "a".repeat(64),
                "manifest-git",
                "evaluation-git",
                Instant.parse("2026-08-15T00:00:00Z"),
                cases,
                new AiRouteCandidateThresholdEvaluator.Result(
                        0.40,
                        List.of(new AiRouteCandidateThresholdEvaluator.ThresholdResult(
                                0.40, 1, 1, 1.0))),
                null);
        return AiRouteEvaluationReport.create(result, judgments, dataPolicyVersion);
    }

    private AiRouteEvaluationResult result(
            String contentVersion,
            String manifestSha256,
            String evaluationGitRevision,
            AiRouteEvaluationResult.Versions versions) {
        return new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.SUCCESS,
                contentVersion,
                manifestSha256,
                "manifest-git",
                evaluationGitRevision,
                Instant.parse("2026-08-15T00:00:00Z"),
                List.of(new AiRouteEvaluationResult.CompletedCase(
                        "case-1",
                        FIRST_BOOK_ID,
                        Duration.ofSeconds(1).toNanos(),
                        List.of(new AiRouteEvaluationResult.RoutePage(1, List.of("개념-1"))),
                        new AiRouteEvaluationResult.Comparison(
                                List.of("개념-1"),
                                List.of(),
                                List.of(new AiRouteEvaluationResult.Prerequisite(1, 2)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()),
                        versions)),
                new AiRouteCandidateThresholdEvaluator.Result(
                        0.40,
                        List.of(new AiRouteCandidateThresholdEvaluator.ThresholdResult(
                                0.40, 1, 1, 1.0))),
                null);
    }

    private AiRouteEvaluationResult failedResult() {
        return new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.FAILED,
                CONTENT_VERSION,
                "a".repeat(64),
                "manifest-git",
                "evaluation-git",
                Instant.parse("2026-08-15T00:00:00Z"),
                List.of(),
                null,
                new AiRouteEvaluationResult.Failure(
                        "case-1",
                        AiRouteEvaluationResult.FailureReason.EXECUTION,
                        null,
                        Duration.ofSeconds(1).toNanos()));
    }

    private AiRouteEvaluationResult.Versions versions(
            String embeddingModel,
            String routeModel,
            String candidatePolicyVersion,
            String promptVersion,
            String schemaVersion) {
        return new AiRouteEvaluationResult.Versions(
                embeddingModel,
                routeModel,
                candidatePolicyVersion,
                promptVersion,
                schemaVersion);
    }

    private void insertBook(
            long bookId,
            String contentVersion,
            String dataPolicyVersion,
            boolean externalTransferAllowed,
            boolean supported,
            String category) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won,
                     content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, ?, ?, '테스트 저자', 1, 10000, ?, ?, ?, ?)
                """,
                bookId,
                category,
                "SCRUM-475 테스트 도서 " + bookId,
                contentVersion,
                supported,
                externalTransferAllowed,
                dataPolicyVersion);
    }

    private boolean supported(long bookId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT ai_route_supported FROM book WHERE id = ?", Boolean.class, bookId));
    }

    private void cleanup() {
        dropFailureCheckIfExists();
        jdbcTemplate.update(
                "DELETE FROM book WHERE id BETWEEN ? AND ?", FIRST_BOOK_ID, NOVEL_BOOK_ID);
    }

    private void dropFailureCheckIfExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'book'
                  AND constraint_name = ?
                """,
                Integer.class,
                FAILURE_CHECK);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE book DROP CHECK " + FAILURE_CHECK);
        }
    }

    private record ActivationScenario(String name, AiRouteEvaluationResult result) {}
}
