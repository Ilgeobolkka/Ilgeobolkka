package com.example.ilgeobolkka.airoute.service.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AiRouteGuideFactoryTest {

    private final AiRouteGuideFactory factory = new AiRouteGuideFactory();

    @Test
    void 역할별_서버_템플릿과_공개_주제만으로_가이드를_만든다() {
        assertEquals(
                "선수 개념을 먼저 살펴보는 페이지입니다. 주제는 \"투자 판단 기준\"입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.PREREQUISITE));
        assertEquals(
                "핵심 개념을 살펴보는 페이지입니다. 주제는 \"투자 판단 기준\"입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.CORE));
        assertEquals(
                "개념이 사례에 적용되는 방식을 살펴보는 페이지입니다. 주제는 \"투자 판단 기준\"입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.EXAMPLE));
        assertEquals(
                "다른 관점과 반론을 살펴보는 페이지입니다. 주제는 \"투자 판단 기준\"입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.COUNTERPOINT));
        assertEquals(
                "앞선 내용을 정리하는 페이지입니다. 주제는 \"투자 판단 기준\"입니다.",
                factory.create("투자 판단 기준", AiRouteItemRole.CONCLUSION));
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
