package com.example.ilgeobolkka.airoute.service.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiRouteItemGuideAssemblerTest {

    @ParameterizedTest
    @CsvSource({
        "PREREQUISITE, 선수 개념을",
        "CORE, 핵심 개념을",
        "EXAMPLE, 사례를",
        "COUNTERPOINT, 반론을",
        "CONCLUSION, 결론을"
    })
    void 가이드는_공개_주제와_역할을_고정_템플릿에_넣는다(AiRouteItemRole role, String clause) {
        String guide = AiRouteItemGuideAssembler.guide(role, "투자 판단 기준");

        assertThat(guide).isEqualTo("투자 판단 기준에 관한 %s 다루는 페이지입니다.".formatted(clause));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void 공개_주제가_없으면_역할만으로_문장을_만든다(String publicGuideTopic) {
        String guide = AiRouteItemGuideAssembler.guide(AiRouteItemRole.CORE, publicGuideTopic);

        assertThat(guide).isEqualTo("핵심 개념을 다루는 페이지입니다.");
    }

    @Test
    void 공개_주제의_앞뒤_공백은_문장에_남기지_않는다() {
        String guide = AiRouteItemGuideAssembler.guide(AiRouteItemRole.EXAMPLE, "  투자 판단 기준  ");

        assertThat(guide).isEqualTo("투자 판단 기준에 관한 사례를 다루는 페이지입니다.");
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "59, 1", "60, 1", "61, 2", "119, 2", "120, 2", "1440, 24"})
    void 예상_시간은_올림한_분이다(int seconds, int expectedMinutes) {
        assertThat(AiRouteItemGuideAssembler.estimatedMinutes(seconds)).isEqualTo(expectedMinutes);
    }

    @Test
    void 예상_초가_없거나_0_이하면_최소_1분이다() {
        assertThat(AiRouteItemGuideAssembler.estimatedMinutes(null)).isEqualTo(1);
        assertThat(AiRouteItemGuideAssembler.estimatedMinutes(0)).isEqualTo(1);
        assertThat(AiRouteItemGuideAssembler.estimatedMinutes(-30)).isEqualTo(1);
    }
}
