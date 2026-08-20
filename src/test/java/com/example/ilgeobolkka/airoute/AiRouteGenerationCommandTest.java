package com.example.ilgeobolkka.airoute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRoutePurposeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiRouteGenerationCommandTest {

    private static final String PURPOSE = "트랜잭션 격리 수준 이해하기";
    private static final long BOOK_ID = 42L;
    private static final String CONTENT_VERSION = "initial-v1";
    /** "한글" NFC 조합형. */
    private static final String HANGUL_NFC = "\uD55C\uAE00";
    /** "한글" NFD 자모 분해형. 바이트 열이 NFC 와 완전히 다르다. */
    private static final String HANGUL_NFD = "\u1112\u1161\u11AB\u1100\u1173\u11AF";

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

    // --- canonical 경계 ---------------------------------------------------

    @Test
    void 같은_의미의_NFC_NFD_공백_변형은_같은_command가_된다() {
        // NFD 자모 + NBSP(U+00A0) + 전각 공백(U+3000) + 앞뒤 공백
        String variant = "  " + HANGUL_NFD + "\u00A0\u3000목적  ";

        AiRouteGenerationCommand canonical =
                AiRouteGenerationCommand.forInkBudget(
                        BOOK_ID, CONTENT_VERSION, HANGUL_NFC + " 목적", 5, 100);
        AiRouteGenerationCommand fromVariant =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, variant, 5, 100);

        assertEquals(HANGUL_NFC + " 목적", fromVariant.normalizedPurpose());
        assertEquals(canonical, fromVariant);
        assertEquals(canonical.hashCode(), fromVariant.hashCode());
    }

    @Test
    void 소장_입력도_같은_의미의_목적이면_같은_command가_된다() {
        AiRouteGenerationCommand canonical =
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, CONTENT_VERSION, HANGUL_NFC + " 목적", AiRouteDepth.QUICK);
        AiRouteGenerationCommand fromVariant =
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, CONTENT_VERSION, " " + HANGUL_NFD + "\t목적 ", AiRouteDepth.QUICK);

        assertEquals(canonical, fromVariant);
        assertEquals(canonical.hashCode(), fromVariant.hashCode());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   ", "\u200B"})
    void 목적_검증은_정규화기에_위임한다(String rawPurpose) {
        // 200/201 code point·공백 종류별 경계는 AiRoutePurposeNormalizerTest 가 전수로 덮는다.
        // 여기서 증명할 것은 command 가 그 정규화기를 실제로 거친다는 사실 하나뿐이다.
        assertThrows(
                InvalidAiRoutePurposeException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, rawPurpose, 5, 100));
    }

    // --- 예산·깊이 규칙 ----------------------------------------------------

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
    void 잔액이_0이면_어떤_양수_예산도_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 1, 0));
    }

    @Test
    void 음수_예산을_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, -1, 100));
    }

    @Test
    void 소장_입력에_깊이가_없으면_거부한다() {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forOwnedDepth(BOOK_ID, CONTENT_VERSION, PURPOSE, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 콘텐츠_버전이_없으면_거부한다(String contentVersion) {
        assertThrows(
                InvalidAiRouteGenerationInputException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, contentVersion, PURPOSE, 5, 100));
    }

    @Test
    void 비소장_예산_기본값은_잔액과_10_중_작은_값이다() {
        assertEquals(10, AiRouteGenerationCommand.defaultInkBudget(100));
        assertEquals(10, AiRouteGenerationCommand.defaultInkBudget(10));
        assertEquals(3, AiRouteGenerationCommand.defaultInkBudget(3));
        assertEquals(0, AiRouteGenerationCommand.defaultInkBudget(0));
    }

    @Test
    void 기본_예산_command는_계산값이_같은_명시_예산과_출처를_구분한다() {
        AiRouteGenerationCommand defaultBudget = AiRouteGenerationCommand.forDefaultInkBudget(
                BOOK_ID, CONTENT_VERSION, PURPOSE, 10);
        AiRouteGenerationCommand explicitBudget =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 10, 10);

        assertEquals(10, defaultBudget.maxAdditionalInk());
        assertTrue(defaultBudget.defaultInkBudget());
        assertFalse(explicitBudget.defaultInkBudget());
        assertNotEquals(explicitBudget, defaultBudget);
    }

    @Test
    void 음수_잔액은_입력_오류가_아니라_불변식_위반으로_던진다() {
        // DB CHECK 제약으로 잔액은 0 이상이다. 입력 예외로 던지면 G08 이 서버 버그를 400 으로 내보낸다.
        assertThrows(IllegalStateException.class, () -> AiRouteGenerationCommand.defaultInkBudget(-1));
    }

    @Test
    void 음수_잔액은_기본값_계산과_command_생성_어느_쪽에서도_같게_판정한다() {
        // 한쪽만 막으면 같은 음수 잔액이 경로에 따라 불변식 위반과 "예산 > 잔액" 입력 오류로 갈린다.
        assertThrows(
                IllegalStateException.class,
                () -> AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 0, -1));
    }

    @Test
    void 필드가_하나라도_다르면_다른_command다() {
        // 지문 입력이 될 타입이라 equals 에서 필드 하나를 빠뜨리면 서로 다른 요청이 같은 요청으로 합쳐진다.
        AiRouteGenerationCommand base =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 5, 100);

        assertNotEquals(
                base,
                AiRouteGenerationCommand.forInkBudget(BOOK_ID + 1, CONTENT_VERSION, PURPOSE, 5, 100),
                "bookId");
        assertNotEquals(
                base,
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, "other-v2", PURPOSE, 5, 100),
                "contentVersion");
        assertNotEquals(
                base,
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, "다른 목적", 5, 100),
                "normalizedPurpose");
        assertNotEquals(
                base,
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 6, 100),
                "maxAdditionalInk");
        assertNotEquals(
                base,
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, AiRouteDepth.QUICK),
                "requestType");
    }

    @Test
    void 깊이만_다르면_다른_command다() {
        AiRouteGenerationCommand quick =
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, AiRouteDepth.QUICK);
        AiRouteGenerationCommand deep =
                AiRouteGenerationCommand.forOwnedDepth(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, AiRouteDepth.DEEP);

        assertNotEquals(quick, deep);
    }

    @Test
    void 목적을_toString에_남기지_않는다() {
        AiRouteGenerationCommand command =
                AiRouteGenerationCommand.forInkBudget(BOOK_ID, CONTENT_VERSION, "노출되면 안 되는 목적", 5, 100);

        assertFalse(command.toString().contains("노출되면"));
    }
}
