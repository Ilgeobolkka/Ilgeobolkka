package com.example.ilgeobolkka.airoute.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRoutePurposeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiRoutePurposeNormalizerTest {

    @Test
    void NFC로_조합한_문자와_분해한_문자가_같은_결과가_된다() {
        String composed = "\uD55C\uAE00"; // "한글" NFC 조합형
        String decomposed = "\u1112\u1161\u11AB\u1100\u1173\u11AF"; // 같은 글자의 NFD 분해형

        assertNotEquals(composed, decomposed, "두 리터럴이 같으면 이 테스트는 아무것도 증명하지 못한다");
        assertEquals(
                AiRoutePurposeNormalizer.normalize(composed),
                AiRoutePurposeNormalizer.normalize(decomposed));
    }

    @Test
    void 여러_종류의_유니코드_공백을_ASCII_공백_하나로_바꾼다() {
        // NBSP(U+00A0)는 Character.isWhitespace 가 false 이고, 나머지도 \s 로는 잡히지 않는다.
        String raw = "독서\u00A0목적\u2003확인\u3000하기\u2009끝";

        assertEquals("독서 목적 확인 하기 끝", AiRoutePurposeNormalizer.normalize(raw));
    }

    @Test
    void 공백_연속_구간과_앞뒤_공백을_정리한다() {
        assertEquals("목적 확인", AiRoutePurposeNormalizer.normalize("  목적\u00A0\t\n  확인   "));
    }

    @Test
    void 정규화_결과를_다시_정규화해도_같다() {
        // command 가 이미 정규화된 값을 다시 넣어도 안전하다는 계약이 여기에 걸려 있다.
        String once = AiRoutePurposeNormalizer.normalize("  독서\u00A0목적  확인 ");

        assertEquals(once, AiRoutePurposeNormalizer.normalize(once));
    }

    @Test
    void 대소문자와_공백_아닌_문자는_바꾸지_않는다() {
        String raw = "Spring Boot 와 JPA-내부 동작 (성능!)";

        assertEquals(raw, AiRoutePurposeNormalizer.normalize(raw));
    }

    @Test
    void 공백이_아닌_앞뒤_제어_문자는_제거하지_않는다() {
        // trim 은 U+0020 이하를 잘라내지만 제어 문자는 White_Space 가 아니므로 남아야 한다.
        // 제거하면 서로 다른 입력이 같은 결과·멱등 지문으로 합쳐진다.
        String raw = "\u0001목적\u0002";

        String normalized = AiRoutePurposeNormalizer.normalize(raw);

        assertEquals(raw, normalized);
        assertNotEquals(AiRoutePurposeNormalizer.normalize("목적"), normalized);
    }

    @Test
    void HTML_모양_입력을_이스케이프하거나_제거하지_않고_그대로_둔다() {
        String raw = "<script>alert('x')</script> 이해하기";

        assertEquals(raw, AiRoutePurposeNormalizer.normalize(raw));
    }

    @Test
    void 정규화_결과가_200_code_point면_허용한다() {
        String raw = "가".repeat(200);

        assertEquals(200, codePointCount(AiRoutePurposeNormalizer.normalize(raw)));
    }

    @Test
    void 정규화_결과가_201_code_point면_거부한다() {
        String raw = "가".repeat(201);

        assertThrows(InvalidAiRoutePurposeException.class, () -> AiRoutePurposeNormalizer.normalize(raw));
    }

    @Test
    void 보조_평면_문자는_UTF16_길이가_아니라_code_point로_센다() {
        // emoji 는 UTF-16 에서 2 char 이므로 length() 기준이면 200 을 넘는다고 잘못 판정한다.
        String raw = "😀".repeat(200);

        String normalized = AiRoutePurposeNormalizer.normalize(raw);

        assertEquals(200, codePointCount(normalized));
        assertEquals(400, normalized.length());
    }

    @Test
    void 보조_평면_문자가_201개면_거부한다() {
        String raw = "😀".repeat(201);

        assertThrows(InvalidAiRoutePurposeException.class, () -> AiRoutePurposeNormalizer.normalize(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u00A0", "\u3000 \u2003"})
    void 빈_입력이거나_공백뿐이면_거부한다(String raw) {
        assertThrows(InvalidAiRoutePurposeException.class, () -> AiRoutePurposeNormalizer.normalize(raw));
    }

    @Test
    void null_입력을_거부한다() {
        assertThrows(InvalidAiRoutePurposeException.class, () -> AiRoutePurposeNormalizer.normalize(null));
    }

    @Test
    void 예외_메시지에_원문을_담지_않는다() {
        String secret = "노출되면 안 되는 원문 " + "가".repeat(200);

        InvalidAiRoutePurposeException exception =
                assertThrows(InvalidAiRoutePurposeException.class, () -> AiRoutePurposeNormalizer.normalize(secret));

        assertFalse(exception.getMessage().contains("노출되면"));
    }

    private int codePointCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
