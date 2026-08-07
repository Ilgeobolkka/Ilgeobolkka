package com.example.ilgeobolkka.airoute.service;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRoutePurposeException;
import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 독서 목적 입력을 저장·외부 요청에 쓰는 정규화 형태로 바꾼다.
 *
 * <p>대소문자와 공백이 아닌 문자는 바꾸지 않으며, 원문은 반환값·예외 메시지 어디에도 남기지 않는다.
 * 정규화 결과는 목적 데이터일 뿐 외부 모델에 대한 지시문이 아니므로 이 클래스는 문구를 덧붙이지 않는다.
 */
@Service
public class AiRoutePurposeNormalizer {

    public static final int MIN_CODE_POINTS = 1;
    public static final int MAX_CODE_POINTS = 200;

    /**
     * Unicode {@code White_Space} 속성 문자의 연속 구간.
     *
     * <p>{@code \s}는 ASCII 6종만, {@code Character#isWhitespace}는 NBSP(U+00A0)를 공백으로 보지 않는다.
     * 정책이 요구하는 것은 Unicode 속성이므로 {@code \p{IsWhite_Space}}를 쓴다.
     */
    private static final Pattern WHITE_SPACE_RUN = Pattern.compile("\\p{IsWhite_Space}+");

    /**
     * 앞뒤에 남은 ASCII 공백.
     *
     * <p>{@code String#trim}은 U+0020 이하를 모두 잘라내 U+0001 같은 제어 문자까지 없앤다. 제어 문자는
     * Unicode {@code White_Space}가 아니므로 "공백이 아닌 문자는 바꾸지 않는다"는 계약을 어기고, 서로 다른
     * 입력이 같은 정규화 결과·멱등 지문으로 합쳐진다. 그래서 U+0020만 제거한다.
     */
    private static final Pattern EDGE_ASCII_SPACE = Pattern.compile("\\A +| +\\z");

    private static final String ASCII_SPACE = " ";

    /**
     * NFC 정규화 → Unicode 공백 연속 구간을 ASCII 공백 하나로 축약 → 앞뒤 ASCII 공백 제거 순으로 처리하고
     * 결과를 code point 개수로 검사한다.
     *
     * @throws InvalidAiRoutePurposeException 결과가 비었거나 {@value #MAX_CODE_POINTS} code point를 넘을 때
     */
    public String normalize(String rawPurpose) {
        if (rawPurpose == null) {
            throw new InvalidAiRoutePurposeException("값이 없습니다.");
        }

        String composed = Normalizer.normalize(rawPurpose, Normalizer.Form.NFC);
        String collapsed = WHITE_SPACE_RUN.matcher(composed).replaceAll(ASCII_SPACE);
        String normalized = EDGE_ASCII_SPACE.matcher(collapsed).replaceAll("");

        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount < MIN_CODE_POINTS) {
            throw new InvalidAiRoutePurposeException("공백을 제외한 내용이 필요합니다.");
        }
        if (codePointCount > MAX_CODE_POINTS) {
            throw new InvalidAiRoutePurposeException(
                    "최대 " + MAX_CODE_POINTS + "자까지 입력할 수 있습니다. 현재 " + codePointCount + "자입니다.");
        }
        return normalized;
    }
}
