package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class SchedulingConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

    @Test
    void 일반_기동에서는_스케줄링을_켠다() {
        runner.run(context -> assertTrue(
                context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class).length > 0));
    }

    /** 스케줄러 스레드는 비데몬이라 켜져 있으면 적재를 마친 배치 JVM 이 종료되지 않는다. */
    @Test
    void 콘텐츠_적재_배치에서는_스케줄링을_켜지_않는다() {
        runner.withPropertyValues("spring.profiles.active=content-import")
                .run(context -> assertEquals(
                        0,
                        context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)
                                .length));
    }
}
