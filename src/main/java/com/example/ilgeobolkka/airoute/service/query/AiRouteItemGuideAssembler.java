package com.example.ilgeobolkka.airoute.service.query;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;

/**
 * 경로 항목의 AI 페이지 가이드와 예상 독서 시간을 만든다.
 *
 * <p>둘 다 저장 컬럼이 없다. 가이드는 사람이 미리 검수한 페이지별 공개 가이드 주제와 경로 역할을 서버의 고정
 * 템플릿에 넣어 만들고, 예상 시간은 적재 때 계산해 둔 {@code book_page.estimated_reading_seconds}를 분으로
 * 바꾼다.
 *
 * <p>외부 모델이 만든 자유 문구와 비공개 분석 텍스트, 페이지 본문은 문구의 입력으로 쓰지 않는다. 여기서
 * 조립할 수 있는 값은 검수된 공개 주제와 역할 두 가지뿐이며, 그래서 이 클래스는 입력을 더 받지 않는다.
 *
 * <p>의존이 없는 순수 함수라 {@link com.example.ilgeobolkka.airoute.service.AiRoutePurposeNormalizer}와
 * 같이 static 으로 둔다. 문구 규칙은 주입으로 바꿔 끼울 대상이 아니다.
 */
public final class AiRouteItemGuideAssembler {

    /** 페이지별 예상 시간의 하한. 정본이 정한 값이라 0분이나 반올림 결과가 0이 되는 경우를 허용하지 않는다. */
    static final int MINIMUM_ESTIMATED_MINUTES = 1;

    private static final int SECONDS_PER_MINUTE = 60;

    private AiRouteItemGuideAssembler() {}

    /**
     * 역할과 공개 가이드 주제로 한 문장 가이드를 만든다.
     *
     * <p>주제가 없는 페이지는 역할만으로 문장을 만든다. 적재 단계에서 주제를 넣지 않은 페이지가 경로에 들어올
     * 수 있는데, 여기서 예외를 던지면 이미 저장된 경로의 상세 조회가 통째로 실패한다.
     */
    public static String guide(AiRouteItemRole role, String publicGuideTopic) {
        String clause = roleClause(role);
        String topic = normalizeTopic(publicGuideTopic);

        return topic == null
                ? "%s 다루는 페이지입니다.".formatted(clause)
                : "%s에 관한 %s 다루는 페이지입니다.".formatted(topic, clause);
    }

    /**
     * 예상 독서 초를 화면에 쓰는 분으로 바꾼다.
     *
     * <p>내림하면 59초짜리 페이지가 0분이 되므로 올림한 뒤 하한을 적용한다. 값이 없거나 0 이하인 페이지도
     * 하한으로 본다.
     */
    public static int estimatedMinutes(Integer estimatedReadingSeconds) {
        if (estimatedReadingSeconds == null || estimatedReadingSeconds <= 0) {
            return MINIMUM_ESTIMATED_MINUTES;
        }

        return Math.max(
                MINIMUM_ESTIMATED_MINUTES,
                Math.ceilDiv(estimatedReadingSeconds, SECONDS_PER_MINUTE));
    }

    /**
     * 역할을 목적어 자리에 넣을 어구로 바꾼다.
     *
     * <p>조사를 어구에 붙여 둔다. 역할마다 받침이 달라 {@code 을}·{@code 를}가 갈리는데, 이것만을 위해 한글
     * 종성 판별을 들이는 것보다 다섯 개를 그대로 적는 편이 읽기 쉽다.
     */
    private static String roleClause(AiRouteItemRole role) {
        return switch (role) {
            case PREREQUISITE -> "선수 개념을";
            case CORE -> "핵심 개념을";
            case EXAMPLE -> "사례를";
            case COUNTERPOINT -> "반론을";
            case CONCLUSION -> "결론을";
        };
    }

    private static String normalizeTopic(String publicGuideTopic) {
        if (publicGuideTopic == null) {
            return null;
        }

        String trimmed = publicGuideTopic.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
