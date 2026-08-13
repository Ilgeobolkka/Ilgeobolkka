package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotSupportedException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteEmbeddingException;
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
import com.example.ilgeobolkka.airoute.service.generation.AiRouteEntitlementSnapshotFactory;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationFailureCode;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationStartService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationView;
import com.example.ilgeobolkka.airoute.service.generation.AiRoutePrerequisiteService;
import com.example.ilgeobolkka.airoute.service.generation.AiRoutePrerequisiteService.PrerequisiteEdge;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteResultItem;
import com.example.ilgeobolkka.airoute.service.generation.GenerationExecutionResult;
import com.example.ilgeobolkka.airoute.service.generation.GenerationStartResult;
import com.example.ilgeobolkka.airoute.service.generation.GenerationTimeBudget;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteOutputValidator;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal;
import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.openai.AiRouteFeatureProperties;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingException;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.CandidatePage;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteGatewayResult;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteInput;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.rental.service.RentalService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 짧은 DB 단계 사이에서 OpenAI 호출과 서버 검증을 조율하는 G07 유스케이스 진입점이다. */
@Service
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteGenerationFacade {

    private static final Logger log = LoggerFactory.getLogger(AiRouteGenerationFacade.class);

    private final AiRouteFeatureProperties featureProperties;
    private final OpenAiProperties openAiProperties;
    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final RentalService rentalService;
    private final InkService inkService;
    private final AiRoutePrerequisiteService prerequisiteService;
    private final AiRouteGenerationStartService startService;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final OpenAiEmbeddingGateway embeddingGateway;
    private final OpenAiRouteGateway routeGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService externalCallExecutor;
    private final AiRouteEntitlementSnapshotFactory entitlementSnapshotFactory =
            new AiRouteEntitlementSnapshotFactory();
    private final AiRouteCandidateSelector candidateSelector = new AiRouteCandidateSelector();
    private final AiRouteOutputValidator outputValidator = new AiRouteOutputValidator();
    private final AiRouteAssembler assembler = new AiRouteAssembler(new AiRouteGuideFactory());

    public AiRouteGenerationFacade(
            AiRouteFeatureProperties featureProperties,
            OpenAiProperties openAiProperties,
            BookService bookService,
            OwnershipService ownershipService,
            RentalService rentalService,
            InkService inkService,
            AiRoutePrerequisiteService prerequisiteService,
            AiRouteGenerationStartService startService,
            AiRouteGenerationLifecycleService lifecycleService,
            OpenAiEmbeddingGateway embeddingGateway,
            OpenAiRouteGateway routeGateway,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.featureProperties = featureProperties;
        this.openAiProperties = openAiProperties;
        this.bookService = bookService;
        this.ownershipService = ownershipService;
        this.rentalService = rentalService;
        this.inkService = inkService;
        this.prerequisiteService = prerequisiteService;
        this.startService = startService;
        this.lifecycleService = lifecycleService;
        this.embeddingGateway = embeddingGateway;
        this.routeGateway = routeGateway;
        this.clock = clock;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        externalCallExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public GenerationExecutionResult generate(
            long readerId, UUID idempotencyKey, AiRouteGenerationCommand command) {
        GenerationTimeBudget timeBudget = GenerationTimeBudget.start(clock, externalCallExecutor);
        GenerationContext context = transactionTemplate.execute(
                status -> prepare(readerId, command));
        if (context == null) {
            throw new IllegalStateException("AI 경로 생성 snapshot을 만들지 못했습니다.");
        }

        GenerationStartResult start = startService.startBefore(
                readerId, idempotencyKey, command, timeBudget.deadline());
        if (start.kind() != GenerationStartResult.Kind.NEW) {
            return existingResult(readerId, start);
        }

        UUID generationId = start.generationId();
        try {
            // G05 transaction을 기다리는 동안 만료할 수도 있으므로 외부 호출 직전에 다시 확인한다.
            timeBudget.requireRemaining();
            return executeNew(readerId, generationId, command, context, timeBudget);
        } catch (GenerationTimeBudget.TimeLimitExceededException exception) {
            return fail(readerId, generationId, AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT);
        } catch (OpenAiEmbeddingException exception) {
            return fail(readerId, generationId, embeddingFailure(exception, timeBudget));
        } catch (InvalidAiRouteEmbeddingException exception) {
            return fail(
                    readerId,
                    generationId,
                    AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE);
        } catch (OpenAiRouteException exception) {
            return fail(readerId, generationId, routeFailure(exception, timeBudget));
        } catch (AiRouteInvalidOutputException exception) {
            return fail(readerId, generationId, AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT);
        } catch (RetryContractChangedException exception) {
            return fail(readerId, generationId, AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT);
        } catch (RuntimeException exception) {
            return failUnexpected(readerId, generationId, exception);
        }
    }

    private GenerationExecutionResult executeNew(
            long readerId,
            UUID generationId,
            AiRouteGenerationCommand command,
            GenerationContext context,
            GenerationTimeBudget timeBudget) {
        requireNoTransaction();
        OpenAiEmbeddingGateway.Embedding embedding = timeBudget.call(() ->
                embeddingGateway.embedPurpose(
                        new OpenAiEmbeddingGateway.PurposeInput(command.normalizedPurpose()),
                        context.embeddingModel(),
                        context.embeddingDimensions()));
        AiRouteEmbedding purposeEmbedding = AiRouteEmbedding.of(
                embedding.model(),
                embedding.dimensions(),
                embedding.vector().stream().mapToDouble(Double::doubleValue).toArray());
        AiRouteCandidateSelection selection = candidateSelector.select(
                command.bookId(), command.contentVersion(), purposeEmbedding, context.candidatePages());
        timeBudget.requireRemaining();

        if (selection.candidates().isEmpty()) {
            complete(generationId, assembler.noRelevantPages());
            return createdResult(readerId, generationId);
        }

        RouteInput routeInput = routeInput(command, context, selection.candidates());
        ValidatedRouteProposal proposal = proposeValidatedRoute(
                command, context, selection, routeInput, timeBudget);
        AiRouteGenerationResult result = assembler.assemble(
                proposal, command, context.entitlement(), context.assemblyPages());
        timeBudget.requireRemaining();
        complete(generationId, result);
        return createdResult(readerId, generationId);
    }

    private ValidatedRouteProposal proposeValidatedRoute(
            AiRouteGenerationCommand command,
            GenerationContext context,
            AiRouteCandidateSelection selection,
            RouteInput routeInput,
            GenerationTimeBudget timeBudget) {
        RouteGatewayResult firstSemanticInvalid = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            RouteGatewayResult gatewayResult;
            try {
                gatewayResult = timeBudget.call(() -> routeGateway.proposeRoute(routeInput));
            } catch (OpenAiRouteException exception) {
                if (attempt == 0
                        && exception.failure() == OpenAiRouteException.Failure.MALFORMED_RESPONSE) {
                    continue;
                }
                throw exception;
            }

            if (firstSemanticInvalid != null) {
                requireSameVersions(firstSemanticInvalid, gatewayResult);
            }
            try {
                return outputValidator.validate(
                        command.bookId(),
                        command.contentVersion(),
                        context.candidatePages(),
                        selection.candidates(),
                        gatewayResult.proposal());
            } catch (AiRouteInvalidOutputException exception) {
                if (attempt == 0 && exception.failure().retryable()) {
                    firstSemanticInvalid = gatewayResult;
                    continue;
                }
                throw exception;
            }
        }
        throw new IllegalStateException("검증 재시도 결과를 확정하지 못했습니다.");
    }

    private void requireSameVersions(RouteGatewayResult first, RouteGatewayResult retried) {
        if (!Objects.equals(first.promptVersion(), retried.promptVersion())
                || !Objects.equals(first.schemaVersion(), retried.schemaVersion())) {
            throw new RetryContractChangedException();
        }
    }

    private void complete(UUID generationId, AiRouteGenerationResult result) {
        if (result.status() == AiRouteGenerationResult.Status.ROUTE) {
            lifecycleService.completeWithRoute(
                    generationId,
                    result.items().stream()
                            .map(item -> new AiRouteResultItem(
                                    item.pageId(),
                                    item.position(),
                                    item.relevance(),
                                    item.prerequisite(),
                                    item.role()))
                            .toList());
            return;
        }

        lifecycleService.completeWithoutRoute(
                generationId,
                AiRouteNoRouteReason.valueOf(result.noRouteReason().name()),
                result.minimumRequiredInk());
    }

    private RouteInput routeInput(
            AiRouteGenerationCommand command,
            GenerationContext context,
            List<AiRouteCandidate> candidates) {
        List<CandidatePage> gatewayCandidates = candidates.stream()
                .map(candidate -> new CandidatePage(
                        candidate.pageNumber(), context.analysisText(candidate.analysisTextRef())))
                .toList();
        List<OpenAiRouteGateway.PrerequisiteEdge> gatewayEdges = context.prerequisites().stream()
                .map(edge -> new OpenAiRouteGateway.PrerequisiteEdge(
                        edge.prerequisitePageNumber(), edge.dependentPageNumber()))
                .toList();
        return new RouteInput(command.normalizedPurpose(), gatewayCandidates, gatewayEdges);
    }

    private GenerationContext prepare(long readerId, AiRouteGenerationCommand command) {
        if (command == null) {
            throw new InvalidAiRouteGenerationInputException("생성 명령이 필요합니다.");
        }
        Book book = bookService.findBook(command.bookId());
        requireSupported(book, command);

        List<BookPage> pages = bookService.findPages(command.bookId()).stream()
                .filter(BookPage::isAiRouteCandidate)
                .toList();
        if (pages.isEmpty()) {
            throw new AiRouteNotSupportedException(command.bookId());
        }
        List<PrerequisiteEdge> prerequisites = prerequisiteService.findByBookId(command.bookId());
        requirePageMetadata(command, pages, prerequisites);

        Instant now = clock.instant();
        boolean owned = ownershipService.isOwned(readerId, command.bookId());
        int inkBalance = owned ? 0 : inkService.getBalance(readerId);
        Set<Long> activeRentalPageIds = new HashSet<>();
        if (!owned) {
            for (BookPage page : pages) {
                if (rentalService.findActiveRental(readerId, page.getId(), now).isPresent()) {
                    activeRentalPageIds.add(page.getId());
                }
            }
        }
        AiRouteEntitlementSnapshot entitlement = entitlementSnapshotFactory.create(
                command, owned, inkBalance, activeRentalPageIds);
        return contextOf(command, pages, prerequisites, entitlement);
    }

    private void requireSupported(Book book, AiRouteGenerationCommand command) {
        if (!featureProperties.enabled()
                || !book.isAiRouteSupported()
                || !book.isAiExternalTransferAllowed()
                || !Objects.equals(book.getAiDataPolicyVersion(), openAiProperties.dataPolicyVersion())) {
            throw new AiRouteNotSupportedException(command.bookId());
        }
        if (!Objects.equals(book.getContentVersion(), command.contentVersion())) {
            throw new InvalidAiRouteGenerationInputException("도서의 현재 콘텐츠 버전과 입력이 다릅니다.");
        }
    }

    private void requirePageMetadata(
            AiRouteGenerationCommand command,
            List<BookPage> pages,
            List<PrerequisiteEdge> prerequisites) {
        String model = pages.getFirst().getEmbeddingModel();
        Integer dimensions = pages.getFirst().getEmbeddingDimensions();
        Set<Integer> pageNumbers = new HashSet<>();
        for (BookPage page : pages) {
            if (page.getId() == null
                    || page.getAiAnalysisText() == null
                    || page.getAiAnalysisText().isBlank()
                    || page.getAiPublicGuideTopic() == null
                    || page.getAiPublicGuideTopic().isBlank()
                    || page.getEstimatedReadingSeconds() == null
                    || page.getEstimatedReadingSeconds() <= 0
                    || page.getEmbeddingModel() == null
                    || !page.getEmbeddingModel().equals(model)
                    || page.getEmbeddingDimensions() == null
                    || !page.getEmbeddingDimensions().equals(dimensions)
                    || page.getEmbedding() == null
                    || page.getDuplicateGroupKeys() == null
                    || !pageNumbers.add(page.getPageNumber())) {
                throw new AiRouteNotSupportedException(command.bookId());
            }
        }
        if (model == null || model.isBlank() || dimensions == null || dimensions <= 0) {
            throw new AiRouteNotSupportedException(command.bookId());
        }
        for (PrerequisiteEdge edge : prerequisites) {
            if (!pageNumbers.contains(edge.prerequisitePageNumber())
                    || !pageNumbers.contains(edge.dependentPageNumber())) {
                throw new AiRouteNotSupportedException(command.bookId());
            }
        }
    }

    private GenerationContext contextOf(
            AiRouteGenerationCommand command,
            List<BookPage> pages,
            List<PrerequisiteEdge> prerequisites,
            AiRouteEntitlementSnapshot entitlement) {
        Map<Integer, List<Integer>> directPrerequisites = new HashMap<>();
        for (PrerequisiteEdge edge : prerequisites) {
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
        return new GenerationContext(
                pages.getFirst().getEmbeddingModel(),
                pages.getFirst().getEmbeddingDimensions(),
                candidatePages,
                assemblyPages,
                prerequisites,
                analysisTexts,
                entitlement);
    }

    private GenerationExecutionResult existingResult(long readerId, GenerationStartResult start) {
        return switch (start.kind()) {
            case EXISTING_GENERATING, EXISTING_FINAL -> GenerationExecutionResult.replayed(
                    findGeneration(readerId, start.generationId()));
            case KEY_REUSED -> GenerationExecutionResult.keyReused();
            case DAILY_LIMIT -> GenerationExecutionResult.dailyLimit();
            case TIMEOUT -> GenerationExecutionResult.timedOutBeforeStart();
            case NEW -> throw new IllegalStateException("새 생성은 기존 결과로 변환할 수 없습니다.");
        };
    }

    private GenerationExecutionResult createdResult(long readerId, UUID generationId) {
        return GenerationExecutionResult.created(findGeneration(readerId, generationId));
    }

    private GenerationExecutionResult fail(
            long readerId, UUID generationId, AiRouteGenerationFailureCode failureCode) {
        lifecycleService.fail(generationId, failureCode.name());
        return createdResult(readerId, generationId);
    }

    private GenerationExecutionResult failUnexpected(
            long readerId, UUID generationId, RuntimeException original) {
        // 예외 메시지는 분석 텍스트나 공급자 원문을 포함할 수 있어 안전한 종류만 기록한다.
        log.error(
                "AI 경로 생성 예상 밖 실패 generationId={} exceptionType={}",
                generationId,
                original.getClass().getSimpleName());
        try {
            return fail(
                    readerId,
                    generationId,
                    AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE);
        } catch (RuntimeException failure) {
            original.addSuppressed(failure);
            throw original;
        }
    }

    private AiRouteGenerationView findGeneration(long readerId, UUID generationId) {
        return lifecycleService
                .findOwnedResult(generationId, readerId)
                .orElseThrow(() -> new IllegalStateException("방금 확정한 생성 결과를 찾지 못했습니다."));
    }

    private AiRouteGenerationFailureCode embeddingFailure(
            OpenAiEmbeddingException exception, GenerationTimeBudget timeBudget) {
        if (timeBudget.expired()) {
            return AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT;
        }
        return exception.failure() == OpenAiEmbeddingException.Failure.BUDGET_LIMIT
                ? AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE
                : AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE;
    }

    private AiRouteGenerationFailureCode routeFailure(
            OpenAiRouteException exception, GenerationTimeBudget timeBudget) {
        if (timeBudget.expired()) {
            return AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT;
        }
        return switch (exception.failure()) {
            case BUDGET_LIMIT -> AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE;
            case MALFORMED_RESPONSE -> AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT;
            case TEMPORARY, TIMEOUT_OR_INCOMPLETE, REFUSAL ->
                    AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE;
        };
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

    private record GenerationContext(
            String embeddingModel,
            int embeddingDimensions,
            List<AiRouteCandidatePage> candidatePages,
            List<AiRouteAssemblyPage> assemblyPages,
            List<PrerequisiteEdge> prerequisites,
            Map<String, String> analysisTexts,
            AiRouteEntitlementSnapshot entitlement) {

        private GenerationContext {
            candidatePages = List.copyOf(candidatePages);
            assemblyPages = List.copyOf(assemblyPages);
            prerequisites = List.copyOf(prerequisites);
            analysisTexts = Map.copyOf(analysisTexts);
        }

        private String analysisText(String reference) {
            String text = analysisTexts.get(reference);
            if (text == null) {
                throw new IllegalStateException("후보 페이지의 분석 텍스트를 찾지 못했습니다.");
            }
            return text;
        }
    }

    private static final class RetryContractChangedException extends RuntimeException {

        private RetryContractChangedException() {
            super("검증 재시도에서 prompt 또는 schema version이 바뀌었습니다.");
        }
    }
}
