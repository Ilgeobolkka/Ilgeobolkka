package com.example.ilgeobolkka.airoute.service;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRoutePurposeException;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 독서 목적 입력을 저장·외부 요청에 쓰는 정규화 형태로 바꾼다.
 *
 * <p>대소문자와 공백이 아닌 문자는 바꾸지 않으며, 원문은 반환값·예외 메시지 어디에도 남기지 않는다.
 * 정규화 결과는 목적 데이터일 뿐 외부 모델에 대한 지시문이 아니므로 이 클래스는 문구를 덧붙이지 않는다.
 *
 * <p>의존이 없는 순수 함수라 static 으로 둔다. 생성 입력의 canonical 경계인
 * {@link com.example.ilgeobolkka.airoute.AiRouteGenerationCommand}가 Spring 컨테이너 없이 이 규칙을
 * 적용해야 하고, 규칙을 주입으로 바꿔 끼울 수 있으면 경계가 경계가 아니게 된다.
 */
public final class AiRoutePurposeNormalizer {

    public static final int MAX_CODE_POINTS = 200;

    /**
     * Unicode {@code White_Space} 속성 문자의 연속 구간.
     *
     * <p>{@code \s}는 ASCII 6종만, {@code Character#isWhitespace}는 NBSP(U+00A0)를 공백으로 보지 않는다.
     * 정책이 요구하는 것은 Unicode 속성이므로 {@code \p{IsWhite_Space}}를 쓴다.
     */
    private static final Pattern WHITE_SPACE_RUN = Pattern.compile("\\p{IsWhite_Space}+");

    /**
     * 앞뒤에 남은 ASCII 공백. 축약이 끝난 뒤라 각 끝에 최대 한 개만 있을 수 있다.
     *
     * <p>{@code String#trim}은 U+0020 이하를 모두 잘라내 U+0001 같은 제어 문자까지 없앤다. 제어 문자는
     * Unicode {@code White_Space}가 아니므로 "공백이 아닌 문자는 바꾸지 않는다"는 계약을 어기고, 서로 다른
     * 입력이 같은 정규화 결과·멱등 지문으로 합쳐진다. 그래서 U+0020만 제거한다.
     */
    private static final Pattern EDGE_ASCII_SPACE = Pattern.compile("\\A | \\z");

    private static final String ASCII_SPACE = " ";

    private AiRoutePurposeNormalizer() {}

    /**
     * NFC 정규화 → Unicode 공백 연속 구간을 ASCII 공백 하나로 축약 → 앞뒤 ASCII 공백 제거 순으로 처리하고
     * 결과에 내용이 남았는지, code point 수가 한도 안인지 검사한다. 이미 정규화된 값을 다시 넣어도 결과가
     * 같다(멱등).
     *
     * @throws InvalidAiRoutePurposeException 남는 내용이 없거나 {@value #MAX_CODE_POINTS} code point를
     *     넘을 때
     */
    public static String normalize(String rawPurpose) {
        if (rawPurpose == null) {
            throw new InvalidAiRoutePurposeException("값이 없습니다.");
        }

        String composed = Normalizer.normalize(rawPurpose, Normalizer.Form.NFC);
        String collapsed = WHITE_SPACE_RUN.matcher(composed).replaceAll(ASCII_SPACE);
        String normalized = EDGE_ASCII_SPACE.matcher(collapsed).replaceAll("");

        if (!hasContent(normalized)) {
            throw new InvalidAiRoutePurposeException("공백과 보이지 않는 문자를 제외한 내용이 필요합니다.");
        }
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount > MAX_CODE_POINTS) {
            throw new InvalidAiRoutePurposeException(
                    "최대 " + MAX_CODE_POINTS + "자까지 입력할 수 있습니다. 현재 " + codePointCount + "자입니다.");
        }
        return normalized;
    }

    /**
     * 눈에 보이는 내용이 한 code point라도 있는지.
     *
     * <p>공백만으로 된 목적을 거부하는 것과 같은 이유로, 제어 문자(Cc)·서식 문자(Cf)만으로 된 목적도
     * 거부한다. 그대로 두면 ZWSP 하나짜리 목적이 지문과 외부 요청까지 간다. 내용이 있으면 그 문자들은
     * 지우지 않고 보존한다 — 서로 다른 입력이 같은 지문으로 합쳐지면 안 되기 때문이다.
     */
    private static boolean hasContent(String normalized) {
        return normalized.codePoints().anyMatch(AiRoutePurposeNormalizer::isContent);
    }

    private static boolean isContent(int codePoint) {
        if (Character.isSpaceChar(codePoint)) {
            return false;
        }
        int type = Character.getType(codePoint);
        return type != Character.CONTROL && type != Character.FORMAT;
    }
}
