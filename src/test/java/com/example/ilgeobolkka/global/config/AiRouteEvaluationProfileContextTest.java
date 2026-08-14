package com.example.ilgeobolkka.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ilgeobolkka.airoute.scheduler.AiRouteGenerationMaintenanceScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

class AiRouteEvaluationProfileContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=evaluation")
            .withUserConfiguration(
                    SecurityConfig.class,
                    SchedulingConfig.class,
                    AiRouteGenerationMaintenanceScheduler.class);

    @Test
    void evaluation은_SecurityFilterChain과_전역_scheduler를_등록하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
                    .isEqualTo("none");
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).doesNotHaveBean(SchedulingConfig.class);
            assertThat(context).doesNotHaveBean(AiRouteGenerationMaintenanceScheduler.class);
            assertThat(context).doesNotHaveBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor");
        });
    }
}
