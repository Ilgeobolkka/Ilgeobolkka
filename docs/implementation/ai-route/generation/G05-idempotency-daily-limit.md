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
- 기존 키 선조회: 유효한 행이 있으면 상태 변경 없이 EXISTING_GENERATING, EXISTING_FINAL, KEY_REUSED 반환
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
   G07의 기존 키 선조회도 같은 판정을 사용하며, 행이 없거나 만료했으면 아무것도 바꾸지 않고 빈 결과를
   반환합니다. 선조회 뒤 생긴 동시 요청은 실제 start의 두 번째 확인과 UK 경합 수렴이 처리합니다.
4. 새 key만 UTC `LocalDate` usage 행을 원자적으로 조건 증가하고 10을 넘으면 generation을 만들지 않습니다.
5. usage 증가와 `GENERATING` insert는 한 transaction이며 commit 뒤에만 G07이 외부 호출합니다.
6. 동시 insert UK 경합은 500이 아니라 기존 행 재조회로 같은 결과에 수렴합니다.
7. 로그에 key 전체·fingerprint·purpose를 남기지 않습니다.

## 테스트

- 같은 key 같은 command의 순차·동시 요청이 generation 1행, usage 1회
- 같은 key 다른 command가 KEY_REUSED, usage 추가 0
- 기존 키 선조회가 같은 command·다른 command·없는 key·만료 key를 구분하고 상태와 usage를 바꾸지 않음
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

- 잠금 순서는 `ai_route_daily_usage` 행 → `ai_route_generation` 행입니다. 반대로 잡으면 아직 없는 생성
  행의 gap lock을 쥔 채 사용량 행을 기다려 같은 독자의 동시 요청끼리 교착합니다. 두 행을 함께 잠그는
  후속 작업은 이 순서를 따릅니다.
- fingerprint는 `AiRouteRequestFingerprint.of(command)` 하나로만 계산하며 필드마다 길이를 앞에 붙여
  이어 붙인 SHA-256입니다. 이미 저장된 `request_fingerprint`와 재계산 값이 같아야 재시도가 멱등하므로
  인코딩을 바꾸면 보관 중인 생성이 모두 키 재사용으로 판정됩니다.
- `remainingDailyGenerations`는 이 작업의 결과 타입에 없습니다. `GenerationStartResult`가 사용량 값을
  들고 나오지 않으므로 G06·G08이 응답을 만들 때 별도로 조회합니다.
- `EXISTING_FINAL`은 `FAILED`와 `CONSUMED`도 실어 나릅니다. `api-spec.md`의 응답 `status`는
  `GENERATING`·`ROUTE`·`NO_ROUTE`·`SAVED` 넷뿐이므로 `FAILED` 재시도는 상태 응답이 아니라 최초 오류
  응답으로 나가야 합니다. G08이 매핑할 때 이 넷만 보고 만들면 실패 재시도가 빠집니다.
- **G06(SCRUM-462)이 이 작업의 수정 허용 파일 밖에서 `AiRouteGenerationStartService`와 그 통합 테스트를
  바꿨습니다.** 조회만 만료를 보고 시작은 보지 않으면 같은 행에 두 경로가 다른 답을 내기 때문이며,
  G06 완료 조건이 "만료 판정이 cleanup 실행 여부에 의존하지 않는다"를 요구합니다. 아래가 그 내용입니다.
- 보관 만료 판정은 G06에서 시작 경로까지 들어왔습니다. 만료한 행은 정리 배치 전이라도 없는 것으로 보고
  그 자리에서 지운 뒤 새 요청으로 시작합니다. 남겨 두면 새 insert 가 멱등 unique key 에 걸립니다.
