package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.ilgeobolkka.airoute.service.generation.GenerationStartResult.Kind;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 한도 경계는 실제 동시 요청으로만 증명된다. 테스트 클래스에 {@code @Transactional}을 붙이지 않는 이유도
 * 같다. 붙이면 모든 작업이 한 트랜잭션에 갇혀 commit·rollback 결과를 볼 수 없다.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=12")
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AiRouteGenerationStartMySqlIntegrationTest.MutableClockConfiguration.class)
class AiRouteGenerationStartMySqlIntegrationTest {

    private static final int LIMIT = AiRouteGenerationStartService.DAILY_GENERATION_LIMIT;

    /** 여러 독자가 빈 인덱스에 동시에 insert 하는 상황을 만들려고 넷을 쓴다. */
    private static final List<Long> READER_IDS = List.of(458_001L, 458_002L, 458_003L, 458_004L);

    private static final long READER_ID = READER_IDS.get(0);
    private static final long OTHER_READER_ID = READER_IDS.get(1);
    private static final long BOOK_ID = 458_101L;
    private static final long MISSING_BOOK_ID = 458_999L;
    private static final long FIRST_PAGE_ID = 458_201L;
    private static final long SECOND_PAGE_ID = 458_202L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String PURPOSE = "핵심 개념만 빠르게";

    /**
     * 이 두 시각은 UTC로는 날짜가 갈리지만 테스트 JVM 기본 시간대인 KST로는 둘 다 2026-08-07이다.
     * ({@code build.gradle}이 {@code user.timezone=Asia/Seoul}로 띄운다.) 계수 기준을 시스템 시간대로
     * 바꾸면 UTC 자정 테스트만 깨진다.
     */
    private static final Instant STARTED_AT = Instant.parse("2026-08-06T23:59:59.123456Z");

    private static final Instant NEXT_UTC_DAY = Instant.parse("2026-08-07T00:00:00.123456Z");
    private static final LocalDate USAGE_DATE = LocalDate.of(2026, 8, 6);
    private static final LocalDate NEXT_USAGE_DATE = LocalDate.of(2026, 8, 7);

    private static final Instant COMPLETED_AT = Instant.parse("2026-08-07T00:00:09.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-07T00:15:09.123456Z");

    private final AiRouteGenerationStartService startService;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final AiRouteGenerationRepository generationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MutableClock clock;

    @Autowired
    AiRouteGenerationStartMySqlIntegrationTest(
            AiRouteGenerationStartService startService,
            AiRouteGenerationLifecycleService lifecycleService,
            AiRouteGenerationRepository generationRepository,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MutableClock clock) {
        this.startService = startService;
        this.lifecycleService = lifecycleService;
        this.generationRepository = generationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        테스트_데이터를_정리한다();
        READER_IDS.forEach(this::독자를_생성한다);
        도서를_생성한다();
        clock.set(STARTED_AT);
    }

    @AfterEach
    void tearDown() {
        테스트_데이터를_정리한다();
    }

    // --- 멱등 -------------------------------------------------------------

