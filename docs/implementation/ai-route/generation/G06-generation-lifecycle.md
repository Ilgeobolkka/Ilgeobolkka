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
   이 보장은 보관 창 안에서만 성립합니다. 창을 넘긴 같은 key는 새 요청이므로 일일 횟수를 다시 씁니다.

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

- 중단 복구는 보관 기간을 복구를 돌린 시각이 아니라 `createdAt + 전체 제한`부터 잽니다. 지금 시각을
  기준으로 잡으면 사라졌어야 할 멱등 상태가 복구할 때마다 되살아납니다. 그래서 오래 방치된 생성은
  복구된 같은 스윕에서 정리됩니다.
- 만료 판정은 두 경계가 서로 다릅니다. 조회·저장은 `now < expiresAt`만 유효로 보고(`expiresAt`부터 만료),
  중단 복구는 나이가 제한 시간을 **넘은** 것만 대상으로 봅니다. 정본이 "만료 시각부터"와 "20초를 넘으면"
  으로 다르게 정하기 때문이며, 어느 한쪽에 맞춰 통일하면 안 됩니다.
- `AiRouteGenerationCleanupService.GENERATION_TIME_LIMIT`이 전체 요청 제한 20초를 들고 있습니다. G07이
  자기 타임아웃에 같은 값을 써야 하는데, 이 작업의 수정 허용 파일 안에 공용 정책 타입을 둘 자리가 없어
  cleanup service에 두었습니다. G07 착수 때 옮길지 정합니다.
- `AiRouteGenerationView`가 정렬된 경로 항목을 함께 담습니다. `position`·`pageNumber`·`relevance`·
  `prerequisite`·`role`이며 `ROUTE`가 아니면 빈 목록입니다.
- `api-spec.md`의 `items[]`가 요구하는 `estimatedMinutes`·`guide`·`additionalCostStatus`는 이 projection에
  없습니다. 앞의 둘은 이미 저장된 값으로 만듭니다 — 예상 시간은 페이지 콘텐츠 길이·형식(PRD 예상 독서
  시간), 가이드는 `book_page.ai_public_guide_topic`과 `role`을 고정 템플릿에 넣어 만듭니다(PRD AI 페이지
  가이드). `additionalCostStatus`의 실현 방식은 **G08 leaf가 자기 결정으로 명시**해 두었으므로
  (컬럼 없이 `page_rental` 이력으로 재구성하거나, 저장 방식이면 그때 F01 migration 승인) 여기서 미리
  정하지 않습니다. 그 결정이 나면 필요한 필드를 projection에 추가하세요.
- **허용 파일 밖 변경** 셋입니다.
  1. `AiRouteGenerationStartService`와 그 통합 테스트(G05 소유) — 완료 조건의 만료 판정 요구 때문.
     승인 완료.
  2. `src/test/resources/application-test.yaml`(F03 소유) — 배치가 F01의
     `AiRouteSchemaMigrationTest` 픽스처를 훼손하는 것을 막으려고. 승인 완료.
  3. `global/config/SchedulingConfig`(F03 소유) — 전역 스케줄링 스위치를 도메인 밖으로 뺀 새 파일.
     승인 완료. G06의 "이 작업 전용 새 파일" 예외로는 안 덮입니다. 도메인 전용이 아니라는 것이
     이 파일을 만든 이유 전부이기 때문입니다.
- 위 3번과 관련해 문서 불일치가 있습니다. `README.md`의 소유권 표는 F03 행을 "`application*.yaml`,
  `.env.example`, OpenAI 조건부 설정·공통 HTTP Bean"으로 적었는데, `F03-openai-configuration.md:39`는
  "`global/config` 또는 `infra/openai`의 새 설정·Properties 파일"로 더 넓습니다. 표만 보면 안 걸리고
  leaf를 보면 걸립니다. 어느 쪽으로 맞출지 정리가 필요합니다.
- `markConsumed`에 소유자 조건이 없습니다. 저장 경로와 생성이 1:1이라 S03이 경로 소유권을 확인하면
  전이적으로 막히지만, 이 API 자체는 `generationId`만으로 상태를 옮깁니다. **S03은 이 전제를 테스트로
  고정하세요** — 남의 경로를 삭제하려는 요청이 소비 처리까지 가지 않는지 확인해야 합니다.
- `completeWithRoute`가 던지면 생성은 `GENERATING`으로 남습니다. 항목 unique 제약 위반 등으로 실패하면
  `ROUTE` 전이가 함께 롤백되기 때문입니다. G07은 예외를 잡아 `fail()`로 마감하세요. 방치하면 20초 뒤
  복구가 `AI_ROUTE_GENERATION_TIMEOUT`으로 처리하는데, 실제 원인과 다른 코드가 남습니다.
- 유지보수 스케줄러는 `ai-route.enabled`로 막지 않습니다. 이 배치는 기능을 제공하는 것이 아니라 이미
  쓰인 데이터를 보관 계약대로 지웁니다. 기능을 켠 채 임시 결과를 만들어 두고 끄면 지울 주체가 사라져
  임시 목적·페이지 결과·멱등 상태가 영구히 남습니다. **조건을 도로 붙이면 안 됩니다.** 플래그는
  controller와 외부 호출에만 겁니다.
- 전역 스케줄링 스위치는 `global/config/SchedulingConfig`에 있습니다. 도메인 클래스가 들고 있으면 그
  클래스를 지울 때 다른 도메인의 배치까지 조용히 멈추므로 분리했습니다.
- 첫 실행 지연만 `ai-route.maintenance-initial-delay-millis`로 바꿀 수 있습니다. 통합 테스트가 만료
  데이터를 만들어 두고 단언하는 동안 배치가 끼어드는 것을 막는 용도이고, 운영 기본값은 0입니다.
- 스케줄러는 무언가 처리한 주기에만 건수를 로그로 남깁니다. 멱등 키·요청 지문·정규화한 목적은 어떤
  형태로도 넣지 않습니다.
