package com.example.ilgeobolkka.airoute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiRouteGenerationCommandTest {

    private static final String PURPOSE = "트랜잭션 격리 수준 이해하기";
    private static final long BOOK_ID = 42L;
    private static final String CONTENT_VERSION = "initial-v1";

    @Test
    void 비소장_입력은_예산만_가지고_깊이는_비어_있다() {
        AiRouteGenerationCommand command =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);

        assertEquals(AiRouteRequestType.INK_BUDGET, command.requestType());
        assertEquals(5, command.maxAdditionalInk());
        assertNull(command.depth());
    }

    @ParameterizedTest
    @EnumSource(AiRouteDepth.class)
    void 소장_입력은_깊이_세_값만_가지고_예산은_비어_있다(AiRouteDepth depth) {
        AiRouteGenerationCommand command =
                AiRouteGenerationCommand.forOwnedDepth(BOOK_ID, CONTENT_VERSION, PURPOSE, depth);

        assertEquals(AiRouteRequestType.OWNED_DEPTH, command.requestType());
        assertEquals(depth, command.depth());
        assertNull(command.maxAdditionalInk());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 100})
    void 예산이_0부터_잔액까지면_허용한다(int budget) {
        AiRouteGenerationCommand command =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, budget, 100);

        assertEquals(budget, command.maxAdditionalInk());
    }

    @Test
    void 예산이_잔액보다_1_크면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 101, 100));
    }

    @Test
    void 음수_예산을_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, -1, 100));
    }

    @Test
    void 예산과_깊이를_함께_주면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () ->
                        new AiRouteGenerationCommand(
                                BOOK_ID,
                                CONTENT_VERSION,
                                PURPOSE,
                                AiRouteRequestType.INK_BUDGET,
                                5,
                                AiRouteDepth.QUICK));
    }

    @Test
    void 예산_입력인데_예산이_없으면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () ->
                        new AiRouteGenerationCommand(
                                BOOK_ID, CONTENT_VERSION, PURPOSE, AiRouteRequestType.INK_BUDGET, null, null));
    }

    @Test
    void 깊이_입력인데_깊이가_없으면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () ->
                        new AiRouteGenerationCommand(
                                BOOK_ID, CONTENT_VERSION, PURPOSE, AiRouteRequestType.OWNED_DEPTH, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void 정규화한_목적이_비어_있거나_공백뿐이면_거부한다(String purpose) {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, purpose, 5, 100));
    }

    @Test
    void 콘텐츠_버전이_없으면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, " ", PURPOSE, 5, 100));
    }

    @Test
    void 비소장_예산_기본값은_잔액과_10_중_작은_값이다() {
        assertEquals(10, AiRouteGenerationCommand.defaultInkBudget(100));
        assertEquals(10, AiRouteGenerationCommand.defaultInkBudget(10));
        assertEquals(3, AiRouteGenerationCommand.defaultInkBudget(3));
        assertEquals(0, AiRouteGenerationCommand.defaultInkBudget(0));
    }

    @Test
    void 음수_잔액으로_기본_예산을_계산하면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.defaultInkBudget(-1));
    }

    @Test
    void 깊이는_세_값만_존재해_그_밖의_값을_받을_수_없다() {
        assertEquals(3, AiRouteDepth.values().length);
        assertThrows(IllegalArgumentException.class, () -> AiRouteDepth.valueOf("UNKNOWN"));
    }

    @Test
    void 같은_입력은_같은_command가_되어_지문_계산에_쓸_수_있다() {
        AiRouteGenerationCommand first =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);
        AiRouteGenerationCommand second =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
