package com.example.ilgeobolkka.airoute.facade;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotSupportedException;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteEngineResult;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationEngine;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationFailureCode;
import com.example.ilgeobolkka.airoute.service.generation.GenerationExecutionResult;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingException;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteException;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteGatewayResult;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** G07의 짧은 DB 단계와 트랜잭션 밖 Gateway 호출을 실제 MySQL 상태 전이로 검증한다. */
@SpringBootTest(
        properties = {
            "ai-route.enabled=true",
            "openai.project-id=test-project",
            "openai.api-key=test-key",
            "openai.data-policy-version=policy-v1"
        })
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteGenerationFacadeMySqlIntegrationTest.TestBeans.class)
class AiRouteGenerationFacadeMySqlIntegrationTest {

    private static final long READER_ID = 467_001L;
    private static final long BOOK_ID = 467_101L;
    private static final long PAGE_ID_BASE = 467_200L;
    private static final long OWNERSHIP_PAYMENT_ID = 467_301L;
    private static final String CONTENT_VERSION = "ai-route-v1";
    private static final String PURPOSE = "핵심 개념의 흐름 이해";
    private static final Instant STARTED_AT = Instant.parse("2026-08-13T00:00:00Z");

    private final AiRouteGenerationFacade facade;
    private final AiRouteGenerationEngine engine;
    private final JdbcTemplate jdbcTemplate;
    private final FakeEmbeddingGateway embeddingGateway;
    private final FakeRouteGateway routeGateway;
    private final MutableClock clock;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    AiRouteGenerationFacadeMySqlIntegrationTest(
            AiRouteGenerationFacade facade,
            AiRouteGenerationEngine engine,
            JdbcTemplate jdbcTemplate,
            FakeEmbeddingGateway embeddingGateway,
            FakeRouteGateway routeGateway,
            MutableClock clock,
            PlatformTransactionManager transactionManager) {
        this.facade = facade;
        this.engine = engine;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingGateway = embeddingGateway;
        this.routeGateway = routeGateway;
        this.clock = clock;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        clock.set(STARTED_AT);
        embeddingGateway.reset(clock);
        routeGateway.reset(clock);
        독자와_잉크를_생성한다();
        지원_도서와_페이지를_생성한다(List.of(true, true));
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    @Test
    void NEW_정상_경로는_Gateway를_transaction_밖에서_한번씩_호출하고_ROUTE로_완료한다() {
        routeGateway.then(정상_응답(1, 2));
        Map<String, Object> before = 사용자_상태();

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(2));

        assertAll(
                () -> assertEquals(GenerationExecutionResult.Execution.NEW, result.execution()),
                () -> assertEquals(GenerationExecutionResult.State.FINAL, result.state()),
                () -> assertEquals(AiRouteGenerationStatus.ROUTE, result.generation().status()),
                () -> assertEquals(2, result.generation().items().size()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()),
                () -> assertEquals(before, 사용자_상태()));
    }

    @Test
    void 평가용_Engine은_사용자와_생성_영속화_없이_운영_경로와_버전을_반환한다() {
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
        jdbcTemplate.update("UPDATE book SET ai_route_supported = FALSE WHERE id = ?", BOOK_ID);
        routeGateway.then(정상_응답(1, 2));

        AiRouteEngineResult result = engine.generate(
                잉크_명령(2),
                AiRouteEntitlementSnapshot.forNonOwned(2, Set.of()));

        assertAll(
                () -> assertEquals(
                        com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.Status.ROUTE,
                        result.generation().status()),
                () -> assertEquals(2, result.generation().items().size()),
                () -> assertEquals("embedding-v1", result.embeddingModel()),
                () -> assertEquals("route-v1", result.routeModel()),
                () -> assertEquals("air-candidate-v1", result.candidatePolicyVersion()),
                () -> assertEquals("prompt-v1", result.promptVersion()),
                () -> assertEquals("schema-v1", result.schemaVersion()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()),
                () -> assertEquals(0, 개수("reader")),
                () -> assertEquals(0, 개수("ink_account")),
                () -> assertEquals(0, 개수("ai_route_generation")),
                () -> assertEquals(0, 개수("ai_route_generation_item")),
                () -> assertEquals(0, 개수("ai_route_daily_usage")));
    }

    @Test
    void 호출자가_transaction을_열면_생성과_Gateway_호출_전에_거부한다() {
        UUID key = UUID.randomUUID();

        assertThrows(
                IllegalTransactionStateException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> facade.generate(READER_ID, key, 잉크_명령(1))));

