package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotSupportedException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteAssembler;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteAssemblyPage;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGuideFactory;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidate;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePage;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidateSelection;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidateSelector;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteEmbedding;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteOutputValidator;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal;
import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.openai.AiRouteFeatureProperties;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.CandidatePage;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteContract;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteGatewayResult;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteInput;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** production과 비웹 평가가 함께 사용하는 사용자·영속 계층 독립 생성 엔진이다. */
@Service
@Conditional(AiRouteGenerationEngine.EngineRequiredCondition.class)
public class AiRouteGenerationEngine {

    private static final StageTimer UNTIMED = new StageTimer() {
        @Override
        public <T> T measure(Stage stage, Supplier<T> operation) {
            return operation.get();
        }
    };

    private final AiRouteFeatureProperties featureProperties;
    private final OpenAiProperties openAiProperties;
    private final BookService bookService;
    private final AiRoutePrerequisiteService prerequisiteService;
    private final OpenAiEmbeddingGateway embeddingGateway;
    private final OpenAiRouteGateway routeGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService externalCallExecutor;
    private final AiRouteCandidateSelector candidateSelector = new AiRouteCandidateSelector();
    private final AiRouteOutputValidator outputValidator = new AiRouteOutputValidator();
    private final AiRouteAssembler assembler = new AiRouteAssembler(new AiRouteGuideFactory());

