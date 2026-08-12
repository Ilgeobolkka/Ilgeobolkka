package com.example.ilgeobolkka.support.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import com.example.ilgeobolkka.airoute.entity.AiReadingRouteItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteCurrent;
import com.example.ilgeobolkka.airoute.entity.AiRouteCurrentId;
import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsage;
import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsageId;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.entity.AiRouteNoRouteReason;
import com.example.ilgeobolkka.airoute.entity.AiRoutePrerequisite;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteCurrentRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteDailyUsageRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.ilgeobolkka.airoute.repository.AiRoutePrerequisiteRepository;
import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class AiRouteEntityMappingMySqlIntegrationTest {

    private static final long READER_ID = 61_000L;
    private static final long OTHER_READER_ID = 61_001L;
    private static final long BOOK_ID = 62_000L;
    private static final long OTHER_BOOK_ID = 62_001L;
    private static final long FIRST_PAGE_ID = 63_000L;
    private static final long SECOND_PAGE_ID = 63_001L;
    private static final UUID GENERATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000061000");
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("00000000-0000-0000-0000-000000061001");
    private static final Instant CREATED_AT = Instant.parse("2026-08-08T00:00:00.123456Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-08T00:01:00.123456Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-08T00:31:00.123456Z");
    private static final Instant OPENED_AT = Instant.parse("2026-08-08T00:02:00.123456Z");
    private static final Instant FEEDBACK_AT = Instant.parse("2026-08-08T00:03:00.123456Z");
    private static final Set<Class<?>> AI_ENTITY_TYPES =
            Set.of(
                    AiRoutePrerequisite.class,
                    AiRouteGeneration.class,
                    AiRouteGenerationItem.class,
                    AiReadingRoute.class,
                    AiReadingRouteItem.class,
                    AiRouteCurrent.class,
                    AiRouteDailyUsage.class);
    private static final Set<Class<?>> AI_IDENTITY_ENTITY_TYPES =
            Set.of(
                    AiRoutePrerequisite.class,
                    AiRouteGenerationItem.class,
                    AiReadingRoute.class,
                    AiReadingRouteItem.class);
    private static final Set<String> AI_ROUTE_EXTENSION_COLUMNS =
            Set.of(
                    "book.content_version",
                    "book.ai_route_supported",
                    "book.ai_external_transfer_allowed",
                    "book.ai_data_policy_version",
                    "book_page.ai_analysis_text",
                    "book_page.ai_public_guide_topic",
                    "book_page.estimated_reading_seconds",
                    "book_page.embedding_model",
                    "book_page.embedding_dimensions",
                    "book_page.embedding_json",
                    "book_page.duplicate_group_keys");

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Statistics statistics;
    private final AiRoutePrerequisiteRepository prerequisiteRepository;
    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final AiReadingRouteRepository readingRouteRepository;
    private final AiReadingRouteItemRepository readingRouteItemRepository;
    private final AiRouteCurrentRepository currentRepository;
    private final AiRouteDailyUsageRepository dailyUsageRepository;

    @Autowired
    AiRouteEntityMappingMySqlIntegrationTest(
            EntityManager entityManager,
            EntityManagerFactory entityManagerFactory,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiRoutePrerequisiteRepository prerequisiteRepository,
            AiRouteGenerationRepository generationRepository,
            AiRouteGenerationItemRepository generationItemRepository,
            AiReadingRouteRepository readingRouteRepository,
            AiReadingRouteItemRepository readingRouteItemRepository,
            AiRouteCurrentRepository currentRepository,
            AiRouteDailyUsageRepository dailyUsageRepository) {
        this.entityManager = entityManager;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.prerequisiteRepository = prerequisiteRepository;
        this.generationRepository = generationRepository;
        this.generationItemRepository = generationItemRepository;
        this.readingRouteRepository = readingRouteRepository;
        this.readingRouteItemRepository = readingRouteItemRepository;
        this.currentRepository = currentRepository;
        this.dailyUsageRepository = dailyUsageRepository;
    }

    @Test
    void 저장_경로가_있는_생성_재조회는_단일_SELECT만_실행한다() {
        기준_독자_도서_페이지를_생성한다();
        AiRouteGeneration generation = 잉크_예산_생성을_시작한다();
        generation.completeRoute(COMPLETED_AT, EXPIRES_AT);
        generation = generationRepository.saveAndFlush(generation);
        AiReadingRoute savedRoute =
                AiReadingRoute.createWithInkBudget(
                        GENERATION_ID,
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "목적",
                        3,
                        CREATED_AT);
        readingRouteRepository.saveAndFlush(savedRoute);
        generation.saveAsRoute(savedRoute);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        AiRouteGeneration loadedGeneration =
                generationRepository.findById(GENERATION_ID).orElseThrow();

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.SAVED, loadedGeneration.getStatus()),
                () -> assertEquals(savedRoute.getId(), loadedGeneration.getSavedRouteId()),
                () -> assertEquals(1, statistics.getPrepareStatementCount()));
    }

    @Test
    void AI_경로_7개_테이블이_JPA_엔티티로_등록된다() {
        List<String> entityNames =
                entityManager.getMetamodel().getEntities().stream()
                        .map(EntityType::getJavaType)
                        .filter(AI_ENTITY_TYPES::contains)
                        .map(Class::getSimpleName)
                        .sorted()
                        .toList();

        assertEquals(
                List.of(
                        "AiReadingRoute",
                        "AiReadingRouteItem",
                        "AiRouteCurrent",
                        "AiRouteDailyUsage",
                        "AiRouteGeneration",
                        "AiRouteGenerationItem",
                        "AiRoutePrerequisite"),
                entityNames);
    }

    @Test
    void AUTO_INCREMENT_AI_엔티티는_IDENTITY_전략을_사용한다() {
        AI_IDENTITY_ENTITY_TYPES.forEach(
                entityType -> {
                    try {
                        GeneratedValue generatedValue =
                                entityType.getDeclaredField("id").getAnnotation(GeneratedValue.class);

                        assertNotNull(generatedValue, entityType.getSimpleName());
                        assertEquals(
                                GenerationType.IDENTITY,
                                generatedValue.strategy(),
                                entityType.getSimpleName());
                    } catch (NoSuchFieldException exception) {
                        throw new AssertionError(
                                entityType.getSimpleName() + "에 id 필드가 없습니다.", exception);
                    }
                });
    }

    @Test
    void AI_생성_팩토리는_canonical_command를_입력으로_받는다() {
        List<Method> factoryMethods =
                Arrays.stream(AiRouteGeneration.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .filter(method -> Modifier.isStatic(method.getModifiers()))
                        .filter(method -> method.getReturnType() == AiRouteGeneration.class)
                        .toList();

        assertAll(
                () -> assertEquals(1, factoryMethods.size()),
                () ->
                        assertTrue(
                                factoryMethods.stream()
                                        .allMatch(
                                                method ->
                                                        Arrays.asList(method.getParameterTypes())
                                                                .contains(
                                                                        AiRouteGenerationCommand
                                                                                .class))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiRouteGeneration.start(
                                                GENERATION_ID,
                                                READER_ID,
                                                IDEMPOTENCY_KEY,
                                                "a".repeat(64),
                                                null,
                                                CREATED_AT)));
    }

    @Test
    void AI_엔티티의_컬럼명과_NULL_허용은_F01_물리_스키마와_일치한다() {
        Set<String> mappedColumns = new TreeSet<>();
        Set<String> mappedNullableColumns = new TreeSet<>();

        AI_ENTITY_TYPES.forEach(
                entityType ->
                        엔티티_컬럼을_추가한다(
                                entityType, mappedColumns, mappedNullableColumns, false));
        엔티티_컬럼을_추가한다(Book.class, mappedColumns, mappedNullableColumns, true);
        엔티티_컬럼을_추가한다(BookPage.class, mappedColumns, mappedNullableColumns, true);

        assertAll(
                () -> assertEquals(new TreeSet<>(물리_컬럼을_조회한다(false)), mappedColumns),
                () ->
                        assertEquals(
                                new TreeSet<>(물리_컬럼을_조회한다(true)),
                                mappedNullableColumns));
    }

    @Test
    void Enum의_DB_문자열은_API_철자와_일치한다() {
        assertAll(
                () ->
                        assertEquals(
                                List.of("INK_BUDGET", "OWNED_DEPTH"),
                                이름을_조회한다(AiRouteRequestType.values())),
                () ->
                        assertEquals(
                                List.of("QUICK", "BALANCED", "DEEP"),
                                이름을_조회한다(AiRouteDepth.values())),
                () ->
                        assertEquals(
                                List.of(
                                        "GENERATING",
                                        "ROUTE",
                                        "NO_ROUTE",
                                        "FAILED",
                                        "SAVED",
                                        "CONSUMED"),
                                이름을_조회한다(AiRouteGenerationStatus.values())),
                () ->
                        assertEquals(
                                List.of(
                                        "NO_RELEVANT_PAGES",
                                        "INSUFFICIENT_BUDGET",
                                        "INSUFFICIENT_DEPTH"),
                                이름을_조회한다(AiRouteNoRouteReason.values())),
                () ->
                        assertEquals(
                                List.of("HIGH", "MEDIUM"),
                                이름을_조회한다(AiRouteItemRelevance.values())),
                () ->
                        assertEquals(
                                List.of(
                                        "PREREQUISITE",
                                        "CORE",
                                        "EXAMPLE",
                                        "COUNTERPOINT",
                                        "CONCLUSION"),
                                이름을_조회한다(AiRouteItemRole.values())),
                () ->
                        assertEquals(
                                List.of("HELPFUL", "NEUTRAL", "NOT_HELPFUL"),
                                이름을_조회한다(AiReadingRouteFeedback.values())));
    }

    @Test
    void 모든_AI_엔티티를_저장하고_연관관계와_JSON을_조회한다() throws Exception {
        기준_독자_도서_페이지를_생성한다();

        Book book = entityManager.find(Book.class, BOOK_ID);
        BookPage firstPage = entityManager.find(BookPage.class, FIRST_PAGE_ID);
        BookPage secondPage = entityManager.find(BookPage.class, SECOND_PAGE_ID);
        book.updateAiRouteMetadata("ai-route-v2", true, "policy-v1");
        book.activateAiRouteSupport();
        firstPage.updateAiRouteMetadata(
                "비공개 분석 텍스트",
                "공개 가이드",
                180,
                "text-embedding-test",
                List.of(0.25, -0.5),
                List.of("group-1"));
        secondPage.updateAiRouteMetadata(
                "두 번째 비공개 분석 텍스트",
                "두 번째 공개 가이드",
                120,
                "text-embedding-test",
                List.of(0.5, 0.75),
                List.of());

        AiRoutePrerequisite prerequisite =
                prerequisiteRepository.save(
                        AiRoutePrerequisite.create(BOOK_ID, 1, 2));
        AiRouteGeneration generation =
                AiRouteGeneration.start(
                        GENERATION_ID,
                        READER_ID,
                        IDEMPOTENCY_KEY,
                        "a".repeat(64),
                        AiRouteGenerationCommand.forInkBudget(
                                BOOK_ID,
                                "ai-route-v2",
                                "JPA 매핑을 이해하고 싶다",
                                3,
                                3),
                        CREATED_AT);
        generation.completeRoute(COMPLETED_AT, EXPIRES_AT);
        generation = generationRepository.save(generation);
        AiRouteGenerationItem generationItem =
                generationItemRepository.save(
                        AiRouteGenerationItem.create(
                                GENERATION_ID,
                                BOOK_ID,
                                FIRST_PAGE_ID,
                                1,
                                AiRouteItemRelevance.HIGH,
                                false,
                                AiRouteItemRole.CORE));
        AiReadingRoute readingRoute =
                AiReadingRoute.createWithInkBudget(
                        GENERATION_ID,
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "JPA 매핑을 이해하고 싶다",
                        3,
                        CREATED_AT);
        readingRoute.complete(COMPLETED_AT);
        readingRoute.updateFeedback(AiReadingRouteFeedback.HELPFUL, FEEDBACK_AT);
        readingRouteRepository.saveAndFlush(readingRoute);
        generation.saveAsRoute(readingRoute);
        AiReadingRouteItem readingRouteItem =
                AiReadingRouteItem.create(
                        readingRoute.getId(),
                        BOOK_ID,
                        FIRST_PAGE_ID,
                        1,
                        AiRouteItemRelevance.HIGH,
                        false,
                        AiRouteItemRole.CORE);
        readingRouteItem.markOpened(OPENED_AT);
        readingRouteItemRepository.save(readingRouteItem);
        AiRouteCurrent current =
                AiRouteCurrent.select(READER_ID, BOOK_ID, readingRoute, CREATED_AT);
        currentRepository.save(current);
        AiRouteDailyUsage dailyUsage =
                AiRouteDailyUsage.start(READER_ID, LocalDate.of(2026, 8, 8));
        dailyUsage.increment();
        dailyUsageRepository.save(dailyUsage);

        assertEquals(readingRoute.getId(), generation.getSavedRouteId());
        assertSame(readingRoute, current.getRoute());

        entityManager.flush();
        entityManager.clear();

        Book loadedBook = entityManager.find(Book.class, BOOK_ID);
        BookPage loadedFirstPage = entityManager.find(BookPage.class, FIRST_PAGE_ID);
        AiRoutePrerequisite loadedPrerequisite =
                prerequisiteRepository.findById(prerequisite.getId()).orElseThrow();
        AiRouteGeneration loadedGeneration =
                generationRepository.findById(GENERATION_ID).orElseThrow();
        AiRouteGenerationItem loadedGenerationItem =
                generationItemRepository.findById(generationItem.getId()).orElseThrow();
        AiReadingRoute loadedReadingRoute =
                readingRouteRepository.findById(readingRoute.getId()).orElseThrow();
        AiReadingRouteItem loadedReadingRouteItem =
                readingRouteItemRepository.findById(readingRouteItem.getId()).orElseThrow();
        AiRouteCurrent loadedCurrent =
                currentRepository
                        .findById(new AiRouteCurrentId(READER_ID, BOOK_ID))
                        .orElseThrow();
        AiRouteDailyUsage loadedDailyUsage =
                dailyUsageRepository
                        .findById(
                                new AiRouteDailyUsageId(
                                        READER_ID, LocalDate.of(2026, 8, 8)))
                        .orElseThrow();
        String serializedPage = objectMapper.writeValueAsString(loadedFirstPage);

        assertAll(
                () -> assertEquals("ai-route-v2", loadedBook.getContentVersion()),
                () -> assertEquals("policy-v1", loadedBook.getAiDataPolicyVersion()),
                () -> assertEquals(List.of(0.25, -0.5), loadedFirstPage.getEmbedding()),
                () -> assertEquals(List.of("group-1"), loadedFirstPage.getDuplicateGroupKeys()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> loadedFirstPage.getEmbedding().set(0, 1.0)),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> loadedFirstPage.getDuplicateGroupKeys().add("group-2")),
                () -> assertEquals(FIRST_PAGE_ID, loadedPrerequisite.getPrerequisitePage().getId()),
                () -> assertEquals(SECOND_PAGE_ID, loadedPrerequisite.getDependentPage().getId()),
                () -> assertEquals(READER_ID, loadedGeneration.getReader().getId()),
                () -> assertEquals(BOOK_ID, loadedGeneration.getBook().getId()),
                () -> assertEquals(AiRouteGenerationStatus.SAVED, loadedGeneration.getStatus()),
                () -> assertEquals(readingRoute.getId(), loadedGeneration.getSavedRouteId()),
                () -> assertEquals(FIRST_PAGE_ID, loadedGenerationItem.getBookPage().getId()),
                () -> assertEquals(READER_ID, loadedReadingRoute.getReader().getId()),
                () -> assertEquals(FIRST_PAGE_ID, loadedReadingRouteItem.getBookPage().getId()),
                () -> assertEquals(OPENED_AT, loadedReadingRouteItem.getOpenedAt()),
                () -> assertEquals(readingRoute.getId(), loadedCurrent.getRoute().getId()),
                () -> assertEquals(1, loadedDailyUsage.getGenerationCount()),
                () -> assertFalse(serializedPage.contains("aiAnalysisText")),
                () -> assertFalse(serializedPage.contains("embeddingModel")),
                () -> assertFalse(serializedPage.contains("embeddingDimensions")),
                () -> assertFalse(serializedPage.contains("embedding")),
                () -> assertFalse(serializedPage.contains("duplicateGroupKeys")));
    }

    @Test
    void 현재_경로를_바꾸면_같은_영속성_컨텍스트의_연관관계도_바뀐다() {
        기준_독자_도서_페이지를_생성한다();
        AiReadingRoute firstRoute =
                AiReadingRoute.createWithInkBudget(
                        UUID.fromString("00000000-0000-0000-0000-000000061010"),
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "첫 번째 목적",
                        3,
                        CREATED_AT);
        AiReadingRoute secondRoute =
                AiReadingRoute.createWithInkBudget(
                        UUID.fromString("00000000-0000-0000-0000-000000061011"),
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "두 번째 목적",
                        3,
                        CREATED_AT);
        readingRouteRepository.saveAndFlush(firstRoute);
        readingRouteRepository.saveAndFlush(secondRoute);
        currentRepository.saveAndFlush(
                AiRouteCurrent.select(READER_ID, BOOK_ID, firstRoute, CREATED_AT));
        entityManager.clear();

        AiRouteCurrent current =
                currentRepository
                        .findById(new AiRouteCurrentId(READER_ID, BOOK_ID))
                        .orElseThrow();
        assertEquals(firstRoute.getId(), current.getRoute().getId());

        current.changeRoute(secondRoute, OPENED_AT);

        assertAll(
                () -> assertEquals(secondRoute.getId(), current.getRouteId()),
                () -> assertEquals(secondRoute.getId(), current.getRoute().getId()));

        entityManager.flush();
        entityManager.clear();

        AiRouteCurrent reloadedCurrent =
                currentRepository
                        .findById(new AiRouteCurrentId(READER_ID, BOOK_ID))
                        .orElseThrow();
        assertEquals(secondRoute.getId(), reloadedCurrent.getRoute().getId());
    }

    @Test
    void 저장_경로는_생성의_소유자와_요청_스냅샷이_모두_일치해야_한다() {
        기준_독자_도서_페이지를_생성한다();
        불일치_독자와_도서를_생성한다();
        List<AiReadingRoute> mismatchedRoutes =
                List.of(
                        AiReadingRoute.createWithInkBudget(
                                UUID.fromString("00000000-0000-0000-0000-000000061020"),
                                OTHER_READER_ID,
                                BOOK_ID,
                                "ai-route-v2",
                                "목적",
                                3,
                                CREATED_AT),
                        AiReadingRoute.createWithInkBudget(
                                UUID.fromString("00000000-0000-0000-0000-000000061021"),
                                READER_ID,
                                OTHER_BOOK_ID,
                                "ai-route-v2",
                                "목적",
                                3,
                                CREATED_AT),
                        AiReadingRoute.createWithInkBudget(
                                UUID.fromString("00000000-0000-0000-0000-000000061022"),
                                READER_ID,
                                BOOK_ID,
                                "other-version",
                                "목적",
                                3,
                                CREATED_AT),
                        AiReadingRoute.createWithInkBudget(
                                UUID.fromString("00000000-0000-0000-0000-000000061023"),
                                READER_ID,
                                BOOK_ID,
                                "ai-route-v2",
                                "다른 목적",
                                3,
                                CREATED_AT),
                        AiReadingRoute.createWithInkBudget(
                                UUID.fromString("00000000-0000-0000-0000-000000061024"),
                                READER_ID,
                                BOOK_ID,
                                "ai-route-v2",
                                "목적",
                                4,
                                CREATED_AT),
                        AiReadingRoute.createWithOwnedDepth(
                                UUID.fromString("00000000-0000-0000-0000-000000061025"),
                                READER_ID,
                                BOOK_ID,
                                "ai-route-v2",
                                "목적",
                                AiRouteDepth.BALANCED,
                                CREATED_AT));

        mismatchedRoutes.forEach(
                route -> {
                    readingRouteRepository.saveAndFlush(route);
                    AiRouteGeneration generation = 잉크_예산_생성을_시작한다(route.getGenerationId());
                    generation.completeRoute(COMPLETED_AT, EXPIRES_AT);

                    assertThrows(
                            IllegalArgumentException.class,
                            () -> generation.saveAsRoute(route),
                            route.getGenerationId().toString());
                });
    }

    @Test
    void AI_엔티티는_허용한_상태만_전이한다() {
        기준_독자_도서_페이지를_생성한다();
        AiReadingRoute savedRoute =
                AiReadingRoute.createWithInkBudget(
                        GENERATION_ID,
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "목적",
                        3,
                        CREATED_AT);
        AiReadingRoute otherGenerationRoute =
                AiReadingRoute.createWithInkBudget(
                        UUID.fromString("00000000-0000-0000-0000-000000061012"),
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "다른 생성의 목적",
                        3,
                        CREATED_AT);
        readingRouteRepository.saveAndFlush(savedRoute);
        readingRouteRepository.saveAndFlush(otherGenerationRoute);
        AiRouteGeneration generation = 잉크_예산_생성을_시작한다();
        generation.completeRoute(COMPLETED_AT, EXPIRES_AT);
        generation.saveAsRoute(savedRoute);
        AiRouteGeneration missingRouteGeneration = 잉크_예산_생성을_시작한다();
        missingRouteGeneration.completeRoute(COMPLETED_AT, EXPIRES_AT);
        AiRouteGeneration unsavedRouteGeneration = 잉크_예산_생성을_시작한다();
        unsavedRouteGeneration.completeRoute(COMPLETED_AT, EXPIRES_AT);
        AiRouteGeneration otherRouteGeneration = 잉크_예산_생성을_시작한다();
        otherRouteGeneration.completeRoute(COMPLETED_AT, EXPIRES_AT);
        AiReadingRoute unsavedRoute =
                AiReadingRoute.createWithInkBudget(
                        GENERATION_ID,
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "저장 전 경로",
                        3,
                        CREATED_AT);

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.SAVED, generation.getStatus()),
                () -> assertNull(generation.getNormalizedPurpose()),
                () -> assertNull(generation.getRequestType()),
                () -> assertNull(generation.getMaxAdditionalInk()),
                () -> assertEquals(savedRoute.getId(), generation.getSavedRouteId()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> missingRouteGeneration.saveAsRoute(null)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> unsavedRouteGeneration.saveAsRoute(unsavedRoute)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> otherRouteGeneration.saveAsRoute(otherGenerationRoute)),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> generation.completeRoute(COMPLETED_AT, EXPIRES_AT)));

        generation.consume();

        AiRouteGeneration insufficientBudget = 잉크_예산_생성을_시작한다();
        insufficientBudget.completeNoRoute(
                AiRouteNoRouteReason.INSUFFICIENT_BUDGET, 4, COMPLETED_AT, EXPIRES_AT);
        AiRouteGeneration insufficientDepth = 소장_깊이_생성을_시작한다();
        insufficientDepth.completeNoRoute(
                AiRouteNoRouteReason.INSUFFICIENT_DEPTH, null, COMPLETED_AT, EXPIRES_AT);
        AiRouteGeneration failed = 잉크_예산_생성을_시작한다();
        failed.fail("AI_ROUTE_PROVIDER_ERROR", COMPLETED_AT, EXPIRES_AT);
        AiReadingRoute route =
                AiReadingRoute.createWithOwnedDepth(
                        GENERATION_ID,
                        READER_ID,
                        BOOK_ID,
                        "ai-route-v2",
                        "목적",
                        AiRouteDepth.BALANCED,
                        CREATED_AT);
        AiReadingRouteItem item =
                AiReadingRouteItem.create(
                        1L,
                        BOOK_ID,
                        FIRST_PAGE_ID,
                        1,
                        AiRouteItemRelevance.HIGH,
                        false,
                        AiRouteItemRole.CORE);

        assertThrows(
                IllegalStateException.class,
                () -> route.updateFeedback(AiReadingRouteFeedback.HELPFUL, COMPLETED_AT));
        route.complete(COMPLETED_AT);
        assertThrows(
                IllegalArgumentException.class,
                () -> route.updateFeedback(AiReadingRouteFeedback.HELPFUL, CREATED_AT));
        route.updateFeedback(AiReadingRouteFeedback.HELPFUL, OPENED_AT);
        assertThrows(
                IllegalArgumentException.class,
                () -> route.updateFeedback(AiReadingRouteFeedback.NEUTRAL, COMPLETED_AT));
        item.markOpened(COMPLETED_AT);
        item.markOpened(OPENED_AT);

        assertAll(
                () -> assertEquals(AiRouteGenerationStatus.CONSUMED, generation.getStatus()),
                () -> assertNull(generation.getSavedRouteId()),
                () ->
                        assertEquals(
                                AiRouteNoRouteReason.INSUFFICIENT_BUDGET,
                                insufficientBudget.getNoRouteReason()),
                () -> assertEquals(4, insufficientBudget.getMinimumRequiredInk()),
                () ->
                        assertEquals(
                                AiRouteNoRouteReason.INSUFFICIENT_DEPTH,
                                insufficientDepth.getNoRouteReason()),
                () -> assertNull(insufficientDepth.getMinimumRequiredInk()),
                () -> assertEquals(AiRouteGenerationStatus.FAILED, failed.getStatus()),
                () -> assertEquals("AI_ROUTE_PROVIDER_ERROR", failed.getFailureCode()),
                () -> assertEquals(AiReadingRouteFeedback.HELPFUL, route.getFeedback()),
                () -> assertEquals(OPENED_AT, route.getFeedbackAt()),
                () -> assertEquals(COMPLETED_AT, item.getOpenedAt()),
                () -> assertThrows(IllegalStateException.class, generation::consume),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        잉크_예산_생성을_시작한다()
                                                .completeNoRoute(
                                                        AiRouteNoRouteReason.INSUFFICIENT_BUDGET,
                                                        3,
                                                        COMPLETED_AT,
                                                        EXPIRES_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        잉크_예산_생성을_시작한다()
                                                .completeNoRoute(
                                                        AiRouteNoRouteReason.INSUFFICIENT_DEPTH,
                                                        null,
                                                        COMPLETED_AT,
                                                        EXPIRES_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> AiRoutePrerequisite.create(BOOK_ID, 1, 1)),
                () ->
                        assertThrows(
                                InvalidAiRouteGenerationInputException.class,
                                () ->
                                        AiRouteGenerationCommand.forInkBudget(
                                                BOOK_ID,
                                                "ai-route-v2",
                                                "목적",
                                                -1,
                                                3)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiReadingRoute.createWithOwnedDepth(
                                                GENERATION_ID,
                                                READER_ID,
                                                BOOK_ID,
                                                "ai-route-v2",
                                                "목적",
                                                null,
                                                CREATED_AT)));
    }

    @Test
    void 도서와_페이지_AI_메타데이터는_지원_조건과_필드_형상을_보장한다() {
        기준_독자_도서_페이지를_생성한다();
        Book book = entityManager.find(Book.class, BOOK_ID);
        BookPage page = entityManager.find(BookPage.class, FIRST_PAGE_ID);
        List<Double> embedding = new ArrayList<>(List.of(0.25, -0.5));
        List<String> duplicateGroups = new ArrayList<>(List.of("group-1"));

        assertThrows(IllegalStateException.class, book::activateAiRouteSupport);
        book.updateAiRouteMetadata("ai-route-v2", true, " ");
        assertThrows(IllegalStateException.class, book::activateAiRouteSupport);

        book.updateAiRouteMetadata("ai-route-v2", true, "policy-v1");
        book.activateAiRouteSupport();
        page.updateAiRouteMetadata(
                "분석",
                "가이드",
                180,
                "text-embedding-test",
                embedding,
                duplicateGroups);
        embedding.add(0.75);
        duplicateGroups.add("group-2");

        assertAll(
                () -> assertTrue(book.isAiRouteSupported()),
                () -> assertEquals(2, page.getEmbeddingDimensions()),
                () -> assertEquals(List.of(0.25, -0.5), page.getEmbedding()),
                () -> assertEquals(List.of("group-1"), page.getDuplicateGroupKeys()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        page.updateAiRouteMetadata(
                                                "분석",
                                                "가이드",
                                                0,
                                                "model",
                                                List.of(0.1),
                                                List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        page.updateAiRouteMetadata(
                                                "분석",
                                                "가이드",
                                                180,
                                                "model",
                                                List.of(),
                                                List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        page.updateAiRouteMetadata(
                                                " ",
                                                "가이드",
                                                180,
                                                "model",
                                                List.of(0.1),
                                                List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        page.updateAiRouteMetadata(
                                                "분석",
                                                " ",
                                                180,
                                                "model",
                                                List.of(0.1),
                                                List.of())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        page.updateAiRouteMetadata(
                                                "분석",
                                                "가이드",
                                                180,
                                                " ",
                                                List.of(0.1),
                                                List.of())));

        book.deactivateAiRouteSupport();
        page.clearAiRouteMetadata();

        assertAll(
                () -> assertFalse(book.isAiRouteSupported()),
                () -> assertNull(page.getAiAnalysisText()),
                () -> assertNull(page.getEmbedding()),
                () -> assertNull(page.getDuplicateGroupKeys()));
    }

    private void 기준_독자_도서_페이지를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'ai-route-mapping@example.com', 'encoded-password',
                        '2026-08-08 00:00:00.123456')
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, '기술', 'AI 경로 매핑', '테스트 저자', NULL, NULL, 2, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지', NULL),
                       (?, ?, 2, 'TEXT', '두 번째 페이지', NULL)
                """,
                FIRST_PAGE_ID,
                BOOK_ID,
                SECOND_PAGE_ID,
                BOOK_ID);
    }

    private void 불일치_독자와_도서를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'other-ai-route-mapping@example.com', 'encoded-password',
                        '2026-08-08 00:00:00.123456')
                """,
                OTHER_READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, '기술', '다른 AI 경로 매핑', '다른 테스트 저자', NULL, NULL, 1, 10000)
                """,
                OTHER_BOOK_ID);
    }

    private void 엔티티_컬럼을_추가한다(
            Class<?> entityType,
            Set<String> mappedColumns,
            Set<String> mappedNullableColumns,
            boolean extensionOnly) {
        String tableName = entityType.getAnnotation(Table.class).name();

        Arrays.stream(entityType.getDeclaredFields())
                .map(field -> new MappedColumn(field.getAnnotation(Column.class)))
                .filter(mappedColumn -> mappedColumn.column() != null)
                .filter(
                        mappedColumn -> {
                            String qualifiedName =
                                    tableName + "." + mappedColumn.column().name();
                            return !extensionOnly || AI_ROUTE_EXTENSION_COLUMNS.contains(qualifiedName);
                        })
                .forEach(
                        mappedColumn -> {
                            String qualifiedName =
                                    tableName + "." + mappedColumn.column().name();
                            mappedColumns.add(qualifiedName);
                            if (mappedColumn.column().nullable()) {
                                mappedNullableColumns.add(qualifiedName);
                            }
                        });
    }

    private List<String> 물리_컬럼을_조회한다(boolean nullableOnly) {
        String nullableCondition = nullableOnly ? "AND is_nullable = 'YES'" : "";

        return jdbcTemplate.queryForList(
                """
                SELECT CONCAT(table_name, '.', column_name)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND (
                      table_name IN (
                          'ai_route_prerequisite', 'ai_route_generation',
                          'ai_route_generation_item', 'ai_reading_route',
                          'ai_reading_route_item', 'ai_route_current',
                          'ai_route_daily_usage'
                      )
                      OR CONCAT(table_name, '.', column_name) IN (
                          'book.content_version',
                          'book.ai_route_supported',
                          'book.ai_external_transfer_allowed',
                          'book.ai_data_policy_version',
                          'book_page.ai_analysis_text',
                          'book_page.ai_public_guide_topic',
                          'book_page.estimated_reading_seconds',
                          'book_page.embedding_model',
                          'book_page.embedding_dimensions',
                          'book_page.embedding_json',
                          'book_page.duplicate_group_keys'
                      )
                  )
                %s
                ORDER BY table_name, ordinal_position
                """
                        .formatted(nullableCondition),
                String.class);
    }

    private List<String> 이름을_조회한다(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private AiRouteGeneration 잉크_예산_생성을_시작한다() {
        return 잉크_예산_생성을_시작한다(GENERATION_ID);
    }

    private AiRouteGeneration 잉크_예산_생성을_시작한다(UUID generationId) {
        return AiRouteGeneration.start(
                generationId,
                READER_ID,
                IDEMPOTENCY_KEY,
                "a".repeat(64),
                AiRouteGenerationCommand.forInkBudget(
                        BOOK_ID, "ai-route-v2", "목적", 3, 3),
                CREATED_AT);
    }

    private AiRouteGeneration 소장_깊이_생성을_시작한다() {
        return AiRouteGeneration.start(
                UUID.randomUUID(),
                READER_ID,
                UUID.randomUUID(),
                "a".repeat(64),
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, "ai-route-v2", "목적", AiRouteDepth.QUICK),
                CREATED_AT);
    }

    private record MappedColumn(Column column) {}
}
