package com.example.ilgeobolkka.airoute.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.validation.AiRouteContentValidationException;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class AiRouteEvaluationReaderTest {

    private static final String SHA256 = "a".repeat(64);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDirectory;

    @Test
    void 최신_fixture의_case_전체를_건수_상수_없이_bookId_오름차순으로_읽는다() throws Exception {
        Path manifest = Path.of("fixtures/content/ai-route-v2/manifest.json");
        Path evaluation = Path.of("fixtures/content/ai-route-v2/evaluation.json");
        AiRouteEvaluationProperties properties = properties(manifest, evaluation);
        List<Long> expectedBookIds = new ContentManifestParser(objectMapper)
                .parseEvaluation(Files.readAllBytes(evaluation))
                .cases()
                .stream()
                .map(AiRouteEvaluationDataset.EvaluationCase::bookId)
                .sorted()
                .toList();

        AiRouteEvaluationPlan plan = reader(properties, "OPENAI_DEFAULT_RETENTION_V1").read();

        assertThat(plan.cases())
                .extracting(evaluationCase -> evaluationCase.input().bookId())
                .isSorted()
                .containsExactlyElementsOf(expectedBookIds);
        assertThat(plan.manifestSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void 지원_도서_10권짜리_입력도_건수_때문에_거부하지_않는다() throws Exception {
        Fixture fixture = fixture(10, scenarios(10));
        AiRouteEvaluationProperties properties = write(fixture);

        AiRouteEvaluationPlan plan = reader(properties).read();

        assertThat(plan.cases()).hasSize(10);
    }

    @Test
    void 중복_bookId는_실행_계획을_만들기_전에_거부한다() throws Exception {
        List<Scenario> scenarios = scenarios(7);
        Fixture base = fixture(7, scenarios);
        List<AiRouteEvaluationDataset.EvaluationCase> cases = new ArrayList<>(base.evaluation().cases());
        cases.set(6, copy(cases.get(6), cases.getFirst().bookId(), cases.get(6).caseId()));
        AiRouteEvaluationProperties properties = write(new Fixture(
                base.manifest(),
                new AiRouteEvaluationDataset("ai-route-v2", cases)));

        assertThatThrownBy(() -> reader(properties).read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bookId가 중복");
    }

    @Test
    void manifest_지원_도서의_case가_하나라도_없으면_거부한다() throws Exception {
        Fixture fixture = fixture(8, scenarios(7));
        AiRouteEvaluationProperties properties = write(fixture);

        assertThatThrownBy(() -> reader(properties).read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("평가 case가 누락");
    }

    @Test
    void 일곱_시나리오_중_하나라도_없으면_거부한다() throws Exception {
        List<Scenario> scenarios = scenarios(7);
        scenarios.set(6, Scenario.OWNED_QUICK);
        AiRouteEvaluationProperties properties = write(fixture(7, scenarios));

        assertThatThrownBy(() -> reader(properties).read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수 평가 시나리오가 누락")
                .hasMessageContaining("OWNED_DEEP");
    }

    @Test
    void 중복_caseId는_기존_parser가_거부한다() throws Exception {
        Fixture base = fixture(7, scenarios(7));
        List<AiRouteEvaluationDataset.EvaluationCase> cases = new ArrayList<>(base.evaluation().cases());
        cases.set(6, copy(cases.get(6), cases.get(6).bookId(), cases.getFirst().caseId()));
        AiRouteEvaluationProperties properties = write(new Fixture(
                base.manifest(),
                new AiRouteEvaluationDataset("ai-route-v2", cases)));

        assertThatThrownBy(() -> reader(properties).read())
                .hasMessageContaining("caseId가 중복");
    }

    @Test
    void 정답_페이지가_필수_개념을_덮지_않으면_실행_계획을_거부한다() throws Exception {
        Fixture base = fixture(7, scenarios(7));
        List<AiRouteContentManifest.Book> books = new ArrayList<>(base.manifest().books());
        AiRouteContentManifest.Book firstBook = books.getFirst();
        AiRouteContentManifest.Page nonReferencePage = new AiRouteContentManifest.Page(
                2,
                "장",
                "절",
                List.of("정답에 없는 필수 개념"),
                List.of(),
                AiRouteContentManifest.ContentRole.CORE,
                true,
                "분석-추가",
                SHA256,
                "공개 주제-추가",
                60,
                List.of(),
                List.of());
        books.set(
                0,
                new AiRouteContentManifest.Book(
                        firstBook.bookId(),
                        firstBook.title(),
                        firstBook.pdfPath(),
                        firstBook.pdfSha256(),
                        2,
                        firstBook.aiRouteCandidate(),
                        firstBook.aiExternalTransferAllowed(),
                        List.of(firstBook.pages().getFirst(), nonReferencePage)));

        List<AiRouteEvaluationDataset.EvaluationCase> cases =
                new ArrayList<>(base.evaluation().cases());
        AiRouteEvaluationDataset.EvaluationCase firstCase = cases.getFirst();
        cases.set(
                0,
                new AiRouteEvaluationDataset.EvaluationCase(
                        firstCase.caseId(),
                        firstCase.bookId(),
                        firstCase.purpose(),
                        firstCase.owned(),
                        firstCase.maxAdditionalInk(),
                        firstCase.depth(),
                        firstCase.activeRentalPageNumbers(),
                        List.of("정답에 없는 필수 개념"),
                        firstCase.helpfulConcepts(),
                        firstCase.requiredPrerequisites(),
                        firstCase.irrelevantPageNumbers(),
                        firstCase.duplicatePageGroups(),
                        firstCase.referencePageNumbers(),
                        firstCase.allowedAlternativePageNumbers()));
        AiRouteEvaluationProperties properties = write(new Fixture(
                new AiRouteContentManifest(
                        base.manifest().contentVersion(),
                        base.manifest().dataPolicyVersion(),
                        base.manifest().embeddingModel(),
                        base.manifest().embeddingDimensions(),
                        books),
                new AiRouteEvaluationDataset(base.evaluation().contentVersion(), cases)));

        assertThatThrownBy(() -> reader(properties).read())
                .isInstanceOf(AiRouteContentValidationException.class)
                .hasMessageContaining("referencePageNumbers의 후보 페이지 primaryConcepts");
    }

    @Test
    void manifest와_환경의_dataPolicyVersion이_다르면_실행_계획을_거부한다() throws Exception {
        AiRouteEvaluationProperties properties = write(fixture(7, scenarios(7)));

        assertThatThrownBy(() -> reader(properties, "policy-v2").read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataPolicyVersion")
                .hasMessageContaining("manifest")
                .hasMessageContaining("환경");
    }

    private AiRouteEvaluationProperties write(Fixture fixture) throws Exception {
        Path manifest = tempDirectory.resolve("manifest.json");
        Path evaluation = tempDirectory.resolve("evaluation.json");
        objectMapper.writeValue(manifest.toFile(), fixture.manifest());
        objectMapper.writeValue(evaluation.toFile(), fixture.evaluation());
        return properties(manifest, evaluation);
    }

    private AiRouteEvaluationProperties properties(Path manifest, Path evaluation) {
        AiRouteEvaluationProperties properties = new AiRouteEvaluationProperties();
        properties.setManifest(manifest);
        properties.setEvaluation(evaluation);
        properties.setManifestGitRevision("manifest-revision");
        properties.setEvaluationGitRevision("evaluation-revision");
        return properties;
    }

    private AiRouteEvaluationReader reader(AiRouteEvaluationProperties properties) {
        return reader(properties, "policy-v1");
    }

    private AiRouteEvaluationReader reader(
            AiRouteEvaluationProperties properties, String dataPolicyVersion) {
        OpenAiProperties openAiProperties =
                new OpenAiProperties("test-project", "test-key", dataPolicyVersion);
        return new AiRouteEvaluationReader(properties, openAiProperties, objectMapper);
    }

    private Fixture fixture(int bookCount, List<Scenario> caseScenarios) {
        List<AiRouteContentManifest.Book> books = new ArrayList<>();
        for (int index = 1; index <= bookCount; index++) {
            books.add(book(index));
        }
        List<AiRouteEvaluationDataset.EvaluationCase> cases = new ArrayList<>();
        for (int index = 1; index <= caseScenarios.size(); index++) {
            cases.add(evaluationCase(index, caseScenarios.get(index - 1)));
        }
        return new Fixture(
                new AiRouteContentManifest(
                        "ai-route-v2", "policy-v1", "embedding-v1", 2, books),
                new AiRouteEvaluationDataset("ai-route-v2", cases));
    }

    private AiRouteContentManifest.Book book(long bookId) {
        AiRouteContentManifest.Page page = new AiRouteContentManifest.Page(
                1,
                "장",
                "절",
                List.of("개념-" + bookId),
                List.of("도움-" + bookId),
                AiRouteContentManifest.ContentRole.CORE,
                true,
                "분석-" + bookId,
                SHA256,
                "공개 주제-" + bookId,
                60,
                List.of(),
                List.of());
        return new AiRouteContentManifest.Book(
                bookId,
                "도서-" + bookId,
                "pdfs/book-%03d.pdf".formatted(bookId),
                SHA256,
                1,
                true,
                true,
                List.of(page));
    }

    private AiRouteEvaluationDataset.EvaluationCase evaluationCase(
            long bookId, Scenario scenario) {
        boolean owned = scenario.owned();
        return new AiRouteEvaluationDataset.EvaluationCase(
                "case-" + bookId,
                bookId,
                "목적-" + bookId,
                owned,
                owned ? null : scenario.budget(),
                owned ? scenario.depth() : null,
                scenario == Scenario.BUDGET_0 ? List.of(1) : List.of(),
                List.of("개념-" + bookId),
                List.of("도움-" + bookId),
                List.of(),
                List.of(),
                List.of(),
                List.of(1),
                List.of());
    }

    private AiRouteEvaluationDataset.EvaluationCase copy(
            AiRouteEvaluationDataset.EvaluationCase source, long bookId, String caseId) {
        return new AiRouteEvaluationDataset.EvaluationCase(
                caseId,
                bookId,
                source.purpose(),
                source.owned(),
                source.maxAdditionalInk(),
                source.depth(),
                source.activeRentalPageNumbers(),
                source.requiredConcepts(),
                source.helpfulConcepts(),
                source.requiredPrerequisites(),
                source.irrelevantPageNumbers(),
                source.duplicatePageGroups(),
                source.referencePageNumbers(),
                source.allowedAlternativePageNumbers());
    }

    private List<Scenario> scenarios(int count) {
        List<Scenario> values = List.of(Scenario.values());
        List<Scenario> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(values.get(index % values.size()));
        }
        return result;
    }

    private record Fixture(
            AiRouteContentManifest manifest, AiRouteEvaluationDataset evaluation) {}

    private enum Scenario {
        BUDGET_0(false, 0, null),
        BUDGET_5(false, 5, null),
        BUDGET_10(false, 10, null),
        BUDGET_15(false, 15, null),
        OWNED_QUICK(true, null, AiRouteEvaluationDataset.Depth.QUICK),
        OWNED_BALANCED(true, null, AiRouteEvaluationDataset.Depth.BALANCED),
        OWNED_DEEP(true, null, AiRouteEvaluationDataset.Depth.DEEP);

        private final boolean owned;
        private final Integer budget;
        private final AiRouteEvaluationDataset.Depth depth;

        Scenario(boolean owned, Integer budget, AiRouteEvaluationDataset.Depth depth) {
            this.owned = owned;
            this.budget = budget;
            this.depth = depth;
        }

        boolean owned() {
            return owned;
        }

        Integer budget() {
            return budget;
        }

        AiRouteEvaluationDataset.Depth depth() {
            return depth;
        }
    }
}
