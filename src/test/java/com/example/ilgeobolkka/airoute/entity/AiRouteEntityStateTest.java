package com.example.ilgeobolkka.airoute.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRouteEntityStateTest {

    private static final long READER_ID = 1L;
    private static final long BOOK_ID = 2L;
    private static final long BOOK_PAGE_ID = 3L;
    private static final UUID GENERATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-10T00:01:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-10T00:16:00Z");

    @Test
    void 생성_완료와_만료_시각은_유효한_순서여야_한다() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> 새_생성().completeRoute(null, EXPIRES_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> 새_생성().completeRoute(COMPLETED_AT, null)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> 새_생성().completeRoute(CREATED_AT.minusNanos(1), EXPIRES_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> 새_생성().completeRoute(COMPLETED_AT, COMPLETED_AT)));
    }

    @Test
    void 실패_코드는_비어_있을_수_없다() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> 새_생성().fail(null, COMPLETED_AT, EXPIRES_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> 새_생성().fail(" ", COMPLETED_AT, EXPIRES_AT)));
    }

    @Test
    void 저장_경로는_한_번만_완료할_수_있다() {
        AiReadingRoute route = 저장_경로(READER_ID, BOOK_ID);
        route.complete(COMPLETED_AT);

        assertThrows(IllegalStateException.class, () -> route.complete(COMPLETED_AT));
    }

    @Test
    void 저장_경로의_잉크_예산은_음수일_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AiReadingRoute.createWithInkBudget(
                                GENERATION_ID,
                                READER_ID,
                                BOOK_ID,
                                "ai-route-v2",
                                "목적",
                                -1,
                                CREATED_AT));
    }

    @Test
    void 경로_항목의_순서는_양수여야_한다() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiRouteGenerationItem.create(
                                                GENERATION_ID,
                                                BOOK_ID,
                                                BOOK_PAGE_ID,
                                                0,
                                                AiRouteItemRelevance.HIGH,
                                                false,
                                                AiRouteItemRole.CORE)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiReadingRouteItem.create(
                                                1L,
                                                BOOK_ID,
                                                BOOK_PAGE_ID,
                                                -1,
                                                AiRouteItemRelevance.HIGH,
                                                false,
                                                AiRouteItemRole.CORE)));
    }

    @Test
    void 최초_열람_시각은_필수이다() {
        AiReadingRouteItem item =
                AiReadingRouteItem.create(
                        1L,
                        BOOK_ID,
                        BOOK_PAGE_ID,
                        1,
                        AiRouteItemRelevance.HIGH,
                        false,
                        AiRouteItemRole.CORE);

        assertThrows(IllegalArgumentException.class, () -> item.markOpened(null));
    }

    @Test
    void 현재_경로는_같은_독자와_도서의_저장_경로만_선택한다() {
        AiReadingRoute selectedRoute = 저장_경로(READER_ID, BOOK_ID);
        AiReadingRoute otherReaderRoute = 저장_경로(READER_ID + 1, BOOK_ID);
        AiReadingRoute otherBookRoute = 저장_경로(READER_ID, BOOK_ID + 1);

        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiRouteCurrent.select(
                                                READER_ID + 1,
                                                BOOK_ID,
                                                selectedRoute,
                                                CREATED_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiRouteCurrent.select(
                                                READER_ID,
                                                BOOK_ID + 1,
                                                selectedRoute,
                                                CREATED_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AiRouteCurrent.select(
                                                READER_ID, BOOK_ID, null, CREATED_AT)));

        AiRouteCurrent current =
                AiRouteCurrent.select(READER_ID, BOOK_ID, selectedRoute, CREATED_AT);

        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> current.changeRoute(otherReaderRoute, COMPLETED_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> current.changeRoute(otherBookRoute, COMPLETED_AT)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> current.changeRoute(null, COMPLETED_AT)),
                () -> assertSame(selectedRoute, current.getRoute()));
    }

    private AiRouteGeneration 새_생성() {
        return AiRouteGeneration.start(
                GENERATION_ID,
                READER_ID,
                IDEMPOTENCY_KEY,
                "a".repeat(64),
                AiRouteGenerationCommand.forInkBudget(
                        BOOK_ID, "ai-route-v2", "목적", 3, 3),
                CREATED_AT);
    }

    private AiReadingRoute 저장_경로(long readerId, long bookId) {
        return AiReadingRoute.createWithInkBudget(
                UUID.randomUUID(),
                readerId,
                bookId,
                "ai-route-v2",
                "목적",
                3,
                CREATED_AT);
    }
}
