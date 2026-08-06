# G07 생성 orchestration

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 6 / 담당 B
- 선행: [F04 Embeddings](../foundation/F04-embeddings-adapter.md),
  [F05 Responses](../foundation/F05-responses-adapter.md), [G04 경로 조립](./G04-route-assembly.md),
  [G06 생명주기](./G06-generation-lifecycle.md)
- 후속: [G08 HTTP API](./G08-generation-api.md), [Q01 평가 runner](../release/Q01-evaluation-runner.md)

## 목표

입력·지원 상태·권한 snapshot을 검증하고 짧은 DB 단계 사이에서 Embeddings·Responses·서버 검증을 호출해
20초 안에 최종 generation 상태를 확정합니다.

## 정본 링크

- [경로 생성 정책](../../../prd/ai-ink-route.md#경로-생성-정책)
- [생성 횟수와 실패](../../../prd/ai-ink-route.md#생성-횟수와-실패)
- [ERD 목표 트랜잭션](../../../erd.md#목표-트랜잭션과-삭제-경계)
- [Facade와 트랜잭션](../../../conventions.md#facade와-트랜잭션)
- 필수 시나리오: [T-AIR-009·010·015](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 기존 외부 호출 분리 패턴은
  [OwnershipPaymentFacade](../../../../src/main/java/com/example/ilgeobolkka/ownership/facade/OwnershipPaymentFacade.java)의
  `TransactionTemplate` 단계를 참고합니다.
- F04·F05·G04·G06은 공급자·계산·영속 경계를 각각 제공합니다.
- AI Facade와 지원 도서 query·권한 snapshot 조합은 없습니다.

## 입력과 산출물

- 입력: readerId, idempotencyKey, G01 command
- 산출물: `AiRouteGenerationFacade`, `AiRouteEntitlementSnapshotFactory`, `GenerationExecutionResult`
- 호출 순서: 사전 검증 → G05 start → F04 → G02 → F05 → G03 → G04 → G06 complete/fail
- G08에 넘길 것: NEW/REPLAY/GENERATING/FINAL과 공개 실패 종류의 HTTP 독립 결과

## 수정 허용 파일

- 새 generation Facade·snapshot factory·호출 시간 budget helper
- 필요한 Book/Ownership/Rental/Ink Service public 조회는 기존 API를 우선 사용하며 기존 Facade는 수정 금지
- 새 `AiRouteGenerationFacadeMySqlIntegrationTest`

## 구현 조건

1. feature flag·도서 지원·권리·DB/environment policy profile을 G05와 외부 호출 전에 검사합니다.
2. 소장·활성 대여·잔액 snapshot은 서버 계산에만 쓰고 F04·F05 입력에 넣지 않습니다.
3. G05가 NEW일 때만 외부 호출하며 같은 key의 기존 상태는 Gateway 0회로 반환합니다.
4. G05 transaction commit 뒤 F04·F05를 호출하고 호출 중 transaction active=false를 테스트합니다.
5. Responses output validation 실패만 남은 전체 20초 안에서 한 번 재시도하고 같은 후보·정책을 사용합니다.
6. 20초 초과, provider budget/temporary, 최종 invalid output을 G06 FAILED 공개 code로 확정합니다.
7. 외부 호출 후 contentVersion이 바뀌어도 임시 생성 snapshot은 유지하고 저장 단계가 다시 검증합니다.
8. 생성 전후 InkAccount·Ledger·Rental·Ownership·ReadingSession·LibraryEntry가 바뀌지 않습니다.

## 테스트

- NEW 정상 ROUTE·두 NO_ROUTE와 기존 key replay의 Gateway 호출 수
- 첫 invalid→정상, 두 번 invalid, provider 오류, budget 오류, 20초 timeout
- 각 Gateway 호출 시 transaction inactive 확인과 complete/fail transaction rollback
- 권리·프로필·미지원 도서의 Gateway 0회
- 생성 전후 잉크·대여·세션·서재 row count·값 불변
- 명령: `./gradlew test --tests '*AiRouteGenerationFacadeMySqlIntegrationTest'`

## 제외 범위

- HTTP status·DTO·Retry-After
- 저장 route 전환과 화면 polling
- Background job·streaming·메시지 큐

## 완료 조건

- T-AIR-009·010·015 orchestration 경계와 20초·1회 재시도가 통합 테스트됩니다.
- 외부 호출 구간에 DB transaction이 없습니다.
- `./gradlew check`가 통과합니다.

## 인계

G08에 상태별 result와 공개 failure 종류를, Q01에 사용자 persistence를 우회하고 같은 내부 생성 엔진을
호출할 평가 진입점을 전달합니다.