    public AiRouteGenerationEngine(
            AiRouteFeatureProperties featureProperties,
            OpenAiProperties openAiProperties,
            BookService bookService,
            AiRoutePrerequisiteService prerequisiteService,
            OpenAiEmbeddingGateway embeddingGateway,
            OpenAiRouteGateway routeGateway,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.featureProperties = featureProperties;
        this.openAiProperties = openAiProperties;
        this.bookService = bookService;
        this.prerequisiteService = prerequisiteService;
        this.embeddingGateway = embeddingGateway;
        this.routeGateway = routeGateway;
        this.clock = clock;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        externalCallExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /** 평가가 사용자 행 없이 명시적 권한 사본만으로 운영과 같은 생성 단계를 실행하는 진입점이다. */
    public AiRouteEngineResult generate(
            AiRouteGenerationCommand command, AiRouteEntitlementSnapshot entitlement) {
        return generateMeasured(command, entitlement, UNTIMED);
    }

    /** 평가 artifact에 비민감 구간 시간만 남기며 운영 생성과 같은 단계를 실행한다. */
    public AiRouteEngineResult generateMeasured(
            AiRouteGenerationCommand command,
            AiRouteEntitlementSnapshot entitlement,
            StageTimer stageTimer) {
        if (stageTimer == null) {
            throw new InvalidAiRouteGenerationInputException("생성 구간 시간 측정기가 필요합니다.");
        }
        GenerationTimeBudget timeBudget = startTimeBudget();
        AiRouteGenerationSnapshot snapshot = stageTimer.measure(
                Stage.CONTENT_PREPARATION,
                () -> transactionTemplate.execute(status -> prepare(command, false)));
        if (snapshot == null) {
            throw new IllegalStateException("AI 경로 생성 콘텐츠 snapshot을 만들지 못했습니다.");
        }
        return generate(snapshot, entitlement, timeBudget, stageTimer);
    }

    /** production Facade가 멱등·사용자 상태를 확정하기 전에 콘텐츠 사본을 준비한다. */
    public AiRouteGenerationSnapshot prepareForProduction(AiRouteGenerationCommand command) {
        return prepare(command, true);
    }

    /** production Facade가 시작 행을 만든 뒤 준비된 동일 사본으로 외부 호출을 실행한다. */
    public AiRouteEngineResult generate(
            AiRouteGenerationSnapshot snapshot,
            AiRouteEntitlementSnapshot entitlement,
            GenerationTimeBudget timeBudget) {
        return generate(snapshot, entitlement, timeBudget, UNTIMED);
    }

    private AiRouteEngineResult generate(
            AiRouteGenerationSnapshot snapshot,
            AiRouteEntitlementSnapshot entitlement,
            GenerationTimeBudget timeBudget,
            StageTimer stageTimer) {
        if (snapshot == null || entitlement == null || timeBudget == null) {
            throw new InvalidAiRouteGenerationInputException(
                    "생성 콘텐츠·권한 사본과 제한 시간이 필요합니다.");
        }
        requireNoTransaction();
        AiRouteGenerationCommand command = snapshot.command();
        RouteContract routeContract = routeGateway.routeContract();
        OpenAiEmbeddingGateway.Embedding embedding = stageTimer.measure(
                Stage.PURPOSE_EMBEDDING,
                () -> timeBudget.call(() -> embeddingGateway.embedPurpose(
                        new OpenAiEmbeddingGateway.PurposeInput(command.normalizedPurpose()),
                        snapshot.embeddingModel(),
                        snapshot.embeddingDimensions())));
        AiRouteEmbedding purposeEmbedding = AiRouteEmbedding.of(
                embedding.model(),
                embedding.dimensions(),
                embedding.vector().stream().mapToDouble(Double::doubleValue).toArray());
        AiRouteCandidateSelection selection = stageTimer.measure(
                Stage.CANDIDATE_SELECTION,
                () -> {
                    AiRouteCandidateSelection selected = candidateSelector.select(
                            command.bookId(),
                            command.contentVersion(),
                            purposeEmbedding,
                            snapshot.candidatePages());
                    timeBudget.requireRemaining();
                    return selected;
                });

        if (selection.candidates().isEmpty()) {
            return result(
                    assembler.noRelevantPages(),
                    snapshot.embeddingModel(),
                    selection.candidatePolicyVersion(),
                    routeContract,
                    selection.scoredCandidates());
        }

        RouteInput routeInput = routeInput(snapshot, selection.candidates());
        ValidatedRouteProposal proposal = proposeValidatedRoute(
                snapshot, selection, routeInput, routeContract, timeBudget, stageTimer);
        AiRouteGenerationResult generation = stageTimer.measure(
                Stage.ROUTE_ASSEMBLY,
                () -> {
                    AiRouteGenerationResult assembled = assembler.assemble(
                            proposal, command, entitlement, snapshot.assemblyPages());
                    timeBudget.requireRemaining();
                    return assembled;
                });
        return result(
                generation,
                snapshot.embeddingModel(),
                selection.candidatePolicyVersion(),
                routeContract,
                selection.scoredCandidates());
    }

    public GenerationTimeBudget startTimeBudget() {
        return GenerationTimeBudget.start(clock, externalCallExecutor);
    }

    private AiRouteGenerationSnapshot prepare(
            AiRouteGenerationCommand command, boolean production) {
        if (command == null) {
            throw new InvalidAiRouteGenerationInputException("생성 명령이 필요합니다.");
        }
        Book book = bookService.findBook(command.bookId());
        requireSupported(book, command, production);

        List<BookPage> pages = bookService.findPages(command.bookId()).stream()
                .filter(BookPage::isAiRouteCandidate)
                .toList();
        if (pages.isEmpty()) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "AI 경로 후보 페이지가 없습니다.");
        }
        List<AiRoutePrerequisiteService.PrerequisiteEdge> prerequisites =
                prerequisiteService.findByBookId(command.bookId());
        requirePageMetadata(command, pages, prerequisites);
        return snapshotOf(command, pages, prerequisites);
    }

    private void requireSupported(
            Book book, AiRouteGenerationCommand command, boolean production) {
        if (production && !featureProperties.enabled()) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "서버의 AI 경로 기능이 비활성화되어 있습니다.");
        }
        if (!book.isAiExternalTransferAllowed()) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "도서의 외부 전송 권리가 확인되지 않았습니다.");
        }
        if (production && !book.isAiRouteSupported()) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "도서의 AI 경로 지원이 비활성화되어 있습니다.");
        }
        if (!Objects.equals(book.getAiDataPolicyVersion(), openAiProperties.dataPolicyVersion())) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "도서와 서버의 데이터 정책 버전이 다릅니다.");
        }
        if (!Objects.equals(book.getContentVersion(), command.contentVersion())) {
            throw new InvalidAiRouteGenerationInputException("도서의 현재 콘텐츠 버전과 입력이 다릅니다.");
        }
    }

    private void requirePageMetadata(
            AiRouteGenerationCommand command,
            List<BookPage> pages,
            List<AiRoutePrerequisiteService.PrerequisiteEdge> prerequisites) {
        String model = pages.getFirst().getEmbeddingModel();
        Integer dimensions = pages.getFirst().getEmbeddingDimensions();
        Set<Integer> pageNumbers = new HashSet<>();
        for (BookPage page : pages) {
            int pageNumber = page.getPageNumber();
            if (page.getId() == null) {
                throw invalidPageMetadata(command, pageNumber, "페이지 식별자가 없습니다.");
            }
            if (page.getAiAnalysisText() == null || page.getAiAnalysisText().isBlank()) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 분석 텍스트가 비어 있습니다.");
            }
            if (page.getAiPublicGuideTopic() == null || page.getAiPublicGuideTopic().isBlank()) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 공개 가이드 주제가 비어 있습니다.");
            }
            if (page.getEstimatedReadingSeconds() == null
                    || page.getEstimatedReadingSeconds() <= 0) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 예상 독서 시간이 양수가 아닙니다.");
            }
            if (page.getEmbeddingModel() == null) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 embedding 모델이 없습니다.");
            }
            if (!page.getEmbeddingModel().equals(model)) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 embedding 모델이 서로 다릅니다.");
            }
            if (page.getEmbeddingDimensions() == null) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 embedding 차원이 없습니다.");
            }
            if (!page.getEmbeddingDimensions().equals(dimensions)) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 embedding 차원이 서로 다릅니다.");
            }
            if (page.getEmbedding() == null) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 embedding vector가 없습니다.");
            }
            if (page.getDuplicateGroupKeys() == null) {
                throw invalidPageMetadata(command, pageNumber, "후보 페이지의 중복 그룹 목록이 없습니다.");
            }
            if (!pageNumbers.add(pageNumber)) {
                throw invalidPageMetadata(command, pageNumber, "페이지 번호가 중복됩니다.");
            }
        }
        if (model == null || model.isBlank()) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "후보 페이지의 embedding 모델이 비어 있습니다.");
        }
        if (dimensions == null || dimensions <= 0) {
            throw new AiRouteNotSupportedException(
                    command.bookId(), "후보 페이지의 embedding 차원이 양수가 아닙니다.");
        }
        for (AiRoutePrerequisiteService.PrerequisiteEdge edge : prerequisites) {
            if (!pageNumbers.contains(edge.prerequisitePageNumber())) {
                throw new AiRouteNotSupportedException(
                        command.bookId(),
                        "선수 페이지가 AI 경로 후보가 아닙니다: prerequisitePageNumber="
                                + edge.prerequisitePageNumber());
            }
        }
    }

    private AiRouteNotSupportedException invalidPageMetadata(
            AiRouteGenerationCommand command, int pageNumber, String reason) {
        return new AiRouteNotSupportedException(
                command.bookId(), reason + " pageNumber=" + pageNumber);
    }

    private AiRouteGenerationSnapshot snapshotOf(
            AiRouteGenerationCommand command,
            List<BookPage> pages,
            List<AiRoutePrerequisiteService.PrerequisiteEdge> prerequisites) {
        Map<Integer, List<Integer>> directPrerequisites = new HashMap<>();
        for (AiRoutePrerequisiteService.PrerequisiteEdge edge : prerequisites) {
            directPrerequisites
                    .computeIfAbsent(edge.dependentPageNumber(), ignored -> new ArrayList<>())
                    .add(edge.prerequisitePageNumber());
        }

        List<AiRouteCandidatePage> candidatePages = new ArrayList<>();
        List<AiRouteAssemblyPage> assemblyPages = new ArrayList<>();
        Map<String, String> analysisTexts = new LinkedHashMap<>();
        for (BookPage page : pages) {
            String analysisRef = Long.toString(page.getId());
            AiRouteEmbedding embedding = AiRouteEmbedding.of(
                    page.getEmbeddingModel(),
                    page.getEmbeddingDimensions(),
                    page.getEmbedding().stream().mapToDouble(Double::doubleValue).toArray());
            candidatePages.add(new AiRouteCandidatePage(
                    command.bookId(),
                    command.contentVersion(),
                    page.getId(),
                    page.getPageNumber(),
                    page.getContentType(),
                    embedding,
                    analysisRef,
                    directPrerequisites.getOrDefault(page.getPageNumber(), List.of())));
            assemblyPages.add(new AiRouteAssemblyPage(
                    page.getId(),
                    page.getPageNumber(),
                    page.getAiPublicGuideTopic(),
                    page.getEstimatedReadingSeconds(),
                    page.getDuplicateGroupKeys()));
            analysisTexts.put(analysisRef, page.getAiAnalysisText());
        }
        return new AiRouteGenerationSnapshot(
                command,
                pages.getFirst().getEmbeddingModel(),
                pages.getFirst().getEmbeddingDimensions(),
                candidatePages,
                assemblyPages,
                prerequisites,
                analysisTexts);
    }

    private RouteInput routeInput(
            AiRouteGenerationSnapshot snapshot, List<AiRouteCandidate> candidates) {
        List<CandidatePage> gatewayCandidates = candidates.stream()
                .map(candidate -> new CandidatePage(
                        candidate.pageNumber(),
                        snapshot.analysisText(candidate.analysisTextRef())))
                .toList();
        return new RouteInput(snapshot.command().normalizedPurpose(), gatewayCandidates);
    }

    private ValidatedRouteProposal proposeValidatedRoute(
            AiRouteGenerationSnapshot snapshot,
            AiRouteCandidateSelection selection,
            RouteInput routeInput,
            RouteContract routeContract,
            GenerationTimeBudget timeBudget,
            StageTimer stageTimer) {
        for (int attempt = 0; attempt < 2; attempt++) {
            RouteGatewayResult gatewayResult;
            try {
                gatewayResult = stageTimer.measure(
                        Stage.ROUTE_RESPONSE,
                        () -> timeBudget.call(() -> routeGateway.proposeRoute(routeInput)));
            } catch (OpenAiRouteException exception) {
                if (attempt == 0
                        && exception.failure() == OpenAiRouteException.Failure.MALFORMED_RESPONSE) {
                    continue;
                }
                throw exception;
            }

            try {
                return stageTimer.measure(
                        Stage.OUTPUT_VALIDATION,
                        () -> {
                            requireSameContract(routeContract, gatewayResult);
                            return outputValidator.validate(
                                    snapshot.command().bookId(),
                                    snapshot.command().contentVersion(),
                                    snapshot.candidatePages(),
                                    selection.candidates(),
                                    gatewayResult.proposal());
                        });
            } catch (AiRouteInvalidOutputException exception) {
                if (attempt == 0 && exception.failure().retryable()) {
                    continue;
                }
                throw exception;
            }
        }
        throw new IllegalStateException("검증 재시도 결과를 확정하지 못했습니다.");
    }

    private void requireSameContract(
            RouteContract contract, RouteGatewayResult result) {
        if (!Objects.equals(contract.promptVersion(), result.promptVersion())
                || !Objects.equals(contract.schemaVersion(), result.schemaVersion())) {
            throw AiRouteInvalidOutputException.retryContractChanged();
        }
    }

    private AiRouteEngineResult result(
            AiRouteGenerationResult generation,
            String embeddingModel,
            String candidatePolicyVersion,
            RouteContract routeContract,
            List<AiRouteCandidate> scoredCandidates) {
        return new AiRouteEngineResult(
                generation,
                embeddingModel,
                routeContract.model(),
                candidatePolicyVersion,
                routeContract.promptVersion(),
                routeContract.schemaVersion(),
                scoredCandidates.stream()
                        .map(candidate -> new AiRouteEngineResult.CandidateScore(
                                candidate.pageNumber(), candidate.similarity()))
                        .toList());
    }

    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("외부 호출 구간에는 DB transaction이 없어야 합니다.");
        }
    }

    @PreDestroy
    void closeExternalCallExecutor() {
        externalCallExecutor.shutdownNow();
    }

    public enum Stage {
        CONTENT_PREPARATION,
        PURPOSE_EMBEDDING,
        CANDIDATE_SELECTION,
        ROUTE_RESPONSE,
        OUTPUT_VALIDATION,
        ROUTE_ASSEMBLY
    }

    public interface StageTimer {

        <T> T measure(Stage stage, Supplier<T> operation);
    }

    static final class EngineRequiredCondition extends AnyNestedCondition {

        EngineRequiredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
        static final class AiRouteEnabled {}

        @Profile("evaluation")
        static final class EvaluationProfile {}
    }
}
