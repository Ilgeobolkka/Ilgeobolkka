package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationApiResult;
import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationItemResponse;
import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationRequest;
import com.example.ilgeobolkka.airoute.dto.AiRouteGenerationResponse;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationApiException;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationConsumedException;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationNotFoundException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.service.AiRoutePurposeNormalizer;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationFailureCode;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationItemView;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationRequestView;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationStartService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationView;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteRequestFingerprint;
import com.example.ilgeobolkka.airoute.service.generation.GenerationExecutionResult;
import com.example.ilgeobolkka.airoute.service.query.AiRouteItemGuideAssembler;
import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.global.exception.ErrorCode;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** HTTP 생성 계약을 기존 도메인 생성 Facade와 조회 서비스에 연결하는 API 전용 어댑터다. */
@Service
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteGenerationApiFacade {

    private final AiRouteGenerationFacade generationFacade;
    private final AiRouteGenerationStartService startService;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final BookService bookService;
    private final InkService inkService;
    private final OwnershipService ownershipService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AiRouteGenerationApiFacade(
            AiRouteGenerationFacade generationFacade,
            AiRouteGenerationStartService startService,
            AiRouteGenerationLifecycleService lifecycleService,
            BookService bookService,
            InkService inkService,
            OwnershipService ownershipService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.generationFacade = generationFacade;
        this.startService = startService;
        this.lifecycleService = lifecycleService;
        this.bookService = bookService;
        this.inkService = inkService;
        this.ownershipService = ownershipService;
        this.clock = clock;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
    }

    /** HTTP 입력을 서버가 신뢰하는 도서·권한 정보와 결합한 뒤 G07 생성 흐름을 호출한다. */
    public AiRouteGenerationApiResult generate(
            long readerId,
            UUID idempotencyKey,
            long bookId,
            AiRouteGenerationRequest request) {
        if (request == null) {
            throw new InvalidAiRouteGenerationInputException("생성 요청이 필요합니다.");
        }
        String normalizedPurpose = validateHttpRequest(request);
        Optional<AiRouteGenerationRequestView> existing =
                startService.findExistingRequest(readerId, idempotencyKey);
        if (existing.isPresent()) {
            requireSameHttpRequest(existing.get(), bookId, normalizedPurpose, request);
            return find(readerId, existing.get().generationId());
        }
        AiRouteGenerationCommand command = transactionTemplate.execute(
                status -> commandOf(readerId, bookId, request));
        if (command == null) {
            throw new IllegalStateException("AI 경로 생성 명령을 만들지 못했습니다.");
        }
        return apiResultOf(readerId, generationFacade.generate(readerId, idempotencyKey, command));
    }

    /** 현재 소장·잔액과 무관하게 판정할 수 있는 HTTP 입력 형식은 멱등 조회보다 먼저 검증한다. */
    private String validateHttpRequest(AiRouteGenerationRequest request) {
        if (request.maxAdditionalInk() != null && request.depth() != null) {
            throw new InvalidAiRouteGenerationInputException("예산과 깊이를 함께 보낼 수 없습니다.");
        }
        return AiRoutePurposeNormalizer.normalize(request.purpose());
    }

    private void requireSameHttpRequest(
            AiRouteGenerationRequestView existing,
            long bookId,
            String normalizedPurpose,
            AiRouteGenerationRequest request) {
        boolean same = existing.bookId() == bookId
                && (existing.status() == AiRouteGenerationStatus.CONSUMED
                        ? sameConsumedRequest(existing, normalizedPurpose, request)
                        : switch (existing.requestType()) {
                            case INK_BUDGET ->
                                sameInkBudgetRequest(existing, normalizedPurpose, request);
                            case OWNED_DEPTH ->
                                sameOwnedDepthRequest(existing, normalizedPurpose, request);
                        });
        if (!same) {
            throw new AiRouteGenerationApiException(ErrorCode.AI_ROUTE_IDEMPOTENCY_KEY_REUSED);
        }
    }

    /** 삭제된 저장 경로 대신 generation에 남은 지문으로 CONSUMED 요청의 동일성을 판정한다. */
    private boolean sameConsumedRequest(
            AiRouteGenerationRequestView existing,
            String normalizedPurpose,
            AiRouteGenerationRequest request) {
        if (request.depth() != null) {
            return existing.requestFingerprint().equals(AiRouteRequestFingerprint.ofCanonicalRequest(
                    existing.bookId(),
                    existing.contentVersion(),
                    normalizedPurpose,
                    AiRouteRequestType.OWNED_DEPTH,
                    null,
                    request.depth(),
                    false));
        }
        boolean defaultBudget = request.maxAdditionalInk() == null;
        return existing.requestFingerprint().equals(AiRouteRequestFingerprint.ofCanonicalRequest(
                existing.bookId(),
                existing.contentVersion(),
                normalizedPurpose,
                AiRouteRequestType.INK_BUDGET,
                request.maxAdditionalInk(),
                null,
                defaultBudget));
    }

    private boolean sameInkBudgetRequest(
            AiRouteGenerationRequestView existing,
            String normalizedPurpose,
            AiRouteGenerationRequest request) {
        if (request.depth() != null) {
            return false;
        }
        boolean defaultBudget = request.maxAdditionalInk() == null;
        Integer budget = defaultBudget ? existing.maxAdditionalInk() : request.maxAdditionalInk();
        String fingerprint = AiRouteRequestFingerprint.ofCanonicalRequest(
                existing.bookId(),
                existing.contentVersion(),
                normalizedPurpose,
                existing.requestType(),
                budget,
                null,
                defaultBudget);
        return existing.requestFingerprint().equals(fingerprint);
    }

    private boolean sameOwnedDepthRequest(
            AiRouteGenerationRequestView existing,
            String normalizedPurpose,
            AiRouteGenerationRequest request) {
        if (request.maxAdditionalInk() != null) {
            return false;
        }
        String fingerprint = AiRouteRequestFingerprint.ofCanonicalRequest(
                existing.bookId(),
                existing.contentVersion(),
                normalizedPurpose,
                existing.requestType(),
                null,
                request.depth(),
                false);
        return existing.requestFingerprint().equals(fingerprint);
    }

    /** 소유자와 만료 조건을 한 조회에 적용하고 GET의 200·202 응답을 만든다. */
    public AiRouteGenerationApiResult find(long readerId, UUID generationId) {
        AiRouteGenerationView generation = lifecycleService
                .findOwnedResult(generationId, readerId)
                .orElseThrow(() -> new AiRouteGenerationNotFoundException(generationId));
        return responseResultOf(readerId, generation, false);
    }

    private AiRouteGenerationCommand commandOf(
            long readerId, long bookId, AiRouteGenerationRequest request) {
        Book book = bookService.findBook(bookId);
        boolean owned = ownershipService.isOwned(readerId, bookId);
        if (owned) {
            if (request.maxAdditionalInk() != null) {
                throw new InvalidAiRouteGenerationInputException("소장 도서에는 예산을 보낼 수 없습니다.");
            }
            return AiRouteGenerationCommand.forOwnedDepth(
                    bookId, book.getContentVersion(), request.purpose(), request.depth());
        }

        if (request.depth() != null) {
            throw new InvalidAiRouteGenerationInputException("비소장 도서에는 깊이를 보낼 수 없습니다.");
        }
        int inkBalance = inkService.getBalance(readerId);
        if (request.maxAdditionalInk() == null) {
            return AiRouteGenerationCommand.forDefaultInkBudget(
                    bookId, book.getContentVersion(), request.purpose(), inkBalance);
        }
        return AiRouteGenerationCommand.forInkBudget(
                bookId,
                book.getContentVersion(),
                request.purpose(),
                request.maxAdditionalInk(),
                inkBalance);
    }

    private AiRouteGenerationApiResult apiResultOf(
            long readerId, GenerationExecutionResult result) {
        switch (result.state()) {
            case KEY_REUSED -> throw new AiRouteGenerationApiException(
                    ErrorCode.AI_ROUTE_IDEMPOTENCY_KEY_REUSED);
            case DAILY_LIMIT -> throw new AiRouteGenerationApiException(
                    ErrorCode.AI_ROUTE_DAILY_LIMIT_EXCEEDED,
                    retryAfterSeconds(clock.instant(), result.retryAfterAt()));
            case TIMEOUT -> throw new AiRouteGenerationApiException(
                    ErrorCode.AI_ROUTE_GENERATION_TIMEOUT);
            case GENERATING, FINAL -> {
                // 아래에서 저장된 공개 실패와 정상 응답을 공통 처리한다.
            }
        }
        if (result.failure() != null) {
            throw new AiRouteGenerationApiException(failureErrorCode(result.failure()));
        }
        return responseResultOf(
                readerId,
                result.generation(),
                result.execution() == GenerationExecutionResult.Execution.NEW);
    }

    private AiRouteGenerationApiResult responseResultOf(
            long readerId, AiRouteGenerationView generation, boolean created) {
        if (generation.status() == AiRouteGenerationStatus.FAILED) {
            throw new AiRouteGenerationApiException(
                    failureErrorCode(generation.failureCode()));
        }
        if (generation.status() == AiRouteGenerationStatus.CONSUMED) {
            throw new AiRouteGenerationConsumedException(generation.generationId());
        }
        AiRouteGenerationResponse response = transactionTemplate.execute(
                status -> responseOf(readerId, generation));
        if (response == null) {
            throw new IllegalStateException("AI 경로 생성 응답을 만들지 못했습니다.");
        }
        return new AiRouteGenerationApiResult(
                response,
                created,
                generation.status() == AiRouteGenerationStatus.GENERATING);
    }

    /** 도메인 실패가 추가되면 누락된 API 매핑을 컴파일 단계에서 발견하도록 명시적으로 변환한다. */
    static ErrorCode failureErrorCode(AiRouteGenerationFailureCode failureCode) {
        return switch (failureCode) {
            case AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE ->
                ErrorCode.AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE;
            case AI_ROUTE_PROVIDER_UNAVAILABLE -> ErrorCode.AI_ROUTE_PROVIDER_UNAVAILABLE;
            case AI_ROUTE_INVALID_OUTPUT -> ErrorCode.AI_ROUTE_INVALID_OUTPUT;
            case AI_ROUTE_GENERATION_TIMEOUT -> ErrorCode.AI_ROUTE_GENERATION_TIMEOUT;
        };
    }

    /** DB에 알 수 없는 과거 실패 코드가 남아 있어도 Enum 변환 예외를 그대로 노출하지 않는다. */
    static ErrorCode failureErrorCode(String failureCode) {
        if (failureCode == null) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }
        try {
            return failureErrorCode(AiRouteGenerationFailureCode.valueOf(failureCode));
        } catch (IllegalArgumentException exception) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }
    }

    private AiRouteGenerationResponse responseOf(
            long readerId, AiRouteGenerationView generation) {
        List<AiRouteGenerationItemResponse> items = generation.status() == AiRouteGenerationStatus.ROUTE
                ? itemResponses(generation)
                : List.of();
        return new AiRouteGenerationResponse(
                generation.generationId(),
                generation.status(),
                generation.bookId(),
                generation.contentVersion(),
                generation.normalizedPurpose(),
                generation.expiresAt(),
                generation.savedRouteId(),
                startService.remainingDailyGenerations(readerId),
                generation.noRouteReason(),
                generation.minimumRequiredInk(),
                items);
    }

    private List<AiRouteGenerationItemResponse> itemResponses(AiRouteGenerationView generation) {
        return generation.items().stream()
                .map(this::itemResponse)
                .toList();
    }

    private AiRouteGenerationItemResponse itemResponse(AiRouteGenerationItemView item) {
        return new AiRouteGenerationItemResponse(
                item.position(),
                item.pageNumber(),
                item.relevance(),
                item.prerequisite(),
                item.role(),
                AiRouteItemGuideAssembler.estimatedMinutes(item.estimatedReadingSeconds()),
                AiRouteItemGuideAssembler.guide(item.role(), item.publicGuideTopic()),
                item.additionalCostStatus());
    }

    static long retryAfterSeconds(Instant now, Instant resetAt) {
        if (now == null || resetAt == null) {
            throw new IllegalArgumentException("현재 시각과 생성 횟수 초기화 시각은 필수입니다.");
        }
        if (!now.isBefore(resetAt)) {
            return 0;
        }
        Duration remaining = Duration.between(now, resetAt);
        return remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
    }
}
