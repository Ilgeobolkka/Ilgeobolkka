package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class AiRouteEvaluationMetricsTest {

    private static final String MANIFEST_SHA = "a".repeat(64);
    private static final String DATA_POLICY_VERSION = "policy-v1";
    private static final AiRouteEvaluationResult.Versions VERSIONS =
            new AiRouteEvaluationResult.Versions(
                    "embedding-v1", "route-v1", "candidate-v1", "prompt-v1", "schema-v1");

    @TempDir
    Path tempDir;

    @Test
    void 출시_기준과_동일한_경계는_통과한다() {
        AiRouteEvaluationResult result = result(standardCases(16, 4, 1));

        AiRouteEvaluationMetrics metrics =
                AiRouteEvaluationMetrics.calculate(result, judgments(result, 4));

        assertThat(metrics.totalCases()).isEqualTo(5);
        assertThat(metrics.requiredConceptCoverageRate()).isEqualTo(0.8);
        assertThat(metrics.irrelevantOrDuplicatePageRate()).isEqualTo(0.2);
        assertThat(metrics.prerequisiteViolationRate()).isEqualTo(0.05);
        assertThat(metrics.usefulRouteRate()).isEqualTo(0.8);
        assertThat(metrics.p95DurationNanos()).isEqualTo(Duration.ofSeconds(10).toNanos());
        assertThat(metrics.overTwentySecondsCases()).isZero();
        assertThat(metrics.passed()).isTrue();
    }

    @Test
    void 출시_기준을_하나라도_벗어나면_실패한다() {
        assertThat(metrics(15, 4, 1, 4).passed()).as("필수 개념 80% 미만").isFalse();
        assertThat(metrics(16, 5, 1, 4).passed()).as("무관·중복 20% 초과").isFalse();
        assertThat(metrics(16, 4, 2, 4).passed()).as("선수 위반 5% 초과").isFalse();
        assertThat(metrics(16, 4, 1, 3).passed()).as("사람 유용성 80% 미만").isFalse();
    }

    @Test
    void 출시_기준보다_안전한_경계도_통과한다() {
        assertThat(metrics(17, 3, 0, 5).passed()).isTrue();
    }

    @Test
    void 필수_개념이나_선수_관계의_분모가_0이면_평가를_거부한다() {
        AiRouteEvaluationResult noConcepts = result(List.of(completedCase(
                1, 0, 0, 1, 0, 1, 0, Duration.ofSeconds(1).toNanos(), VERSIONS)));
        AiRouteEvaluationResult noPrerequisites = result(List.of(completedCase(
                1, 1, 1, 1, 0, 0, 0, Duration.ofSeconds(1).toNanos(), VERSIONS)));

        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        noConcepts, judgments(noConcepts, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수 개념");
        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        noPrerequisites, judgments(noPrerequisites, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("선수 관계");
    }

    @Test
    void 무관_페이지와_중복_그룹의_추가_페이지는_합집합으로_한_번만_센다() {
        List<AiRouteEvaluationResult.RoutePage> pages = routePages(1, 5, 5);
        AiRouteEvaluationResult.Comparison comparison = new AiRouteEvaluationResult.Comparison(
                concepts("case-1-concept-", 5),
                List.of(),
                List.of(new AiRouteEvaluationResult.Prerequisite(1, 2)),
                List.of(2, 4),
                List.of(List.of(1, 2, 3), List.of(3, 4, 5)),
                List.of(),
                List.of());
        AiRouteEvaluationResult result = result(List.of(new AiRouteEvaluationResult.CompletedCase(
                "case-1",
                101L,
                Duration.ofSeconds(1).toNanos(),
                pages,
                comparison,
                VERSIONS)));

        AiRouteEvaluationMetrics metrics =
                AiRouteEvaluationMetrics.calculate(result, judgments(result, 1));

        assertThat(metrics.irrelevantOrDuplicatePages()).isEqualTo(4);
        assertThat(metrics.totalRecommendedPages()).isEqualTo(5);
    }

    @Test
    void 의존_페이지가_없으면_위반은_아니지만_선수_관계_분모에는_남긴다() {
        AiRouteEvaluationResult.CompletedCase completed = new AiRouteEvaluationResult.CompletedCase(
                "case-1",
                101L,
                Duration.ofSeconds(1).toNanos(),
                routePages(1, 1, 1),
                new AiRouteEvaluationResult.Comparison(
                        List.of("case-1-concept-1"),
                        List.of(),
                        List.of(new AiRouteEvaluationResult.Prerequisite(2, 3)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()),
                VERSIONS);
        AiRouteEvaluationResult result = result(List.of(completed));

        AiRouteEvaluationMetrics metrics =
                AiRouteEvaluationMetrics.calculate(result, judgments(result, 1));

        assertThat(metrics.prerequisiteViolations()).isZero();
        assertThat(metrics.totalPrerequisites()).isEqualTo(1);
    }

    @Test
    void p95는_N90의_86번째와_N10의_10번째_값을_사용한다() {
        List<Long> ninetyDurations = new ArrayList<>();
        for (int index = 1; index <= 90; index++) {
            if (index <= 85) {
                ninetyDurations.add(Duration.ofSeconds(1).toNanos());
            } else if (index == 86) {
                ninetyDurations.add(Duration.ofSeconds(10).toNanos());
            } else {
                ninetyDurations.add(Duration.ofSeconds(20).toNanos());
            }
        }
        List<Long> tenDurations = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            tenDurations.add(Duration.ofSeconds(index <= 9 ? 1 : 10).toNanos());
        }

        AiRouteEvaluationMetrics ninety = metricsForDurations(ninetyDurations);
        AiRouteEvaluationMetrics ten = metricsForDurations(tenDurations);

        assertThat(ninety.p95DurationNanos()).isEqualTo(Duration.ofSeconds(10).toNanos());
        assertThat(ninety.overTwentySecondsCases()).isZero();
        assertThat(ten.p95DurationNanos()).isEqualTo(Duration.ofSeconds(10).toNanos());
        assertThat(ninety.passed()).isTrue();
        assertThat(ten.passed()).isTrue();
    }

    @Test
    void p95가_10초를_넘거나_단_한_건이라도_20초를_넘으면_실패한다() {
        List<Long> tenDurations = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            tenDurations.add(Duration.ofSeconds(index <= 9 ? 1 : 10).toNanos() + 1);
        }
        List<Long> ninetyDurations = new ArrayList<>();
        for (int index = 1; index <= 89; index++) {
            ninetyDurations.add(Duration.ofSeconds(1).toNanos());
        }
        ninetyDurations.add(Duration.ofSeconds(20).toNanos() + 1);

        assertThat(metricsForDurations(tenDurations).passed()).isFalse();
        AiRouteEvaluationMetrics overTwenty = metricsForDurations(ninetyDurations);
        assertThat(overTwenty.p95DurationNanos()).isEqualTo(Duration.ofSeconds(1).toNanos());
        assertThat(overTwenty.overTwentySecondsCases()).isEqualTo(1);
        assertThat(overTwenty.passed()).isFalse();
    }

    @Test
    void 사람_판정은_caseId로_결합하며_누락_추가_중복을_거부한다() {
        AiRouteEvaluationResult result = result(standardCases(16, 4, 1));
        List<AiRouteHumanJudgment> reversed = new ArrayList<>(judgments(result, 4));
        java.util.Collections.reverse(reversed);

        assertThat(AiRouteEvaluationMetrics.calculate(result, reversed).usefulRoutes())
                .isEqualTo(4);
        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        result, reversed.subList(0, 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("누락");
        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        result,
                        append(reversed, new AiRouteHumanJudgment("unknown", true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는");
        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        result, append(reversed, reversed.getFirst())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void 한_평가에_서로_다른_모델이나_정책_버전이_섞이면_거부한다() {
        List<AiRouteEvaluationResult.CompletedCase> cases = standardCases(16, 4, 1);
        AiRouteEvaluationResult.Versions changed = new AiRouteEvaluationResult.Versions(
                "embedding-v2", "route-v1", "candidate-v1", "prompt-v1", "schema-v1");
        List<AiRouteEvaluationResult.CompletedCase> mixed = new ArrayList<>(cases);
        mixed.set(4, completedCase(
                5, 20, 16, 20, 4, 20, 1, Duration.ofSeconds(10).toNanos(), changed));
        AiRouteEvaluationResult result = result(mixed);

        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        result, judgments(result, 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("버전");
    }

    @Test
    void Q01이_한_case라도_ROUTE로_끝내지_못한_결과는_거부한다() {
        AiRouteEvaluationResult failed = new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.FAILED,
                "ai-route-v2",
                MANIFEST_SHA,
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

        assertThatThrownBy(() -> AiRouteEvaluationMetrics.calculate(
                        failed, List.of(new AiRouteHumanJudgment("case-1", false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROUTE");
    }

    @Test
    void report는_manifest_SHA와_평가_revision과_모델_정책_버전으로만_재사용을_판정한다() {
        AiRouteEvaluationResult original = result(standardCases(16, 4, 1));
        AiRouteEvaluationReport report =
                AiRouteEvaluationReport.create(
                        original, judgments(original, 4), DATA_POLICY_VERSION);

        assertThat(report.reuseRejection(result(
                        MANIFEST_SHA, "다른-manifest-git", "evaluation-git", VERSIONS)))
                .isEmpty();
        assertThat(report.reuseRejection(result(
                        "b".repeat(64), "manifest-git", "evaluation-git", VERSIONS)))
                .get(InstanceOfAssertFactories.STRING)
                .contains("manifest SHA-256");
        assertThat(report.reuseRejection(result(
                        MANIFEST_SHA, "manifest-git", "evaluation-git-v2", VERSIONS)))
                .get(InstanceOfAssertFactories.STRING)
                .contains("평가 데이터 Git revision");

        List<AiRouteEvaluationResult.Versions> changedVersions = List.of(
                new AiRouteEvaluationResult.Versions(
                        "embedding-v2", "route-v1", "candidate-v1", "prompt-v1", "schema-v1"),
                new AiRouteEvaluationResult.Versions(
                        "embedding-v1", "route-v2", "candidate-v1", "prompt-v1", "schema-v1"),
                new AiRouteEvaluationResult.Versions(
                        "embedding-v1", "route-v1", "candidate-v2", "prompt-v1", "schema-v1"),
                new AiRouteEvaluationResult.Versions(
                        "embedding-v1", "route-v1", "candidate-v1", "prompt-v2", "schema-v1"),
                new AiRouteEvaluationResult.Versions(
                        "embedding-v1", "route-v1", "candidate-v1", "prompt-v1", "schema-v2"));
        assertThat(changedVersions)
                .allSatisfy(versions -> assertThat(report.reuseRejection(result(
                                        MANIFEST_SHA,
                                        "manifest-git",
                                        "evaluation-git",
                                        versions)))
                                .get(InstanceOfAssertFactories.STRING)
                                .contains("모델·정책 버전"));
    }

    @Test
    void report_artifact는_checksum을_제공하고_비공개_판정_입력을_기록하지_않는다()
            throws Exception {
        AiRouteEvaluationResult result = result(standardCases(16, 4, 1));
        AiRouteEvaluationReport report =
                AiRouteEvaluationReport.create(
                        result, judgments(result, 4), DATA_POLICY_VERSION);
        Path output = tempDir.resolve("report.json");

        AiRouteEvaluationReportWriter.Artifact artifact =
                new AiRouteEvaluationReportWriter(new ObjectMapper()).write(output, report);

        byte[] bytes = Files.readAllBytes(output);
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(artifact.path()).isEqualTo(output.toAbsolutePath().normalize());
        assertThat(artifact.sha256()).isEqualTo(HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(json)
                .contains(
                        "dataPolicyVersion",
                        "manifestSha256",
                        "humanJudgments",
                        "requiredConceptCoverageRate")
                .doesNotContain(
                        "routePages",
                        "comparison",
                        "primaryConcepts",
                        "purpose",
                        "analysisText",
                        "apiKey",
                        "providerRequest",
                        "providerResponse");
        assertThatThrownBy(() -> new AiRouteEvaluationReportWriter(new ObjectMapper())
                        .write(output, report))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("덮어쓰지");
    }

    @Test
    void 최종화_runner는_Q01_결과와_사람_판정을_읽어_report를_작성한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRouteEvaluationResult result = result(standardCases(16, 4, 1));
        List<AiRouteHumanJudgment> judgments = judgments(result, 4);
        AiRouteEvaluationProperties properties = finalizationProperties();
        Files.write(properties.output(), objectMapper.writeValueAsBytes(result));
        Files.write(properties.judgments(), objectMapper.writeValueAsBytes(judgments));
        AiRouteEvaluationArtifactReader reader =
                new AiRouteEvaluationArtifactReader(objectMapper);
        AiRouteEvaluationFinalizationRunner runner = new AiRouteEvaluationFinalizationRunner(
                properties,
                reader,
                new AiRouteEvaluationReportWriter(objectMapper),
                new OpenAiProperties(null, null, DATA_POLICY_VERSION));

        runner.run(new DefaultApplicationArguments());

        AiRouteEvaluationReport report =
                reader.readReport(properties.reportOutput());
        assertThat(report.metrics().passed()).isTrue();
        assertThat(report.targetBookIds())
                .containsExactlyElementsOf(result.completedCases().stream()
                        .map(AiRouteEvaluationResult.CompletedCase::bookId)
                        .toList());
    }

    @Test
    void 최종화_runner는_미통과_report를_기록하고_실패한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRouteEvaluationResult result = result(standardCases(15, 4, 1));
        AiRouteEvaluationProperties properties = finalizationProperties();
        Files.write(properties.output(), objectMapper.writeValueAsBytes(result));
        Files.write(
                properties.judgments(),
                objectMapper.writeValueAsBytes(judgments(result, 4)));
        AiRouteEvaluationArtifactReader reader =
                new AiRouteEvaluationArtifactReader(objectMapper);
        AiRouteEvaluationFinalizationRunner runner = new AiRouteEvaluationFinalizationRunner(
                properties,
                reader,
                new AiRouteEvaluationReportWriter(objectMapper),
                new OpenAiProperties(null, null, DATA_POLICY_VERSION));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("통과하지 못했습니다")
                .hasMessageContaining("옮기거나 지우세요");
        assertThat(reader.readReport(properties.reportOutput()).metrics().passed()).isFalse();
    }

    @Test
    void 활성화_runner는_기록된_report를_읽어_활성화_service를_호출한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiRouteEvaluationResult result = result(standardCases(16, 4, 1));
        AiRouteEvaluationReport report = AiRouteEvaluationReport.create(
                result, judgments(result, 4), DATA_POLICY_VERSION);
        AiRouteEvaluationProperties properties = finalizationProperties();
        Files.write(properties.output(), objectMapper.writeValueAsBytes(result));
        Files.write(properties.reportOutput(), objectMapper.writeValueAsBytes(report));
        AiRouteSupportActivationService activationService =
                mock(AiRouteSupportActivationService.class);
        when(activationService.activate(report))
                .thenReturn(new AiRouteSupportActivationService.Activation(
                        report.contentVersion(),
                        report.dataPolicyVersion(),
                        report.targetBookIds()));
        AiRouteSupportActivationRunner runner = new AiRouteSupportActivationRunner(
                properties,
                new AiRouteEvaluationArtifactReader(objectMapper),
                activationService);

        runner.run(new DefaultApplicationArguments());

        verify(activationService).activate(report);
    }

    @Test
    void evaluation_phase는_평가_최종화_활성화_runner를_하나씩만_선택한다() {
        phaseContext("evaluate").run(context -> {
            assertThat(context).hasSingleBean(AiRouteEvaluationRunner.class);
            assertThat(context).doesNotHaveBean(AiRouteEvaluationFinalizationRunner.class);
            assertThat(context).doesNotHaveBean(AiRouteSupportActivationRunner.class);
        });
        phaseContext("finalize").run(context -> {
            assertThat(context).doesNotHaveBean(AiRouteEvaluationRunner.class);
            assertThat(context).hasSingleBean(AiRouteEvaluationFinalizationRunner.class);
            assertThat(context).doesNotHaveBean(AiRouteSupportActivationRunner.class);
        });
        phaseContext("activate").run(context -> {
            assertThat(context).doesNotHaveBean(AiRouteEvaluationRunner.class);
            assertThat(context).doesNotHaveBean(AiRouteEvaluationFinalizationRunner.class);
            assertThat(context).hasSingleBean(AiRouteSupportActivationRunner.class);
        });
    }

    private ApplicationContextRunner phaseContext(String phase) {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=evaluation",
                        "ai-route-evaluation.phase=" + phase)
                .withBean(AiRouteEvaluationProperties.class, AiRouteEvaluationProperties::new)
                .withBean(AiRouteEvaluationReader.class, () -> mock(AiRouteEvaluationReader.class))
                .withBean(AiRouteEvaluationService.class, () -> mock(AiRouteEvaluationService.class))
                .withBean(
                        AiRouteEvaluationResultWriter.class,
                        () -> mock(AiRouteEvaluationResultWriter.class))
                .withBean(
                        AiRouteEvaluationArtifactReader.class,
                        () -> mock(AiRouteEvaluationArtifactReader.class))
                .withBean(
                        AiRouteEvaluationReportWriter.class,
                        () -> mock(AiRouteEvaluationReportWriter.class))
                .withBean(OpenAiProperties.class, () -> new OpenAiProperties(null, null, DATA_POLICY_VERSION))
                .withBean(
                        AiRouteSupportActivationService.class,
                        () -> mock(AiRouteSupportActivationService.class))
                .withUserConfiguration(
                        AiRouteEvaluationRunner.class,
                        AiRouteEvaluationFinalizationRunner.class,
                        AiRouteSupportActivationRunner.class);
    }

    private AiRouteEvaluationProperties finalizationProperties() {
        AiRouteEvaluationProperties properties = new AiRouteEvaluationProperties();
        properties.setOutput(tempDir.resolve("result.json"));
        properties.setJudgments(tempDir.resolve("judgments.json"));
        properties.setReportOutput(tempDir.resolve("report.json"));
        return properties;
    }

    private AiRouteEvaluationMetrics metrics(
            int coveredConcepts, int irrelevantPages, int violations, int usefulCases) {
        AiRouteEvaluationResult result =
                result(standardCases(coveredConcepts, irrelevantPages, violations));
        return AiRouteEvaluationMetrics.calculate(result, judgments(result, usefulCases));
    }

    private AiRouteEvaluationMetrics metricsForDurations(List<Long> durations) {
        List<AiRouteEvaluationResult.CompletedCase> cases = new ArrayList<>();
        for (int index = 0; index < durations.size(); index++) {
            cases.add(completedCase(
                    index + 1, 1, 1, 1, 0, 1, 0, durations.get(index), VERSIONS));
        }
        AiRouteEvaluationResult result = result(cases);
        return AiRouteEvaluationMetrics.calculate(result, judgments(result, durations.size()));
    }

    private List<AiRouteEvaluationResult.CompletedCase> standardCases(
            int coveredConcepts, int irrelevantPages, int violations) {
        List<Long> durations = List.of(
                Duration.ofSeconds(1).toNanos(),
                Duration.ofSeconds(2).toNanos(),
                Duration.ofSeconds(3).toNanos(),
                Duration.ofSeconds(4).toNanos(),
                Duration.ofSeconds(10).toNanos());
        List<AiRouteEvaluationResult.CompletedCase> cases = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            cases.add(completedCase(
                    index,
                    20,
                    coveredConcepts,
                    20,
                    irrelevantPages,
                    20,
                    violations,
                    durations.get(index - 1),
                    VERSIONS));
        }
        return List.copyOf(cases);
    }

    private AiRouteEvaluationResult.CompletedCase completedCase(
            int caseIndex,
            int requiredConceptCount,
            int coveredConceptCount,
            int routePageCount,
            int irrelevantPageCount,
            int prerequisiteCount,
            int violationCount,
            long durationNanos,
            AiRouteEvaluationResult.Versions versions) {
        List<AiRouteEvaluationResult.RoutePage> pages =
                routePages(caseIndex, routePageCount, coveredConceptCount);
        List<AiRouteEvaluationResult.Prerequisite> prerequisites = new ArrayList<>();
        for (int index = 0; index < prerequisiteCount; index++) {
            if (index < violationCount) {
                prerequisites.add(new AiRouteEvaluationResult.Prerequisite(
                        routePageCount + 1, 1));
            } else if (routePageCount == 1) {
                prerequisites.add(new AiRouteEvaluationResult.Prerequisite(1, 2));
            } else {
                int before = (index % (routePageCount - 1)) + 1;
                prerequisites.add(new AiRouteEvaluationResult.Prerequisite(before, before + 1));
            }
        }
        List<Integer> irrelevant = new ArrayList<>();
        for (int index = 0; index < irrelevantPageCount; index++) {
            irrelevant.add(routePageCount - index);
        }
        return new AiRouteEvaluationResult.CompletedCase(
                "case-" + caseIndex,
                100L + caseIndex,
                durationNanos,
                pages,
                new AiRouteEvaluationResult.Comparison(
                        concepts("case-" + caseIndex + "-concept-", requiredConceptCount),
                        List.of(),
                        prerequisites,
                        irrelevant,
                        List.of(),
                        List.of(),
                        List.of()),
                versions);
    }

    private List<AiRouteEvaluationResult.RoutePage> routePages(
            int caseIndex, int count, int coveredConceptCount) {
        List<AiRouteEvaluationResult.RoutePage> pages = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= count; pageNumber++) {
            String concept = pageNumber <= coveredConceptCount
                    ? "case-" + caseIndex + "-concept-" + pageNumber
                    : "other-" + caseIndex + "-" + pageNumber;
            pages.add(new AiRouteEvaluationResult.RoutePage(pageNumber, List.of(concept)));
        }
        return List.copyOf(pages);
    }

    private List<String> concepts(String prefix, int count) {
        List<String> concepts = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            concepts.add(prefix + index);
        }
        return List.copyOf(concepts);
    }

    private List<AiRouteHumanJudgment> judgments(
            AiRouteEvaluationResult result, int usefulCases) {
        List<AiRouteHumanJudgment> judgments = new ArrayList<>();
        for (int index = 0; index < result.completedCases().size(); index++) {
            judgments.add(new AiRouteHumanJudgment(
                    result.completedCases().get(index).caseId(), index < usefulCases));
        }
        return List.copyOf(judgments);
    }

    private List<AiRouteHumanJudgment> append(
            List<AiRouteHumanJudgment> source, AiRouteHumanJudgment value) {
        List<AiRouteHumanJudgment> copy = new ArrayList<>(source);
        copy.add(value);
        return List.copyOf(copy);
    }

    private AiRouteEvaluationResult result(
            List<AiRouteEvaluationResult.CompletedCase> cases) {
        return result(MANIFEST_SHA, "manifest-git", "evaluation-git", cases);
    }

    private AiRouteEvaluationResult result(
            String manifestSha,
            String manifestGitRevision,
            String evaluationGitRevision,
            AiRouteEvaluationResult.Versions versions) {
        return result(
                manifestSha,
                manifestGitRevision,
                evaluationGitRevision,
                List.of(completedCase(
                        1,
                        1,
                        1,
                        1,
                        0,
                        1,
                        0,
                        Duration.ofSeconds(1).toNanos(),
                        versions)));
    }

    private AiRouteEvaluationResult result(
            String manifestSha,
            String manifestGitRevision,
            String evaluationGitRevision,
            List<AiRouteEvaluationResult.CompletedCase> cases) {
        return new AiRouteEvaluationResult(
                AiRouteEvaluationResult.Status.SUCCESS,
                "ai-route-v2",
                manifestSha,
                manifestGitRevision,
                evaluationGitRevision,
                Instant.parse("2026-08-15T00:00:00Z"),
                cases,
                candidateReview(),
                null);
    }

    private AiRouteCandidateThresholdEvaluator.Result candidateReview() {
        return new AiRouteCandidateThresholdEvaluator.Result(
                0.40,
                List.of(new AiRouteCandidateThresholdEvaluator.ThresholdResult(
                        0.40, 95, 100, 0.95)));
    }
}
