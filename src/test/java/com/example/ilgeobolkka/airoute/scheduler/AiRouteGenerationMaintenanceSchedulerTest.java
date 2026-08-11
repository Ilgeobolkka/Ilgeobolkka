package com.example.ilgeobolkka.airoute.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 조건부 등록만 확인한다. 정리·복구 자체는 {@code AiRouteGenerationLifecycleMySqlIntegrationTest} 가
 * 덮으므로 여기서 다시 보지 않는다.
 *
 * <p>이 테스트가 막는 것은 조용한 실패 하나다. {@code @ConditionalOnProperty} 의 prefix·name 이 어긋나면
 * 빈이 등록되지 않고 아무 오류도 나지 않는다. {@code @EnableScheduling} 을 같은 조건부 클래스에 얹어
 * 두었으므로, 그때는 스케줄링 자체가 꺼진 채로 서버가 정상 기동하고 정리·복구가 영원히 돌지 않는다.
 *
 * <p>실제로 주기마다 뜨는지는 보지 않는다. 시간에 기대는 확인이라 느리고 불안정한 데 비해, 여기서 얻는
 * 것은 이미 프레임워크가 보장하는 부분이다.
 */
class AiRouteGenerationMaintenanceSchedulerTest {

    @Test
    void AI_경로를_켜면_유지보수_스케줄러를_등록한다() {
        schedulerContextRunner()
                .withPropertyValues("ai-route.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .hasSingleBean(AiRouteGenerationMaintenanceScheduler.class);
                        });
    }

    @Test
    void AI_경로를_끄면_유지보수_스케줄러를_등록하지_않는다() {
        schedulerContextRunner()
                .withPropertyValues("ai-route.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .doesNotHaveBean(AiRouteGenerationMaintenanceScheduler.class);
                        });
    }

    /** 속성을 아예 주지 않는 경우다. 기본값이 거짓이라 등록되지 않아야 한다. */
    @Test
    void 설정이_없으면_유지보수_스케줄러를_등록하지_않는다() {
        schedulerContextRunner()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .doesNotHaveBean(AiRouteGenerationMaintenanceScheduler.class);
                        });
    }

    private ApplicationContextRunner schedulerContextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(SchedulerTestConfiguration.class);
    }

    /**
     * 스케줄러를 {@code @Bean} 으로 직접 만들지 않고 {@link Import} 로 올린다. 직접 만들면 클래스에 붙은
     * {@code @ConditionalOnProperty} 가 평가되지 않아 이 테스트가 아무것도 확인하지 못한다.
     */
    @Configuration(proxyBeanMethods = false)
    @Import(AiRouteGenerationMaintenanceScheduler.class)
    static class SchedulerTestConfiguration {

        @Bean
        AiRouteGenerationCleanupService cleanupService() {
            return mock(AiRouteGenerationCleanupService.class);
        }
    }
}
