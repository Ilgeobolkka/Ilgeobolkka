package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteEmbeddingException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteEngineResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteEntitlementSnapshotFactory;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationEngine;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationFailureCode;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationSnapshot;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationStartService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationView;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteResultItem;
import com.example.ilgeobolkka.airoute.service.generation.GenerationExecutionResult;
import com.example.ilgeobolkka.airoute.service.generation.GenerationStartResult;
import com.example.ilgeobolkka.airoute.service.generation.GenerationTimeBudget;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingException;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.rental.service.RentalService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 사용자 상태·멱등·생명주기를 조율하고 실제 생성은 공용 Engine에 위임하는 G07 진입점이다. */
@Service
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteGenerationFacade {

    private static final Logger log = LoggerFactory.getLogger(AiRouteGenerationFacade.class);

    private final OwnershipService ownershipService;
    private final RentalService rentalService;
    private final InkService inkService;
    private final AiRouteGenerationStartService startService;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final AiRouteGenerationEngine generationEngine;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AiRouteEntitlementSnapshotFactory entitlementSnapshotFactory =
            new AiRouteEntitlementSnapshotFactory();

    public AiRouteGenerationFacade(
            OwnershipService ownershipService,
            RentalService rentalService,
            InkService inkService,
            AiRouteGenerationStartService startService,
            AiRouteGenerationLifecycleService lifecycleService,
            AiRouteGenerationEngine generationEngine,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.ownershipService = ownershipService;
        this.rentalService = rentalService;
        this.inkService = inkService;
        this.startService = startService;
        this.lifecycleService = lifecycleService;
        this.generationEngine = generationEngine;
        this.clock = clock;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
    }

    public GenerationExecutionResult generate(
            long readerId, UUID idempotencyKey, AiRouteGenerationCommand command) {
        if (command == null) {
            throw new InvalidAiRouteGenerationInputException("생성 명령이 필요합니다.");
        }
        GenerationTimeBudget timeBudget = generationEngine.startTimeBudget();
        Optional<GenerationStartResult> existing =
                startService.findExisting(readerId, idempotencyKey, command);
        if (existing.isPresent()) {
            return existingResult(readerId, existing.get());
        }

        ProductionContext context = transactionTemplate.execute(
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
            timeBudget.requireRemaining();
            AiRouteEngineResult engineResult = generationEngine.generate(
                    context.generation(), context.entitlement(), timeBudget);
            complete(generationId, engineResult.generation());
            return createdResult(readerId, generationId);
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
        } catch (RuntimeException exception) {
            return failUnexpected(readerId, generationId, exception);
        }
    }

    private ProductionContext prepare(long readerId, AiRouteGenerationCommand command) {
        AiRouteGenerationSnapshot generation = generationEngine.prepareForProduction(command);
        Instant now = clock.instant();
        boolean owned = ownershipService.isOwned(readerId, command.bookId());
        int inkBalance = owned ? 0 : inkService.getBalance(readerId);
        Set<Long> activeRentalPageIds = new HashSet<>();
        if (!owned) {
            for (Long pageId : generation.candidatePageIds()) {
                if (rentalService.findActiveRental(readerId, pageId, now).isPresent()) {
                    activeRentalPageIds.add(pageId);
                }
            }
        }
        AiRouteEntitlementSnapshot entitlement = entitlementSnapshotFactory.create(
                command, owned, inkBalance, activeRentalPageIds);
        return new ProductionContext(generation, entitlement);
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
                                    item.role(),
                                    item.additionalCostStatus()))
                            .toList());
            return;
        }

        lifecycleService.completeWithoutRoute(
                generationId,
                AiRouteNoRouteReason.valueOf(result.noRouteReason().name()),
                result.minimumRequiredInk());
    }

    private GenerationExecutionResult existingResult(long readerId, GenerationStartResult start) {
        return switch (start.kind()) {
            case EXISTING_GENERATING, EXISTING_FINAL -> GenerationExecutionResult.replayed(
                    findGeneration(readerId, start.generationId()));
            case KEY_REUSED -> GenerationExecutionResult.keyReused();
            case DAILY_LIMIT -> GenerationExecutionResult.dailyLimit(start.retryAfterAt());
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

    private record ProductionContext(
            AiRouteGenerationSnapshot generation, AiRouteEntitlementSnapshot entitlement) {}
}
