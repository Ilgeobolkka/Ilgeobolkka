# G06 생성 완료·실패·만료 복구

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 4 / 담당 C
- 선행: [G05 생성 시작](./G05-idempotency-daily-limit.md)
- 후속: [G07 orchestration](./G07-generation-orchestration.md), [S01 단일 저장](../saved-route/S01-save-generation.md)

## 목표

GENERATING 이후 ROUTE·NO_ROUTE·FAILED·SAVED·CONSUMED 전이와 15분 보관, 만료 정리, 중단된 생성 복구를
짧은 MySQL transaction으로 구현합니다.

## 정본 링크

- [저장과 생명주기](../../../prd/ai-ink-route.md#저장과-생명주기)
- [생성 횟수와 실패](../../../prd/ai-ink-route.md#생성-횟수와-실패)
- [ERD generation 상태](../../../erd.md#새-테이블)
- 필수 시나리오: [T-AIR-008·009·013·019](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- G05가 GENERATING 생성과 기존 상태 조회를 제공합니다.
- application에는 AI cleanup scheduler·startup recovery가 없습니다.
- 공통 시간은 기존 `Clock` Bean을 사용하며 system clock 직접 호출을 피합니다.

## 입력과 산출물

- 입력: generationId, ROUTE items 또는 NO_ROUTE 사유 또는 공개 failureCode, `Clock`
- 산출물: `AiRouteGenerationLifecycleService`, `AiRouteGenerationCleanupService`
- 산출물: 완료·실패·SAVED·CONSUMED 전이와 소유자 유효 조회
- G07/S01/G08에 넘길 것: 상태별 DTO projection과 만료 판정 API

## 수정 허용 파일

- generation lifecycle·cleanup service
- generation·item Repository의 상태 전이·만료 query
- 조건부 scheduler/startup recovery 설정 파일은 이 작업 전용 새 파일
- 새 `AiRouteGenerationLifecycleMySqlIntegrationTest`

## 구현 조건

1. `GENERATING -> ROUTE|NO_ROUTE|FAILED`만 첫 최종 전이로 허용하고 completedAt·expiresAt=+15분을 같은
   Clock 값으로 기록합니다.
2. ROUTE만 item을, NO_ROUTE만 사유·조건부 minimumRequiredInk를, FAILED만 공개 failureCode를 저장합니다.
3. 만료 시각부터 조회·저장을 거부하고 cleanup 실행 전이라도 404 대상 projection을 반환합니다.
4. 저장 성공 호출은 임시 목적·입력·item을 지우고 SAVED+routeId+fingerprint를 원래 expiresAt까지 남깁니다.
5. route 삭제 호출은 CONSUMED로 전이하고 pointer를 비워 재저장을 막습니다.
6. cleanup은 item 후 generation 순서로 만료 상태를 삭제하고 과거 key 사용 여부를 남기지 않습니다.
7. 전체 제한을 지난 GENERATING은 FAILED로 복구하고 같은 key가 외부 호출을 다시 시작하지 않게 합니다.

## 테스트

- 각 허용·금지 상태 전이와 상태별 nullable field
- 완료 15분 직전·정확 expiresAt·직후의 조회와 cleanup 전후 동일 동작
- ROUTE/NO_ROUTE/FAILED 재조회, SAVED→CONSUMED→만료 삭제
- 중단 GENERATING 복구와 같은 key 재조회 시 외부 호출 재시작 신호 없음
- cleanup 중간 실패 rollback과 다른 독자 조건 조회 0건
- 명령: `./gradlew test --tests '*AiRouteGenerationLifecycleMySqlIntegrationTest'`

## 제외 범위

- 외부 호출·재시도와 HTTP polling
- 저장 route·current 생성 transaction
- scheduler 주기 운영 설정 일반화

## 완료 조건

- T-AIR-008·009·013·019의 persistence 생명주기 부분이 고정 Clock으로 통과합니다.
- 만료 판정이 cleanup 실행 여부에 의존하지 않습니다.
- `./gradlew check`가 통과합니다.

## 인계

G07에 complete/fail API를, S01에 SAVED API를, S03에 CONSUMED API를 전달합니다. 후속 작업은 Entity status를
직접 변경하지 않습니다.

- 만료 판정은 두 경계가 서로 다릅니다. 조회·저장은 `now < expiresAt`만 유효로 보고(`expiresAt`부터 만료),
  중단 복구는 나이가 제한 시간을 **넘은** 것만 대상으로 봅니다. 정본이 "만료 시각부터"와 "20초를 넘으면"
  으로 다르게 정하기 때문이며, 어느 한쪽에 맞춰 통일하면 안 됩니다.
- `AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT`이 전체 요청 제한 20초를 들고 있습니다. G07이
  자기 타임아웃에 같은 값을 써야 하는데, 이 작업의 수정 허용 파일 안에 공용 정책 타입을 둘 자리가 없어
  cleanup service에 두었습니다. G07 착수 때 옮길지 정합니다.
- `AiRouteGenerationView`에 경로 항목이 없습니다. 항목까지 필요한 화면 응답은 G08이 따로 조회합니다.
- `markConsumed`에 소유자 조건이 없습니다. 저장 경로와 생성이 1:1이라 S03이 경로 소유권을 확인하면
  전이적으로 막히지만, 이 API 자체는 `generationId`만으로 상태를 옮깁니다.
- `@EnableScheduling`을 조건부 스케줄러 클래스 안에 두었습니다. `AI_ROUTE_ENABLED=false`인 환경에서는
  스케줄링 자체가 꺼지므로, 다른 도메인이 `@Scheduled`를 추가하면 그 환경에서 아무 오류 없이 돌지
  않습니다. 그 시점에 `@EnableScheduling`을 전역 설정으로 옮기는 것이 맞습니다.
- 스케줄러가 복구·정리 건수를 로그로 남기지 않습니다. 운영에서 배치가 도는지 확인할 방법이 없습니다.
