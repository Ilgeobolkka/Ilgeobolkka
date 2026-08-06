# G05 멱등 생성 시작·일일 한도

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 3 / 담당 C
- 선행: [F02 JPA 기반](../foundation/F02-jpa-mapping.md), [G01 canonical command](./G01-purpose-input.md)
- 후속: [G06 생성 생명주기](./G06-generation-lifecycle.md), [G07 orchestration](./G07-generation-orchestration.md)

## 목표

같은 독자·UUID key의 생성 시작을 멱등 처리하고, 첫 외부 호출을 시작할 새 요청만 계정·UTC 날짜별 최대
10회로 generation 행과 함께 원자적으로 계수합니다.

## 정본 링크

- [저장과 생명주기 1단계](../../../prd/ai-ink-route.md#저장과-생명주기)
- [생성 횟수와 실패](../../../prd/ai-ink-route.md#생성-횟수와-실패)
- [ERD generation·daily usage](../../../erd.md#새-테이블)
- 필수 시나리오: [T-AIR-007·008](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- F02가 generation·daily usage Entity·Repository 기본형을 제공합니다.
- 기존 동시성 패턴은
  [InkService](../../../../src/main/java/com/example/ilgeobolkka/ink/service/InkService.java)의 account lock과
  MySQL 통합 테스트를 참고하지만 잉크 계좌를 AI limit lock으로 사용하지 않습니다.
- request fingerprint 구현은 없습니다.

## 입력과 산출물

- 입력: readerId, UUID idempotencyKey, G01 command, `Clock`
- 산출물: `AiRouteRequestFingerprint`, `AiRouteGenerationStartService`, `GenerationStartResult`
- start result: NEW, EXISTING_GENERATING, EXISTING_FINAL, KEY_REUSED, DAILY_LIMIT
- G06/G07에 넘길 것: NEW generationId 또는 저장된 기존 상태·expiresAt

## 수정 허용 파일

- 새 generation start service·fingerprint 타입
- `AiRouteGenerationRepository`, `AiRouteDailyUsageRepository`에 이 작업 전용 lock/upsert query 추가
- 새 `AiRouteGenerationStartMySqlIntegrationTest`, fingerprint 단위 테스트

## 구현 조건

1. fingerprint는 bookId·contentVersion·normalizedPurpose·requestType·budget/depth의 고정 순서를 SHA-256으로
   계산하고 원문·JSON map 순서에 의존하지 않습니다.
2. `(readerId,idempotencyKey)` 기존 행을 소유자 조건으로 잠금 조회하고 같은 fingerprint면 저장 상태를 반환합니다.
3. 다른 fingerprint면 `AI_ROUTE_IDEMPOTENCY_KEY_REUSED` 결과이며 usage를 증가시키지 않습니다.
4. 새 key만 UTC `LocalDate` usage 행을 원자적으로 조건 증가하고 10을 넘으면 generation을 만들지 않습니다.
5. usage 증가와 `GENERATING` insert는 한 transaction이며 commit 뒤에만 G07이 외부 호출합니다.
6. 동시 insert UK 경합은 500이 아니라 기존 행 재조회로 같은 결과에 수렴합니다.
7. 로그에 key 전체·fingerprint·purpose를 남기지 않습니다.

## 테스트

- 같은 key 같은 command의 순차·동시 요청이 generation 1행, usage 1회
- 같은 key 다른 command가 KEY_REUSED, usage 추가 0
- 다른 key 11개 동시 요청에서 10개 NEW·1개 DAILY_LIMIT
- 성공 예정·실패 예정 구분 없이 시작 시점 계수, UTC 자정 전후 별도 usage
- rollback 주입 시 generation·usage 둘 다 미반영
- 명령: `./gradlew test --tests '*AiRouteGenerationStartMySqlIntegrationTest'`

## 제외 범위

- Embeddings·Responses 호출, 최종 결과 저장
- 15분 expiresAt 계산·cleanup·중단 복구
- HTTP status·Retry-After

## 완료 조건

- T-AIR-007·008의 시작·계수 경계가 실제 MySQL 동시성으로 통과합니다.
- NEW 반환 시 transaction이 종료되어 있습니다.
- `./gradlew check`가 통과합니다.

## 인계

G06에 상태별 잠금 조회와 fingerprint 계약을, G07에 NEW만 외부 호출한다는 결과 타입을 전달합니다.