        assertAll(
                () -> assertEquals(0, 생성_수()),
                () -> assertEquals(0, 일일_사용량()),
                () -> assertEquals(0, embeddingGateway.calls()),
                () -> assertEquals(0, routeGateway.calls()));
    }

    @Test
    void 후보_선수에서_비후보_의존_페이지로_향하는_간선은_생성을_막지_않는다() {
        jdbcTemplate.update(
                """
                UPDATE book_page
                SET ai_analysis_text = NULL,
                    ai_public_guide_topic = NULL,
                    estimated_reading_seconds = NULL,
                    embedding_model = NULL,
                    embedding_dimensions = NULL,
                    embedding_json = NULL,
                    duplicate_group_keys = NULL,
                    ai_route_candidate = FALSE
                WHERE book_id = ? AND page_number = 2
                """,
                BOOK_ID);
        선수를_생성한다(1, 2);
        routeGateway.then(정상_응답(1));

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.ROUTE, result.generation().status()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 비후보_선수에서_후보_의존_페이지로_향하는_간선은_원인을_담아_거부한다() {
        jdbcTemplate.update(
                """
                UPDATE book_page
                SET ai_analysis_text = NULL,
                    ai_public_guide_topic = NULL,
                    estimated_reading_seconds = NULL,
                    embedding_model = NULL,
                    embedding_dimensions = NULL,
                    embedding_json = NULL,
                    duplicate_group_keys = NULL,
                    ai_route_candidate = FALSE
                WHERE book_id = ? AND page_number = 1
                """,
                BOOK_ID);
        선수를_생성한다(1, 2);

        지원_거부를_검증한다("선수 페이지가 AI 경로 후보가 아닙니다: prerequisitePageNumber=1");
    }

    @Test
    void 외부_호출_중_contentVersion이_바뀌어도_완료와_멱등_재요청은_최초_snapshot을_쓴다() {
        UUID key = UUID.randomUUID();
        routeGateway.then(정상_응답(1));
        routeGateway.beforeReturn(() -> jdbcTemplate.update(
                "UPDATE book SET content_version = 'ai-route-v2' WHERE id = ?", BOOK_ID));

        GenerationExecutionResult first = facade.generate(READER_ID, key, 잉크_명령(1));
        GenerationExecutionResult replay = facade.generate(READER_ID, key, 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.ROUTE, first.generation().status()),
                () -> assertEquals(CONTENT_VERSION, first.generation().contentVersion()),
                () -> assertEquals(1, first.generation().items().size()),
                () -> assertEquals(GenerationExecutionResult.Execution.REPLAY, replay.execution()),
                () -> assertEquals(first.generation().generationId(), replay.generation().generationId()),
                () -> assertEquals(CONTENT_VERSION, replay.generation().contentVersion()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()),
                () -> assertEquals(
                        "ai-route-v2",
                        jdbcTemplate.queryForObject(
                                "SELECT content_version FROM book WHERE id = ?",
                                String.class,
                                BOOK_ID)));
    }

    @Test
    void 후보가_없으면_Responses를_호출하지_않고_NO_RELEVANT_PAGES로_완료한다() {
        embeddingGateway.vector(0.0, 1.0);

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(2));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.NO_ROUTE, result.generation().status()),
                () -> assertEquals(
                        AiRouteNoRouteReason.NO_RELEVANT_PAGES,
                        result.generation().noRouteReason()),
                () -> assertNull(result.generation().minimumRequiredInk()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(0, routeGateway.calls()));
    }

    @Test
    void 예산_안에_선택할_페이지가_없으면_INSUFFICIENT_BUDGET으로_완료한다() {
        routeGateway.then(정상_응답(1, 2));

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(0));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.NO_ROUTE, result.generation().status()),
                () -> assertEquals(
                        AiRouteNoRouteReason.INSUFFICIENT_BUDGET,
                        result.generation().noRouteReason()),
                () -> assertEquals(1, result.generation().minimumRequiredInk()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void QUICK에_선수_폐쇄_여섯_페이지를_담지_못하면_INSUFFICIENT_DEPTH로_완료한다() {
        테스트_데이터를_정리한다();
        독자와_잉크를_생성한다();
        지원_도서와_페이지를_생성한다(
                List.of(false, false, false, false, false, true));
        for (int prerequisite = 1; prerequisite <= 5; prerequisite++) {
            선수를_생성한다(prerequisite, 6);
        }
        소장한다();
        routeGateway.then(정상_응답(1, 2, 3, 4, 5, 6));

        GenerationExecutionResult result = facade.generate(
                READER_ID,
                UUID.randomUUID(),
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, AiRouteDepth.QUICK));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.NO_ROUTE, result.generation().status()),
                () -> assertEquals(
                        AiRouteNoRouteReason.INSUFFICIENT_DEPTH,
                        result.generation().noRouteReason()),
                () -> assertNull(result.generation().minimumRequiredInk()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 같은_멱등_키의_재요청은_Gateway를_다시_호출하지_않고_최종_결과를_재생한다() {
        UUID key = UUID.randomUUID();
        routeGateway.then(정상_응답(1));
        GenerationExecutionResult first = facade.generate(READER_ID, key, 잉크_명령(1));

        GenerationExecutionResult replay = facade.generate(READER_ID, key, 잉크_명령(1));

        assertAll(
                () -> assertEquals(GenerationExecutionResult.Execution.REPLAY, replay.execution()),
                () -> assertEquals(GenerationExecutionResult.State.FINAL, replay.state()),
                () -> assertEquals(first.generation().generationId(), replay.generation().generationId()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 준비_중_기한을_넘긴_재요청도_기존_결과를_재생한다() {
        UUID key = UUID.randomUUID();
        routeGateway.then(정상_응답(1));
        GenerationExecutionResult first = facade.generate(READER_ID, key, 잉크_명령(1));
        clock.set(STARTED_AT);
        clock.advanceSecondsOnInstantCall(2, 20);

        GenerationExecutionResult replay = facade.generate(READER_ID, key, 잉크_명령(1));

        assertAll(
                () -> assertEquals(GenerationExecutionResult.Execution.REPLAY, replay.execution()),
                () -> assertEquals(first.generation().generationId(), replay.generation().generationId()),
                () -> assertEquals(1, 일일_생성_횟수()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 첫_semantic_invalid만_같은_입력으로_한번_재시도한다() {
        routeGateway.then(빈_응답());
        routeGateway.then(정상_응답(1));

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.ROUTE, result.generation().status()),
                () -> assertEquals(2, routeGateway.calls()),
                () -> assertEquals(routeGateway.inputs().get(0), routeGateway.inputs().get(1)));
    }

    @Test
    void 첫_malformed_output만_같은_입력으로_한번_재시도한다() {
        routeGateway.then(
                new OpenAiRouteException(OpenAiRouteException.Failure.MALFORMED_RESPONSE));
        routeGateway.then(정상_응답(1));

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.ROUTE, result.generation().status()),
                () -> assertEquals(2, routeGateway.calls()),
                () -> assertEquals(routeGateway.inputs().get(0), routeGateway.inputs().get(1)));
    }

    @Test
    void semantic_invalid_재시도에서_prompt나_schema_version이_바뀌면_FAILED로_확정한다() {
        routeGateway.then(빈_응답());
        RouteGatewayResult changedVersion = 정상_응답(1);
        routeGateway.then(new RouteGatewayResult(
                changedVersion.proposal(), "prompt-v2", changedVersion.schemaVersion()));

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.FAILED, result.generation().status()),
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT,
                        result.failure()),
                () -> assertEquals(2, routeGateway.calls()));
    }

    @Test
    void 두번의_invalid_output은_FAILED로_확정한다() {
        routeGateway.then(빈_응답());
        routeGateway.then(빈_응답());

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.FAILED, result.generation().status()),
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_INVALID_OUTPUT,
                        result.failure()),
                () -> assertEquals(2, routeGateway.calls()));
    }

    @ParameterizedTest
    @EnumSource(
            value = OpenAiRouteException.Failure.class,
            names = {"TEMPORARY", "TIMEOUT_OR_INCOMPLETE", "REFUSAL"})
    void 재시도하지_않는_공급자_오류는_한번만_호출하고_FAILED로_확정한다(
            OpenAiRouteException.Failure failure) {
        routeGateway.then(new OpenAiRouteException(failure));

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE,
                        result.failure()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 공급자_지출_한도는_전용_공개_코드로_확정한다() {
        embeddingGateway.failure(OpenAiEmbeddingException.Failure.BUDGET_LIMIT);

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE,
                        result.failure()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(0, routeGateway.calls()));
    }

    @Test
    void 영벡터_Embedding_응답은_공급자_실패로_확정하고_같은_키에_재생한다() {
        UUID key = UUID.randomUUID();
        embeddingGateway.vector(0.0, 0.0);

        GenerationExecutionResult first = facade.generate(READER_ID, key, 잉크_명령(1));
        GenerationExecutionResult replay = facade.generate(READER_ID, key, 잉크_명령(1));

        assertAll(
                () -> assertEquals(GenerationExecutionResult.Execution.NEW, first.execution()),
                () -> assertEquals(AiRouteGenerationStatus.FAILED, first.generation().status()),
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE,
                        first.failure()),
                () -> assertEquals(GenerationExecutionResult.Execution.REPLAY, replay.execution()),
                () -> assertEquals(first.failure(), replay.failure()),
                () -> assertEquals(first.generation().generationId(), replay.generation().generationId()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(0, routeGateway.calls()));
    }

    @Test
    void 전체_20초를_넘긴_응답은_GENERATION_TIMEOUT으로_확정한다() {
        routeGateway.then(정상_응답(1));
        routeGateway.advanceSecondsOnCall(20);

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.FAILED, result.generation().status()),
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT,
                        result.failure()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 생성_준비_중_20초를_넘으면_호출과_생성_횟수_없이_중단한다() {
        clock.advanceSecondsOnInstantCall(2, 20);

        GenerationExecutionResult result = facade.generate(
                READER_ID, UUID.randomUUID(), 잉크_명령(1));

        assertAll(
                () -> assertEquals(GenerationExecutionResult.Execution.REJECTED, result.execution()),
                () -> assertEquals(GenerationExecutionResult.State.TIMEOUT, result.state()),
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_GENERATION_TIMEOUT,
                        result.failure()),
                () -> assertNull(result.generation()),
                () -> assertEquals(0, 생성_수()),
                () -> assertEquals(0, 일일_사용량()),
                () -> assertEquals(0, embeddingGateway.calls()),
                () -> assertEquals(0, routeGateway.calls()));
    }

    @Test
    void 예상하지_못한_오류도_최초와_재요청에_같은_공개_오류를_반환한다() {
        UUID key = UUID.randomUUID();
        routeGateway.then(new IllegalStateException("테스트용 예상 밖 오류"));

        GenerationExecutionResult first = facade.generate(READER_ID, key, 잉크_명령(1));
        GenerationExecutionResult replay = facade.generate(READER_ID, key, 잉크_명령(1));

        assertAll(
                () -> assertEquals(GenerationExecutionResult.Execution.NEW, first.execution()),
                () -> assertEquals(AiRouteGenerationStatus.FAILED, first.generation().status()),
                () -> assertEquals(
                        AiRouteGenerationFailureCode.AI_ROUTE_PROVIDER_UNAVAILABLE,
                        first.failure()),
                () -> assertEquals(GenerationExecutionResult.Execution.REPLAY, replay.execution()),
                () -> assertEquals(first.failure(), replay.failure()),
                () -> assertEquals(first.generation().generationId(), replay.generation().generationId()),
                () -> assertEquals(1, embeddingGateway.calls()),
                () -> assertEquals(1, routeGateway.calls()));
    }

    @Test
    void 미지원_도서는_생성_행과_Gateway_호출_없이_거부한다() {
        jdbcTemplate.update("UPDATE book SET ai_route_supported = FALSE WHERE id = ?", BOOK_ID);

        지원_거부를_검증한다("도서의 AI 경로 지원이 비활성화되어 있습니다");
    }

    @Test
    void 외부_전송_권리가_없는_도서는_생성_행과_Gateway_호출_없이_거부한다() {
        jdbcTemplate.update(
                """
                UPDATE book
                SET ai_route_supported = FALSE,
                    ai_external_transfer_allowed = FALSE
                WHERE id = ?
                """,
                BOOK_ID);

        지원_거부를_검증한다("도서의 외부 전송 권리가 확인되지 않았습니다");
    }

    @Test
    void DB와_환경의_데이터_정책이_다르면_생성_행과_Gateway_호출_없이_거부한다() {
        jdbcTemplate.update(
                "UPDATE book SET ai_data_policy_version = 'policy-v2' WHERE id = ?", BOOK_ID);

        지원_거부를_검증한다("도서와 서버의 데이터 정책 버전이 다릅니다");
    }

    @Test
    void 후보_페이지의_분석_텍스트가_비어_있으면_원인을_담아_거부한다() {
        jdbcTemplate.update(
                "UPDATE book_page SET ai_analysis_text = ' ' WHERE book_id = ? AND page_number = 1",
                BOOK_ID);

        지원_거부를_검증한다("후보 페이지의 분석 텍스트가 비어 있습니다. pageNumber=1");
    }

    private AiRouteGenerationCommand 잉크_명령(int budget) {
        return AiRouteGenerationCommand.forInkBudget(
                BOOK_ID, CONTENT_VERSION, PURPOSE, budget, 5);
    }

    private void 지원_거부를_검증한다(String expectedReason) {
        AiRouteNotSupportedException exception = assertThrows(
                AiRouteNotSupportedException.class,
                () -> facade.generate(READER_ID, UUID.randomUUID(), 잉크_명령(1)));
        assertAll(
                () -> assertTrue(exception.getMessage().contains(expectedReason)),
                () -> assertEquals(0, 생성_수()),
                () -> assertEquals(0, embeddingGateway.calls()),
                () -> assertEquals(0, routeGateway.calls()));
    }

    private OpenAiRouteGateway.RouteGatewayResult 정상_응답(int... pageNumbers) {
        List<OpenAiRouteGateway.ModelRouteItem> items = new ArrayList<>();
        for (int index = 0; index < pageNumbers.length; index++) {
            items.add(new OpenAiRouteGateway.ModelRouteItem(
                    pageNumbers[index],
                    index == 0
                            ? OpenAiRouteGateway.Relevance.HIGH
                            : OpenAiRouteGateway.Relevance.MEDIUM,
                    index < pageNumbers.length - 1,
                    index < pageNumbers.length - 1
                            ? OpenAiRouteGateway.Role.PREREQUISITE
                            : OpenAiRouteGateway.Role.CORE));
        }
        return new OpenAiRouteGateway.RouteGatewayResult(
                new OpenAiRouteGateway.ModelRouteProposal(items),
                "prompt-v1",
                "schema-v1");
    }

    private OpenAiRouteGateway.RouteGatewayResult 빈_응답() {
        return new OpenAiRouteGateway.RouteGatewayResult(
                new OpenAiRouteGateway.ModelRouteProposal(List.of()),
                "prompt-v1",
                "schema-v1");
    }

    private void 독자와_잉크를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'scrum-467@example.com', 'hash', '2026-08-13 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 5)", READER_ID);
    }

    /** true면 목적과 같은 [1,0], false면 임계값 아래인 [0,1] 벡터를 저장한다. */
    private void 지원_도서와_페이지를_생성한다(List<Boolean> relevantPages) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won,
                     content_version, ai_route_supported,
                     ai_external_transfer_allowed, ai_data_policy_version)
                VALUES (?, '인문', 'SCRUM-467 테스트 도서', '테스트 저자', ?, 10000,
                        ?, TRUE, TRUE, 'policy-v1')
                """,
                BOOK_ID,
                relevantPages.size(),
                CONTENT_VERSION);
        for (int index = 0; index < relevantPages.size(); index++) {
            boolean relevant = relevantPages.get(index);
            jdbcTemplate.update(
                    """
                    INSERT INTO book_page
                        (id, book_id, page_number, content_type, text_content,
                         ai_analysis_text, ai_public_guide_topic, estimated_reading_seconds,
                         embedding_model, embedding_dimensions, embedding_json,
                         duplicate_group_keys, ai_route_candidate)
                    VALUES (?, ?, ?, 'TEXT', '테스트 본문', ?, ?, 60,
                            'embedding-v1', 2, ?, JSON_ARRAY(), TRUE)
                    """,
                    PAGE_ID_BASE + index + 1,
                    BOOK_ID,
                    index + 1,
                    "분석 텍스트 " + (index + 1),
                    "공개 주제 " + (index + 1),
                    relevant ? "[1.0, 0.0]" : "[0.0, 1.0]");
        }
    }

    private void 선수를_생성한다(int prerequisitePageNumber, int dependentPageNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_prerequisite
                    (book_id, prerequisite_page_number, dependent_page_number)
                VALUES (?, ?, ?)
                """,
                BOOK_ID,
                prerequisitePageNumber,
                dependentPageNumber);
    }

    private void 소장한다() {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status,
                     amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 10000,
                        '2026-08-12 00:00:00.000000', '2026-08-12 00:00:01.000000')
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                BOOK_ID,
                UUID.randomUUID().toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, '2026-08-12 00:00:01.000000')
                """,
                READER_ID,
                BOOK_ID,
                OWNERSHIP_PAYMENT_ID);
    }

    private Map<String, Object> 사용자_상태() {
        return Map.of(
                "balance",
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        READER_ID),
                "ledger",
                개수("ink_ledger"),
                "rental",
                개수("page_rental"),
                "ownership",
                개수("book_ownership"),
                "session",
                개수("reading_session"),
                "library",
                개수("library_entry"));
    }

    private int 개수(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int 생성_수() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 일일_사용량() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_daily_usage WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 일일_생성_횟수() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(generation_count), 0)
                FROM ai_route_daily_usage
                WHERE reader_id = ?
                """,
                Integer.class,
                READER_ID);
    }

    private void 테스트_데이터를_정리한다() {
        jdbcTemplate.update("DELETE FROM ai_route_generation_item WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ai_route_prerequisite WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM page_rental WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM library_entry WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM reading_session WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_ownership WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM ink_account WHERE reader_id = ?", READER_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM reader WHERE id = ?", READER_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(STARTED_AT);
        }

        @Bean
        @Primary
        FakeEmbeddingGateway fakeEmbeddingGateway() {
            return new FakeEmbeddingGateway();
        }

        @Bean
        @Primary
        FakeRouteGateway fakeRouteGateway() {
            return new FakeRouteGateway();
        }
    }

    static final class FakeEmbeddingGateway implements OpenAiEmbeddingGateway {

        private final AtomicInteger callCount = new AtomicInteger();
        private volatile double[] vector = {1.0, 0.0};
        private volatile OpenAiEmbeddingException.Failure failure;
        private volatile MutableClock clock;

        void reset(MutableClock clock) {
            callCount.set(0);
            vector = new double[] {1.0, 0.0};
            failure = null;
            this.clock = clock;
        }

        void vector(double first, double second) {
            vector = new double[] {first, second};
        }

        void failure(OpenAiEmbeddingException.Failure failure) {
            this.failure = failure;
        }

        int calls() {
            return callCount.get();
        }

        @Override
        public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
            callCount.incrementAndGet();
            if (failure != null) {
                throw new OpenAiEmbeddingException(failure);
            }
            return new Embedding(List.of(vector[0], vector[1]), model, dimensions);
        }

        @Override
        public Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeRouteGateway implements OpenAiRouteGateway {

        private final Deque<Object> results = new ArrayDeque<>();
        private final List<RouteInput> inputs = new CopyOnWriteArrayList<>();
        private volatile long advanceSeconds;
        private volatile MutableClock clock;
        private Runnable beforeReturn = () -> {};

        synchronized void reset(MutableClock clock) {
            results.clear();
            inputs.clear();
            advanceSeconds = 0;
            this.clock = clock;
            beforeReturn = () -> {};
        }

        synchronized void then(Object result) {
            results.addLast(result);
        }

        void advanceSecondsOnCall(long seconds) {
            advanceSeconds = seconds;
        }

        synchronized void beforeReturn(Runnable action) {
            beforeReturn = action;
        }

        int calls() {
            return inputs.size();
        }

        List<RouteInput> inputs() {
            return List.copyOf(inputs);
        }

        @Override
        public RouteContract routeContract() {
            return new RouteContract("route-v1", "prompt-v1", "schema-v1");
        }

        @Override
        public synchronized RouteGatewayResult proposeRoute(RouteInput input) {
            inputs.add(input);
            if (advanceSeconds > 0) {
                clock.advanceSeconds(advanceSeconds);
            }
            Runnable action = beforeReturn;
            beforeReturn = () -> {};
            action.run();
            Object result = results.removeFirst();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return (RouteGatewayResult) result;
        }
    }

    static final class MutableClock extends Clock {

        private volatile Instant instant;
        private int instantCalls;
        private int advanceOnInstantCall = -1;
        private long secondsToAdvance;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        synchronized void set(Instant instant) {
            this.instant = instant;
            instantCalls = 0;
            advanceOnInstantCall = -1;
            secondsToAdvance = 0;
        }

        synchronized void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        synchronized void advanceSecondsOnInstantCall(int call, long seconds) {
            advanceOnInstantCall = call;
            secondsToAdvance = seconds;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public synchronized Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public synchronized Instant instant() {
            instantCalls++;
            if (instantCalls == advanceOnInstantCall) {
                instant = instant.plusSeconds(secondsToAdvance);
                advanceOnInstantCall = -1;
            }
            return instant;
        }
    }
}
