package com.example.ilgeobolkka.airoute.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationCleanupService;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationCleanupService.BatchOutcome;
import com.example.ilgeobolkka.global.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/**
 * 유지보수 배치가 기능 플래그와 무관하게 등록되는지 확인한다.
 *
 * <p>이 테스트가 막는 것은 조용한 데이터 잔류다. 기능을 켠 채 임시 결과를 만들어 두고 나중에 끄면,
 * 배치가 등록되지 않는 순간부터 임시 목적·페이지 결과·멱등 상태와 중단된 {@code GENERATING} 이 영구히
 * 남는다. 15분 뒤 삭제는 기능이 아니라 보관 계약이라 플래그로 면제되지 않는다.
 */
class AiRouteGenerationMaintenanceSchedulerTest {

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void 기능_플래그와_무관하게_유지보수_스케줄러를_등록한다(String enabled) {
        schedulerContextRunner()
                .withPropertyValues("ai-route.enabled=" + enabled)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .hasSingleBean(AiRouteGenerationMaintenanceScheduler.class);
                        });
    }

    /** 속성을 아예 주지 않는 기본 상태다. AI 경로를 한 번도 켠 적 없는 서버도 정리는 돌아야 한다. */
    @Test
    void 설정이_없어도_유지보수_스케줄러를_등록한다() {
        schedulerContextRunner()
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .hasSingleBean(AiRouteGenerationMaintenanceScheduler.class);
                        });
    }

    /** 일회성 비웹 프로세스에 scheduler thread가 남으면 작업이 끝나도 JVM이 종료되지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"performance-seed", "content-import"})
    void 일회성_비웹_프로필에서는_스케줄링을_활성화하지_않는다(String profile) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(SchedulerTestConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .doesNotHaveBean(
                                            TaskManagementConfigUtils
                                                    .SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
                        });
    }

    /**
     * 복구를 먼저, 정리를 나중에 부른다. 순서가 뒤집히면 이번 주기에 복구된 생성이 만료 시각을 받기
     * 전에 정리를 지나쳐, 다음 주기까지 한 바퀴를 더 기다린다.
     *
     * <p>기본 stub 이 0건을 골라 상한(200) 에 못 미치므로 {@link
     * #정리_대상이_상한을_넘으면_같은_sweep_안에서_반복해_모두_처리한다} 와 달리 각 메서드가 정확히 한
     * 번씩만 불려야 한다.
     */
    @Test
    void 복구를_먼저_하고_정리를_나중에_한다() {
        schedulerContextRunner()
                .run(
                        context -> {
                            AiRouteGenerationCleanupService cleanupService =
                                    context.getBean(AiRouteGenerationCleanupService.class);
                            context.getBean(AiRouteGenerationMaintenanceScheduler.class).sweep();

                            InOrder 순서 = inOrder(cleanupService);
                            순서.verify(cleanupService).recoverAbandoned();
                            순서.verify(cleanupService).removeExpired();
                            순서.verifyNoMoreInteractions();
                        });
    }

    /**
     * 대상이 상한을 넘으면 한 번의 호출로는 다 처리되지 않는다. 예전에는 그 남은 몫을 다음 주기(1분
     * 뒤)로 미뤄, 밀린 만큼 15분 보관 계약을 넘겨 DB 에 남았다. 이제는 고른 건수가 상한과 같은 동안
     * 같은 sweep 안에서 반복해, 상한보다 적게 골라 온 순간에만 멈춘다.
     */
    @Test
    void 정리_대상이_상한을_넘으면_같은_sweep_안에서_반복해_모두_처리한다() {
        schedulerContextRunner()
                .run(
                        context -> {
                            AiRouteGenerationCleanupService cleanupService =
                                    context.getBean(AiRouteGenerationCleanupService.class);
                            int 상한 = AiRouteGenerationCleanupService.BATCH_SIZE;
                            when(cleanupService.removeExpired())
                                    .thenReturn(배치(상한, 상한))
                                    .thenReturn(배치(상한, 상한))
                                    .thenReturn(배치(1, 1));

                            context.getBean(AiRouteGenerationMaintenanceScheduler.class).sweep();

                            verify(cleanupService, times(1)).recoverAbandoned();
                            verify(cleanupService, times(3)).removeExpired();
                        });
    }

    /**
     * 복구는 목록을 뽑은 뒤 잠그기 전에 호출자가 정상 완료한 건을 건너뛴다. 그래서 상한만큼 골라도
     * 되돌린 수는 그보다 적을 수 있다.
     *
     * <p>반복 여부를 되돌린 수로 판단하면 200건 중 한 건만 건너뛰어도 199가 돌아와 "대상이 상한보다
     * 적었다" 로 읽히고, 뒤에 남은 backlog 가 통째로 다음 주기로 밀린다. 밀리는 것은 아직 복구되지 않은
     * {@code GENERATING} 이고 만료는 논리적 실패 시각부터 매기므로, 오래 방치된 건일수록 이미 보관
     * 기간을 넘긴 상태다. 고른 수로 판단해야 같은 sweep 에서 끝까지 비운다.
     *
     * <p>두 번째 호출은 고른 200건을 <b>전부</b> 건너뛴 경우다. 되돌린 수가 0이어도 멈추면 안 된다.
     */
    @Test
    void 복구가_경합으로_건너뛰어도_고른_수가_상한이면_계속_반복한다() {
        schedulerContextRunner()
                .run(
                        context -> {
                            AiRouteGenerationCleanupService cleanupService =
                                    context.getBean(AiRouteGenerationCleanupService.class);
                            int 상한 = AiRouteGenerationCleanupService.BATCH_SIZE;
                            when(cleanupService.recoverAbandoned())
                                    .thenReturn(배치(상한, 상한 - 1))
                                    .thenReturn(배치(상한, 0))
                                    .thenReturn(배치(3, 3));

                            context.getBean(AiRouteGenerationMaintenanceScheduler.class).sweep();

                            verify(cleanupService, times(3)).recoverAbandoned();
                        });
    }

    /**
     * {@code @Scheduled} 가 실제로 붙어 도는지 본다. 등록 테스트만으로는 애너테이션을 떼도 통과한다.
     *
     * <p>주기가 아니라 <b>첫 실행</b>만 확인한다. 두 번째 발화까지 기다리면 1분짜리 테스트가 된다.
     */
    @Test
    void 등록된_스케줄러는_기동_직후_한_번_스스로_돈다() {
        new ApplicationContextRunner()
                .withPropertyValues("ai-route.maintenance-initial-delay-millis=0")
                .withUserConfiguration(SchedulerTestConfiguration.class)
                .run(
                        context -> {
                            AiRouteGenerationCleanupService cleanupService =
                                    context.getBean(AiRouteGenerationCleanupService.class);
                            verify(cleanupService, timeout(5000)).removeExpired();
                        });
    }

    /**
     * 스케줄러를 {@code @Bean} 으로 직접 만들지 않고 {@link Import} 로 올린다. 직접 만들면 클래스에 붙는
     * 조건 애너테이션이 평가되지 않아, 나중에 누가 조건을 도로 붙여도 이 테스트가 통과해 버린다.
     *
     * <p>{@code SchedulingConfig} 도 함께 올린다. 전역 스위치가 도메인 밖으로 나가서, 그것 없이는
     * {@code @Scheduled} 가 붙어 있어도 돌지 않는다.
     */
    private ApplicationContextRunner schedulerContextRunner() {
        return new ApplicationContextRunner()
                // 이 runner 는 application-test.yaml 을 읽지 않아 첫 실행이 기동 즉시다. 배치가 배경에서
                // 돌면 아래 호출 순서 단언에 제 호출과 섞여 들어온다.
                .withPropertyValues("ai-route.maintenance-initial-delay-millis=3600000")
                .withUserConfiguration(SchedulerTestConfiguration.class);
    }

    private static BatchOutcome 배치(int selected, int processed) {
        return new BatchOutcome(selected, processed);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AiRouteGenerationMaintenanceScheduler.class, SchedulingConfig.class})
    static class SchedulerTestConfiguration {

        /**
         * 배치가 record 를 돌려주므로 mock 기본값은 {@code null} 이다. 그대로 두면 스케줄러가 첫 호출에서
         * NPE 로 터져 모든 테스트가 같은 이유로 깨진다. 아무것도 처리하지 않은 결과를 기본값으로 둔다.
         */
        @Bean
        AiRouteGenerationCleanupService cleanupService() {
            AiRouteGenerationCleanupService cleanupService =
                    mock(AiRouteGenerationCleanupService.class);
            when(cleanupService.recoverAbandoned()).thenReturn(배치(0, 0));
            when(cleanupService.removeExpired()).thenReturn(배치(0, 0));
            return cleanupService;
        }
    }
}
