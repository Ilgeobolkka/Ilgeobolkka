package com.example.ilgeobolkka.airoute.scheduler;

import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 버려진 생성 복구와 만료 정리를 주기적으로 돌린다.
 *
 * <p><b>{@code ai-route.enabled} 로 막지 않는다.</b> 이 배치가 하는 일은 AI 경로라는 기능을 제공하는 것이
 * 아니라 이미 DB 에 쓰인 데이터를 계약대로 지우는 것이다. 임시 목적은 사용자 콘텐츠이고 15분 뒤 삭제는
 * 보관 계약이므로 기능을 끈다고 면제되지 않는다. 기능을 켠 채 결과를 만들어 둔 뒤 껐다면, 그 데이터를
 * 지울 주체가 여기밖에 없다. 조건부로 두면 그때부터 임시 목적·페이지 결과·멱등 상태와 중단된
 * {@code GENERATING} 이 영구히 남는다.
 *
 * <p>기능 플래그는 controller 와 외부 호출에만 걸린다. 새 데이터가 더 생기지 않을 뿐, 남은 데이터는
 * 계속 정리한다.
 *
 * <p>스케줄링 자체를 켜는 것은 {@code SchedulingConfig} 다. 전역 스위치를 도메인 클래스가 들고 있으면
 * 이 클래스를 지울 때 다른 도메인의 배치까지 조용히 멈춘다.
 *
 * <p>기동 직후 한 번과 그 뒤 주기 실행을 이 하나로 처리한다. {@code fixedDelay} 는 기본
 * {@code initialDelay} 가 0이라 첫 실행이 기동 직후다.
 */
@Slf4j
@Component
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
     * 복구를 먼저 한다. 복구는 논리적으로 실패한 시각을 기준으로 만료를 매기므로, 오래 방치된 생성은
     * 복구되자마자 이미 만료 상태다. 정리를 나중에 두면 그런 행이 같은 주기에 사라진다. 순서를 뒤집으면
     * 다음 주기를 한 바퀴 더 기다린다.
     *
     * <p>건수를 남기지 않으면 이 배치가 도는지 운영에서 확인할 방법이 없다. 아무 일도 없던 주기까지
     * 찍으면 1분마다 소음이 되므로 무언가 처리했을 때만 남긴다.
     *
     * <p>{@code initialDelay} 만 밖에서 바꿀 수 있게 열어 두었다. 통합 테스트가 만료 데이터를 만들어
     * 두고 단언하는 동안 이 배치가 끼어들어 지워 버리면 안 되기 때문이다. 운영에서는 기본값 0으로 기동
     * 직후 한 번 돈다.
     */
    @Scheduled(
            fixedDelay = SWEEP_INTERVAL_MILLIS,
            initialDelayString = "${ai-route.maintenance-initial-delay-millis:0}")
    public void sweep() {
        int recovered = cleanupService.recoverAbandoned();
        int removed = cleanupService.removeExpired();
        if (recovered > 0 || removed > 0) {
            // 건수만 남긴다. 멱등 키·지문·목적은 어떤 형태로도 로그에 넣지 않는다.
            log.info("AI 경로 유지보수: 중단 복구 {}건, 만료 정리 {}건", recovered, removed);
        }
    }
}
