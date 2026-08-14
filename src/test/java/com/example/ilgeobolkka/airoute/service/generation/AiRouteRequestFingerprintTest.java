package com.example.ilgeobolkka.airoute.service.generation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.airoute.AiRouteDepth;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

class AiRouteRequestFingerprintTest {

    private static final long BOOK_ID = 42L;
    private static final String CONTENT_VERSION = "ai-route-v2";
    private static final String PURPOSE = "핵심 개념만 빠르게";
    private static final int INK_BALANCE = 100;

    /**
     * 인코딩이 조용히 바뀌면 이미 저장된 {@code request_fingerprint} 와 재계산 값이 어긋나 정상 재시도가
     * 전부 {@code AI_ROUTE_IDEMPOTENCY_KEY_REUSED} 로 튄다. DB에 남는 값이라 한 건은 고정해 둔다.
     */
    private static final String GOLDEN_FINGERPRINT =
            "7c0a5324423f6fb651fbfdc230144a78127c9ab06629515884e7fb25c8fca40d";

    @Test
    void 같은_입력은_항상_같은_소문자_hex_64자가_된다() {
        String first = AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 5));
        String second = AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 5));

        assertAll(
                () -> assertEquals(first, second),
                () -> assertTrue(first.matches("[0-9a-f]{64}"), first),
                () -> assertEquals(GOLDEN_FINGERPRINT, first));
    }

    // --- 지문 입력이 실제로 반영되는가 -------------------------------------

    @Test
    void 도서가_다르면_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget(BOOK_ID, CONTENT_VERSION, PURPOSE, 5)),
                AiRouteRequestFingerprint.of(inkBudget(BOOK_ID + 1, CONTENT_VERSION, PURPOSE, 5)));
    }

    @Test
    void 콘텐츠_버전이_다르면_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget(BOOK_ID, "ai-route-v2", PURPOSE, 5)),
                AiRouteRequestFingerprint.of(inkBudget(BOOK_ID, "ai-route-v3", PURPOSE, 5)));
    }

    @Test
    void 목적이_다르면_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget("핵심 개념만 빠르게", 5)),
                AiRouteRequestFingerprint.of(inkBudget("핵심 개념만 천천히", 5)));
    }

    @Test
    void 예산이_다르면_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 5)),
                AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 6)));
    }

    @Test
    void 계산된_예산이_같아도_기본_예산과_명시_예산의_지문은_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 10)),
                AiRouteRequestFingerprint.of(AiRouteGenerationCommand.forDefaultInkBudget(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, 10)));
    }

    @Test
    void 기본_예산은_계산할_때의_잔액이_달라도_같은_지문이다() {
        assertEquals(
                AiRouteRequestFingerprint.of(AiRouteGenerationCommand.forDefaultInkBudget(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, 10)),
                AiRouteRequestFingerprint.of(AiRouteGenerationCommand.forDefaultInkBudget(
                        BOOK_ID, CONTENT_VERSION, PURPOSE, 3)));
    }

    @Test
    void 깊이가_다르면_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(ownedDepth(AiRouteDepth.QUICK)),
                AiRouteRequestFingerprint.of(ownedDepth(AiRouteDepth.DEEP)));
    }

    @Test
    void 예산_요청과_깊이_요청은_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 0)),
                AiRouteRequestFingerprint.of(ownedDepth(AiRouteDepth.QUICK)));
    }

    /**
     * 필드를 구분자로 이어 붙이면 두 입력의 바이트열이 {@code "ab"+"cd" == "a"+"bcd"} 로 같아진다. 길이
     * 접두를 지우면 이 테스트만 깨진다.
     */
    @Test
    void 필드_경계가_옮겨간_입력은_지문이_다르다() {
        assertNotEquals(
                AiRouteRequestFingerprint.of(inkBudget(BOOK_ID, "ab", "cd", 5)),
                AiRouteRequestFingerprint.of(inkBudget(BOOK_ID, "a", "bcd", 5)));
    }

    // --- 원문에 의존하지 않는가 --------------------------------------------

    @Test
    void 공백만_다른_원문은_같은_지문이_된다() {
        assertEquals(
                AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 5)),
                AiRouteRequestFingerprint.of(inkBudget("  핵심   개념만\t빠르게 ", 5)));
    }

    @Test
    void 결합형_원문은_완성형과_같은_지문이_된다() {
        String decomposed = Normalizer.normalize(PURPOSE, Normalizer.Form.NFD);

        assertAll(
                () -> assertNotEquals(PURPOSE, decomposed),
                () ->
                        assertEquals(
                                AiRouteRequestFingerprint.of(inkBudget(PURPOSE, 5)),
                                AiRouteRequestFingerprint.of(inkBudget(decomposed, 5))));
    }

    @Test
    void 명령이_없으면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> AiRouteRequestFingerprint.of(null));
    }

    // --- fixture -----------------------------------------------------------

    private static AiRouteGenerationCommand inkBudget(String rawPurpose, int maxAdditionalInk) {
        return inkBudget(BOOK_ID, CONTENT_VERSION, rawPurpose, maxAdditionalInk);
    }

    private static AiRouteGenerationCommand inkBudget(
            long bookId, String contentVersion, String rawPurpose, int maxAdditionalInk) {
        return AiRouteGenerationCommand.forInkBudget(
                bookId, contentVersion, rawPurpose, maxAdditionalInk, INK_BALANCE);
    }

    private static AiRouteGenerationCommand ownedDepth(AiRouteDepth depth) {
        return AiRouteGenerationCommand.forOwnedDepth(BOOK_ID, CONTENT_VERSION, PURPOSE, depth);
    }
}
