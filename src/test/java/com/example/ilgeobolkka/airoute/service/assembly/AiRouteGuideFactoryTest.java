package com.example.ilgeobolkka.airoute.service.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.service.query.AiRouteItemGuideAssembler;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AiRouteGuideFactoryTest {

    private final AiRouteGuideFactory factory = new AiRouteGuideFactory();

    @Test
    void 역할별_서버_템플릿과_공개_주제만으로_가이드를_만든다() {
        assertEquals(
                "투자 판단 기준에 관한 선수 개념을 다루는 페이지입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.PREREQUISITE));
        assertEquals(
                "투자 판단 기준에 관한 핵심 개념을 다루는 페이지입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.CORE));
        assertEquals(
                "투자 판단 기준에 관한 사례를 다루는 페이지입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.EXAMPLE));
        assertEquals(
                "투자 판단 기준에 관한 반론을 다루는 페이지입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.COUNTERPOINT));
        assertEquals(
                "투자 판단 기준에 관한 결론을 다루는 페이지입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.CONCLUSION));
    }

    @Test
    void 생성과_저장_경로_조회는_같은_가이드_정본을_사용한다() {
        assertEquals(
                AiRouteItemGuideAssembler.guide(
                        AiRouteItemRole.EXAMPLE, "  투자 판단 기준  "),
                factory.create("  투자 판단 기준  ", AiRouteItemRole.EXAMPLE));
    }

    @Test
    void 공개_메서드는_분석_텍스트나_모델_문구를_입력받지_않는다() {
        Method create = Arrays.stream(AiRouteGuideFactory.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("create"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                Arrays.asList(String.class, AiRouteItemRole.class),
                Arrays.asList(create.getParameterTypes()));
    }

    @Test
    void 공개_주제나_역할이_없으면_가이드를_만들지_않는다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(" ", AiRouteItemRole.CORE));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create("투자 판단 기준", null));
    }
}
