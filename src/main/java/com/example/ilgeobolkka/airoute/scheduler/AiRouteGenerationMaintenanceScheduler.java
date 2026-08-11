package com.example.ilgeobolkka.airoute.scheduler;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 버려진 생성 복구와 만료 정리를 주기적으로 돌린다.
 *
 * <p>{@code ai-route.enabled} 가 참일 때만 등록한다. AI 경로를 끈 서버는 이 테이블을 쓰지 않으므로 도는
 * 일도 없어야 하고, {@link EnableScheduling} 도 이 조건 안에 두어 AI 경로 때문에 스케줄러가 켜지는 일이
 * 없게 한다.
 *
 * <p>기동 직후 한 번과 그 뒤 주기 실행을 모두 이 하나로 처리한다. {@code fixedDelay} 는 기본
 * {@code initialDelay} 가 0이라 첫 실행이 기동 직후다. 별도 startup 훅을 두면 같은 일을 두 번 한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AiRouteGenerationMaintenanceScheduler {

    /**
     * 앞선 실행이 끝난 뒤부터 잰다. 보관 기간이 15분이라 1분 간격이면 충분히 촘촘하고, 복구 기준이
     * 20초라 버려진 생성은 길어야 1분 남짓 {@code GENERATING} 으로 남는다. 그동안 같은 키 재시도는
     * 진행 중 상태를 받으므로 계약을 깨지 않는다.
     */
    private static final long SWEEP_INTERVAL_MILLIS = 60_000L;

    private final AiRouteGenerationCleanupService cleanupService;

    /**
     * 복구를 먼저 한다. 복구가 매긴 만료 시각은 지금부터 15분 뒤라 이번 정리 대상이 아니고, 다음
     * 주기부터 자연히 대상이 된다.
     */
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        cleanupService.recoverAbandoned();
        cleanupService.removeExpired();
    }
}