    @Test
    void 같은_키_같은_입력을_두_번_시작하면_생성이_하나만_남는다() {
        UUID key = UUID.randomUUID();

        GenerationStartResult first = startService.start(READER_ID, key, 명령(PURPOSE));
        GenerationStartResult second = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, first.kind()),
                () -> assertEquals(Kind.EXISTING_GENERATING, second.kind()),
                () -> assertEquals(first.generationId(), second.generationId()),
                () -> assertEquals(AiRouteGenerationStatus.GENERATING, second.status()),
                () -> assertNull(second.expiresAt()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 같은_키_같은_입력의_동시_요청도_생성이_하나만_남는다() throws Exception {
        UUID key = UUID.randomUUID();

        List<GenerationStartResult> results = 동시에_시작한다(READER_ID, List.of(key, key));

        assertAll(
                () -> assertEquals(1, 개수(results, Kind.NEW)),
                () -> assertEquals(1, 개수(results, Kind.EXISTING_GENERATING)),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 같은_키에_다른_입력이_오면_거절하고_횟수를_쓰지_않는다() {
        UUID key = UUID.randomUUID();
        startService.start(READER_ID, key, 명령(PURPOSE));

        GenerationStartResult reused = startService.start(READER_ID, key, 명령("다른 목적으로 읽는다"));

        assertAll(
                () -> assertEquals(Kind.KEY_REUSED, reused.kind()),
                () -> assertNull(reused.generationId()),
                () -> assertNull(reused.status()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 실패로_끝난_생성을_다시_시작해도_횟수를_더_쓰지_않는다() {
        UUID key = UUID.randomUUID();
        GenerationStartResult started = startService.start(READER_ID, key, 명령(PURPOSE));
        생성을_실패로_끝낸다(started.generationId());

        GenerationStartResult retried = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.EXISTING_FINAL, retried.kind()),
                () -> assertEquals(AiRouteGenerationStatus.FAILED, retried.status()),
                () -> assertEquals(EXPIRES_AT, retried.expiresAt()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    /**
     * 요청이 기존 생성 조회를 마친 뒤에 다른 요청이 같은 {@code (readerId, idempotencyKey)} 를 확정하는
     * 순서를 만든다. 이 요청은 insert 까지 밀고 갔다가 unique key 에 부딪히고, 조건 6이 요구하는 것은
     * 그때 500 이 아니라 기존 행으로 수렴하는 것이다.
     *
     * <p>한도에는 여유가 있으므로 요청은 insert 까지 밀고 간다. 아래 한도 테스트와 다른 것은 사용량
     * 시드값뿐이고, 그 하나가 unique key 경합 경로와 한도 경로를 가른다.
     */
    @Test
    void 조회_뒤에_확정된_같은_키가_있으면_그_행으로_수렴한다() throws Exception {
        UUID key = UUID.randomUUID();
        UUID 먼저_들어온_생성 = UUID.randomUUID();
        사용량을_심는다(READER_ID, USAGE_DATE, 0);

        GenerationStartResult converged =
                사용량_잠금_뒤에_시작한다(key, () -> 생성을_직접_넣는다(먼저_들어온_생성, key));

        assertAll(
                () -> assertEquals(Kind.EXISTING_GENERATING, converged.kind()),
                () -> assertEquals(먼저_들어온_생성, converged.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(0, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    /**
     * 사용량 잠금을 기다리는 사이 같은 멱등 키의 요청이 그날의 마지막 한 건을 가져간 상황이다. 잠금을
     * 얻었을 때 한도는 이미 찼지만, 이 요청은 앞선 요청과 같은 요청이므로 거절이 아니라 그 생성 상태를
     * 받아야 한다. 이미 시작된 생성을 두고 429로 거절하면 15분 안의 같은 요청은 저장된 상태를 돌려준다는
     * 계약이 깨진다.
     *
     * <p>한도에 닿아 있으므로 요청은 insert 까지 가지 못하고 돌아간다. unique key 경합이 잡아 줄 수 없는
     * 유일한 경로라 거절 직전에 스스로 확인해야 한다.
     */
    @Test
    void 한도에_닿았어도_같은_키의_생성이_이미_있으면_그_상태를_돌려준다() throws Exception {
        UUID key = UUID.randomUUID();
        UUID 먼저_들어온_생성 = UUID.randomUUID();
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT);

        GenerationStartResult result =
                사용량_잠금_뒤에_시작한다(key, () -> 생성을_직접_넣는다(먼저_들어온_생성, key));

        assertAll(
                () -> assertEquals(Kind.EXISTING_GENERATING, result.kind()),
                () -> assertEquals(먼저_들어온_생성, result.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    /**
     * 앞 테스트와 같은 인터리빙인데, 사용량 잠금을 기다리는 시간이 보관 기간을 넘긴 경우다. 잠금을
     * 얻었을 때 같은 키의 생성은 이미 시작·완료·만료까지 끝나 있다. 만료한 멱등 상태는 없는 것이므로
     * 이 요청은 새 요청이고, 쓸 횟수가 없으니 한도 초과로 거절해야 한다. 걸러 내지 않으면 15분이 지나
     * 사라졌어야 할 결과를 {@code EXISTING_FINAL} 로 돌려준다.
     *
     * <p>잠금 조회는 read view 가 아니라 최신 commit 을 읽으므로, 이 만료 행은 요청이 앞머리에서 존재
     * 확인을 마친 <b>뒤에</b> 생겼는데도 여기서 보인다. 그 확인이 참이었을 때 만료 행을 지우는 경로로는
     * 막을 수 없는 자리다.
     *
     * <p>두 날짜의 사용량을 모두 한도로 채운다. 대기가 15분이라 자정을 넘고, 넘긴 요청은 새 날짜의
     * 사용량 행으로 옮겨 잠그기 때문이다. 새 날짜에 여유가 있으면 한도 경로가 아니라 insert 로 간다.
     */
    @Test
    void 한도에_닿아_기다리는_사이_같은_키가_만료하면_기존_결과를_돌려주지_않는다() throws Exception {
        UUID key = UUID.randomUUID();
        UUID 먼저_들어온_생성 = UUID.randomUUID();
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT);
        사용량을_심는다(READER_ID, NEXT_USAGE_DATE, LIMIT);

        GenerationStartResult result =
                사용량_잠금_뒤에_시작한다(
                        key,
                        () -> {
                            생성을_직접_넣는다(먼저_들어온_생성, key);
                            생성을_실패로_끝낸다(먼저_들어온_생성);
                            clock.set(EXPIRES_AT);
                        });

        assertAll(
                () -> assertEquals(Kind.DAILY_LIMIT, result.kind()),
                () -> assertNull(result.generationId()),
                () -> assertNull(result.status()),
                // 거절만 하고 돌아가는 경로라 만료 행은 정리 배치 몫으로 남는다.
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, NEXT_USAGE_DATE)));
    }

    /**
     * 전날 한도를 다 쓴 상태에서 자정 직전에 시작해, 사용량 잠금을 기다리는 사이 자정을 넘긴다. 진입
     * 시점 날짜로 계수하면 이미 초기화된 어제 한도로 거절해 {@code 매일 00:00 UTC 초기화} 계약을 깬다.
     *
     * <p>시계 읽기 래치가 뒤따르는 요청이 <b>전날 날짜로 확정한 것</b>을 보장한다. 확정을 확인한 뒤에만
     * 시계를 옮기므로, 처음부터 새 날짜를 읽어 우연히 통과하는 일이 없다.
     */
    @Test
    void 사용량_잠금을_기다리다_자정을_넘기면_새_날짜로_계수한다() throws Exception {
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT);

        GenerationStartResult result =
                사용량_잠금_뒤에_시작한다(UUID.randomUUID(), () -> clock.set(NEXT_UTC_DAY));

        assertAll(
                () -> assertEquals(Kind.NEW, result.kind()),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, NEXT_USAGE_DATE)),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)));
    }

    /**
     * 생성 인덱스가 비어 있을 때 여러 독자가 동시에 처음 insert 하는 상황이다. 존재를 확인하지 않고
     * 없는 행에 바로 잠금을 걸면 각자 gap lock을 쥔 채 서로의 insert를 기다려 교착한다. 그때는 결과가
     * 아니라 예외가 올라와 {@code future.get()} 에서 터진다.
     */
    @Test
    void 여러_독자가_빈_테이블에서_동시에_시작해도_각자_한_건씩_만든다() throws Exception {
        List<GenerationStartResult> results = 독자별로_동시에_시작한다(READER_IDS);

        assertAll(
                () -> assertEquals(READER_IDS.size(), 개수(results, Kind.NEW)),
                () ->
                        assertEquals(
                                Collections.nCopies(READER_IDS.size(), 1),
                                READER_IDS.stream().map(this::생성_수를_조회한다).toList()),
                () ->
                        assertEquals(
                                Collections.nCopies(READER_IDS.size(), 1),
                                READER_IDS.stream()
                                        .map(readerId -> 사용량을_조회한다(readerId, USAGE_DATE))
                                        .toList()));
    }

    /**
     * 보관 기간이 지난 멱등 상태는 정리 배치가 아직 안 돌았어도 없는 것으로 봐야 한다. 15분 뒤에는 같은
     * 키도 새 요청으로 취급한다는 것이 계약이고, 그 판정이 배치 주기에 달려 있으면 만료 정각부터 다음
     * 스윕까지 클라이언트가 지난 결과에 갇힌다.
     */
    @Test
    void 만료_정각에는_정리_전이라도_같은_키가_새_요청이_된다() {
        UUID key = UUID.randomUUID();
        GenerationStartResult first = startService.start(READER_ID, key, 명령(PURPOSE));
        생성을_실패로_끝낸다(first.generationId());
        clock.set(EXPIRES_AT);

        GenerationStartResult retried = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, retried.kind()),
                () -> assertNotEquals(first.generationId(), retried.generationId()),
                // 만료 행을 지우고 새로 넣었으므로 여전히 한 행이다.
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                // 앞선 요청은 8/6에, 새 요청은 8/7에. 되돌리지 않고 새로 한 건을 더 센다.
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, NEXT_USAGE_DATE)));
    }

    /**
     * 항목이 딸린 만료 행을 지우는 경로다. 앞의 재요청 테스트는 {@code FAILED} 라 항목이 0건이어서,
     * 항목을 먼저 지우고 생성을 지운 뒤 insert 하는 순서가 실제로 외래 키를 통과하는지 확인하지 못한다.
     */
    @Test
    void 항목이_있는_만료_경로도_같은_키로_다시_시작할_수_있다() {
        UUID key = UUID.randomUUID();
        GenerationStartResult first = startService.start(READER_ID, key, 명령(PURPOSE));
        clock.set(COMPLETED_AT);
        lifecycleService.completeWithRoute(first.generationId(), 두_항목());
        clock.set(EXPIRES_AT);

        GenerationStartResult retried = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, retried.kind()),
                () -> assertNotEquals(first.generationId(), retried.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(0, 항목_수를_조회한다(first.generationId())),
                () -> assertEquals(0, 항목_수를_조회한다(retried.generationId())));
    }

    /**
     * 만료 행을 지운 뒤 insert 가 unique key 가 아닌 제약으로 실패하면, rollback 으로 그 만료 행이
     * 되살아난다. 수렴 경로가 그 행을 기존 결과라고 돌려주면 원래 예외를 삼킨다. 되살아난 행은 만료
     * 상태이므로 걸러져야 하고, 호출자는 제약 위반을 그대로 받아야 한다.
     */
    @Test
    void 만료_행을_지운_뒤_다른_제약으로_실패하면_원래_예외를_올린다() {
        UUID key = UUID.randomUUID();
        GenerationStartResult first = startService.start(READER_ID, key, 명령(PURPOSE));
        생성을_실패로_끝낸다(first.generationId());
        clock.set(EXPIRES_AT);

        AiRouteGenerationCommand 없는_도서 =
                AiRouteGenerationCommand.forInkBudget(
                        MISSING_BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> startService.start(READER_ID, key, 없는_도서));
        assertEquals(1, 생성_수를_조회한다(READER_ID));
    }

    @Test
    void 만료_직전에는_같은_키가_아직_기존_결과를_받는다() {
        UUID key = UUID.randomUUID();
        GenerationStartResult first = startService.start(READER_ID, key, 명령(PURPOSE));
        생성을_실패로_끝낸다(first.generationId());
        clock.set(EXPIRES_AT.minusNanos(1000));

        GenerationStartResult retried = startService.start(READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.EXISTING_FINAL, retried.kind()),
                () -> assertEquals(first.generationId(), retried.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 다른_독자가_같은_멱등_키를_써도_각자_새로_시작한다() {
        UUID key = UUID.randomUUID();

        GenerationStartResult mine = startService.start(READER_ID, key, 명령(PURPOSE));
        GenerationStartResult others = startService.start(OTHER_READER_ID, key, 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, mine.kind()),
                () -> assertEquals(Kind.NEW, others.kind()),
                () -> assertNotEquals(mine.generationId(), others.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(1, 생성_수를_조회한다(OTHER_READER_ID)));
    }

    // --- 일일 한도 ---------------------------------------------------------

    @Test
    void 한도_직전에는_시작하고_한도에_닿으면_거절한다() {
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT - 1);

        GenerationStartResult last = startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));
        GenerationStartResult overLimit =
                startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.NEW, last.kind()),
                () -> assertEquals(Kind.DAILY_LIMIT, overLimit.kind()),
                () -> assertNull(overLimit.generationId()),
                () -> assertEquals(1, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 다른_키_열한_개가_동시에_들어와도_열_건만_시작한다() throws Exception {
        List<UUID> keys = Stream.generate(UUID::randomUUID).limit(LIMIT + 1L).toList();

        List<GenerationStartResult> results = 동시에_시작한다(READER_ID, keys);

        assertAll(
                () -> assertEquals(LIMIT, 개수(results, Kind.NEW)),
                () -> assertEquals(1, 개수(results, Kind.DAILY_LIMIT)),
                () -> assertEquals(LIMIT, 생성_수를_조회한다(READER_ID)),
                () -> assertEquals(LIMIT, 사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 한_독자가_한도를_다_써도_다른_독자는_시작할_수_있다() {
        사용량을_심는다(READER_ID, USAGE_DATE, LIMIT);

        GenerationStartResult blocked =
                startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));
        GenerationStartResult allowed =
                startService.start(OTHER_READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        assertAll(
                () -> assertEquals(Kind.DAILY_LIMIT, blocked.kind()),
                () -> assertEquals(Kind.NEW, allowed.kind()),
                () -> assertEquals(1, 사용량을_조회한다(OTHER_READER_ID, USAGE_DATE)));
    }

    @Test
    void UTC_자정을_넘기면_사용량을_새로_센다() {
        startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        clock.set(NEXT_UTC_DAY);
        startService.start(READER_ID, UUID.randomUUID(), 명령(PURPOSE));

        assertAll(
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, USAGE_DATE)),
                () -> assertEquals(1, 사용량을_조회한다(READER_ID, NEXT_USAGE_DATE)),
                () -> assertEquals(2, 생성_수를_조회한다(READER_ID)));
    }

    // --- 저장 내용과 트랜잭션 경계 -----------------------------------------

    @Test
    void 새_생성은_지문과_정규화한_목적을_시작_시각과_함께_저장한다() {
        AiRouteGenerationCommand command = 명령("  핵심   개념만 빠르게 ");

        GenerationStartResult started = startService.start(READER_ID, UUID.randomUUID(), command);

        Map<String, Object> row = 생성을_조회한다(started.generationId());
        assertAll(
                () ->
                        assertEquals(
                                AiRouteRequestFingerprint.of(command),
                                row.get("request_fingerprint")),
                () -> assertEquals("GENERATING", row.get("status")),
                () -> assertEquals(PURPOSE, row.get("normalized_purpose")),
                () -> assertEquals("2026-08-06 23:59:59.123456", row.get("created_at")),
                () -> assertNull(row.get("expires_at")));
    }

    @Test
    void 호출자가_트랜잭션을_열면_시작하지_않는다() {
        UUID key = UUID.randomUUID();

        assertThrows(
                IllegalTransactionStateException.class,
                () ->
                        transactionTemplate.executeWithoutResult(
                                status -> startService.start(READER_ID, key, 명령(PURPOSE))));
        assertAll(
                () -> assertEquals(0, 생성_수를_조회한다(READER_ID)),
                () -> assertNull(사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 생성_저장에_실패하면_사용량도_남지_않는다() {
        AiRouteGenerationCommand 없는_도서 =
                AiRouteGenerationCommand.forInkBudget(
                        MISSING_BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> startService.start(READER_ID, UUID.randomUUID(), 없는_도서));
        assertAll(
                () -> assertEquals(0, 생성_수를_조회한다(READER_ID)),
                () -> assertNull(사용량을_조회한다(READER_ID, USAGE_DATE)));
    }

    @Test
    void 멱등_키가_없으면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> startService.start(READER_ID, null, 명령(PURPOSE)));
    }

    @Test
    void 외부_호출_마감_시각이_없으면_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> startService.startBefore(
                        READER_ID, UUID.randomUUID(), 명령(PURPOSE), null));
    }

    // --- 동시 실행 ---------------------------------------------------------

    /**
     * 사용량 행을 미리 잠가 시작 요청을 그 앞에 세우고, 멈춰 있는 동안 {@code 잠금_중_동작} 을 실행한 뒤
     * 잠금을 풀어 준다. 요청은 기존 생성 조회를 마치고(없음) 사용량 날짜를 확정한 직후 멈추므로, 동작은
     * 항상 그 두 지점 사이에 끼어든다.
     *
     * <p>멈춘 지점을 시계 읽기 래치로 관측하는 것이 핵심이다. 고정 시간 대기였다면 실행이 느릴 때 요청이
     * 경합 지점에 닿기 전에 동작이 끝나 회귀를 놓친다.
     *
     * <p>세 경합 테스트가 같은 인터리빙을 쓰므로 여기 한 번만 적는다. 각 테스트가 다른 것은 사용량
     * 시드값과 이 동작, 그리고 단언뿐이다.
     */
    private GenerationStartResult 사용량_잠금_뒤에_시작한다(UUID key, Runnable 잠금_중_동작)
            throws Exception {
        CountDownLatch 사용량_잠금_확보 = new CountDownLatch(1);
        CountDownLatch 사용량_잠금_해제 = new CountDownLatch(1);
        CountDownLatch 날짜_확정 = clock.다음_읽기를_알린다();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> 길목 =
                    executor.submit(
                            () ->
                                    transactionTemplate.executeWithoutResult(
                                            status -> {
                                                사용량_행을_잠근다(READER_ID, USAGE_DATE);
                                                사용량_잠금_확보.countDown();
                                                해제를_기다린다(사용량_잠금_해제);
                                            }));
            Future<GenerationStartResult> 뒤따르는_요청 =
                    executor.submit(
                            () -> {
                                assertTrue(사용량_잠금_확보.await(5, TimeUnit.SECONDS));
                                return startService.start(READER_ID, key, 명령(PURPOSE));
                            });

            assertTrue(날짜_확정.await(10, TimeUnit.SECONDS));
            잠금_중_동작.run();
            사용량_잠금_해제.countDown();

            길목.get(30, TimeUnit.SECONDS);
            return 뒤따르는_요청.get(30, TimeUnit.SECONDS);
        } finally {
            사용량_잠금_해제.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private List<GenerationStartResult> 동시에_시작한다(long readerId, List<UUID> keys)
            throws Exception {
        return 동시에_실행한다(
                keys.stream()
                        .map(
                                key ->
                                        (Callable<GenerationStartResult>)
                                                () -> startService.start(readerId, key, 명령(PURPOSE)))
                        .toList());
    }

    private List<GenerationStartResult> 독자별로_동시에_시작한다(List<Long> readerIds) throws Exception {
        return 동시에_실행한다(
                readerIds.stream()
                        .map(
                                readerId ->
                                        (Callable<GenerationStartResult>)
                                                () ->
                                                        startService.start(
                                                                readerId,
                                                                UUID.randomUUID(),
                                                                명령(PURPOSE)))
                        .toList());
    }

    private List<GenerationStartResult> 동시에_실행한다(List<Callable<GenerationStartResult>> calls)
            throws Exception {
        int count = calls.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<GenerationStartResult>> futures = new ArrayList<>();
            for (Callable<GenerationStartResult> call : calls) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return call.call();
                                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<GenerationStartResult> results = new ArrayList<>();
            for (Future<GenerationStartResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static long 개수(List<GenerationStartResult> results, Kind kind) {
        return results.stream().filter(result -> result.kind() == kind).count();
    }

    // --- fixture -----------------------------------------------------------

    private static List<AiRouteResultItem> 두_항목() {
        return List.of(
                new AiRouteResultItem(
                        FIRST_PAGE_ID, 1, AiRouteItemRelevance.HIGH, false, AiRouteItemRole.CORE),
                new AiRouteResultItem(
                        SECOND_PAGE_ID,
                        2,
                        AiRouteItemRelevance.MEDIUM,
                        true,
                        AiRouteItemRole.PREREQUISITE));
    }

    private int 항목_수를_조회한다(UUID generationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation_item WHERE generation_id = ?",
                Integer.class,
                generationId.toString());
    }

    private static AiRouteGenerationCommand 명령(String rawPurpose) {
        return AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, rawPurpose, 5, 100);
    }

    private void 생성을_실패로_끝낸다(UUID generationId) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    AiRouteGeneration generation =
                            generationRepository.findById(generationId).orElseThrow();
                    generation.fail("AI_ROUTE_TEST_FAILURE", COMPLETED_AT, EXPIRES_AT);
                });
    }

    /**
     * 서비스를 거치지 않고 {@code GENERATING} 행을 넣는다. 호출자의 transaction 이 commit 하기 전까지
     * 이 행은 다른 스냅샷 조회에 보이지 않으면서 unique key 는 이미 차지한 상태가 된다.
     */
    private void 생성을_직접_넣는다(UUID generationId, UUID idempotencyKey) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_generation
                    (generation_id, reader_id, book_id, content_version, idempotency_key,
                     request_fingerprint, normalized_purpose, request_type, max_additional_ink,
                     status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'INK_BUDGET', 5, 'GENERATING',
                        '2026-08-06 23:59:59.123456')
                """,
                generationId.toString(),
                READER_ID,
                BOOK_ID,
                CONTENT_VERSION,
                idempotencyKey.toString(),
                AiRouteRequestFingerprint.of(명령(PURPOSE)),
                PURPOSE);
    }

    /** 뒤따르는 요청을 이 행에서 멈춰 세운다. transaction 이 끝날 때까지 잠금을 쥐고 있는다. */
    private void 사용량_행을_잠근다(long readerId, LocalDate usageDate) {
        jdbcTemplate.queryForObject(
                """
                SELECT generation_count
                FROM ai_route_daily_usage
                WHERE reader_id = ? AND usage_date = ?
                FOR UPDATE
                """,
                Integer.class,
                readerId,
                usageDate.toString());
    }

    private static void 해제를_기다린다(CountDownLatch 해제) {
        try {
            if (!해제.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 해제 신호를 기다리다 시간이 지났습니다.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 유지가 중단됐습니다.", interrupted);
        }
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'hash', '2026-08-06 00:00:00.000000')
                """,
                readerId,
                "scrum-458-reader-" + readerId + "@example.com");
    }

    private void 도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '인문', 'SCRUM-458 테스트 도서', '테스트 저자', 2, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지')
                """,
                FIRST_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 2, 'TEXT', '둘째 페이지')
                """,
                SECOND_PAGE_ID,
                BOOK_ID);
    }

    private void 사용량을_심는다(long readerId, LocalDate usageDate, int generationCount) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_route_daily_usage (reader_id, usage_date, generation_count)
                VALUES (?, ?, ?)
                """,
                readerId,
                usageDate.toString(),
                generationCount);
    }

    private int 생성_수를_조회한다(long readerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_route_generation WHERE reader_id = ?",
                Integer.class,
                readerId);
    }

    private Integer 사용량을_조회한다(long readerId, LocalDate usageDate) {
        List<Integer> counts =
                jdbcTemplate.queryForList(
                        """
                        SELECT generation_count
                        FROM ai_route_daily_usage
                        WHERE reader_id = ? AND usage_date = ?
                        """,
                        Integer.class,
                        readerId,
                        usageDate.toString());
        return counts.isEmpty() ? null : counts.getFirst();
    }

    private Map<String, Object> 생성을_조회한다(UUID generationId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT request_fingerprint, status, normalized_purpose, expires_at,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at
                FROM ai_route_generation
                WHERE generation_id = ?
                """,
                generationId.toString());
    }

    private void 테스트_데이터를_정리한다() {
        for (long readerId : READER_IDS) {
            jdbcTemplate.update(
                    """
                    DELETE FROM ai_route_generation_item
                    WHERE generation_id IN (
                        SELECT generation_id FROM ai_route_generation WHERE reader_id = ?
                    )
                    """,
                    readerId);
            jdbcTemplate.update("DELETE FROM ai_route_generation WHERE reader_id = ?", readerId);
            jdbcTemplate.update("DELETE FROM ai_route_daily_usage WHERE reader_id = ?", readerId);
        }
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
        for (long readerId : READER_IDS) {
            jdbcTemplate.update("DELETE FROM reader WHERE id = ?", readerId);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(STARTED_AT);
        }
    }

    /**
     * UTC 자정 경계를 한 컨텍스트 안에서 넘나들어야 해서 {@link Clock#fixed} 대신 옮길 수 있는 시계를
     * 쓴다. 동시 실행 테스트가 다른 스레드에서 읽으므로 {@code volatile} 이다.
     */
    static class MutableClock extends Clock {

        private volatile Instant instant;
        private volatile CountDownLatch 읽힘 = new CountDownLatch(0);

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        /**
         * 다음 읽기를 관측할 래치를 건다. 서비스가 사용량 날짜를 확정한 시점을 테스트가 알아야 경합
         * 순서를 시간이 아니라 신호로 고정할 수 있다. 첫 읽기 뒤에는 열린 래치라 아무 일도 하지 않는다.
         */
        CountDownLatch 다음_읽기를_알린다() {
            CountDownLatch 신호 = new CountDownLatch(1);
            읽힘 = 신호;
            return 신호;
        }

        @Override
        public Instant instant() {
            읽힘.countDown();
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }
    }
}
