# G08 생성·조회 HTTP API

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 7 / 담당 B
- 선행: [G07 생성 orchestration](./G07-generation-orchestration.md)
- 후속: [W01 생성 화면](../web/W01-generation-page.md), [Q03 출시 회귀](../release/Q03-release-regression.md)

## 목표

인증 독자의 생성 POST와 소유자 generation GET을 API 계약의 DTO·200/201/202·공개 오류로 노출하고 기능
비활성 시 두 경로를 등록하지 않습니다.

## 정본 링크

- [목표 JSON endpoint](../../../api-spec.md#목표-json-엔드포인트)
- [생성 입력과 결과](../../../api-spec.md#생성-입력과-결과)
- [목표 오류](../../../api-spec.md#목표-오류)
- [인증과 세션](../../../conventions.md#인증과-세션)
- 필수 시나리오: [T-AIR-001·007~010·013~016·018·020](../../../test-strategy.md#5-필수-시나리오)
  (020은 저장 거부 뒤 `GET /api/ai-route-generations/{generationId}` 재조회가 생성 시점
  `additionalCostStatus` 스냅샷을 그대로 반환하고 재계산하지 않는 부분만 담당합니다)
- 이 스냅샷은 [ADR 0016](../../../adr/domain/0016-store-generation-item-cost-status.md)에 따라 조립기가
  계산한 값을 `ai_route_generation_item.additional_cost_status`에 저장합니다. 업무 시각 기반 권한
  이력은 동시 transaction의 커밋 순서를 복원할 수 없으므로 재구성에 사용하지 않습니다.

## 현재 구현 기준선

- 기존 API는
  [AuthenticatedReader](../../../../src/main/java/com/example/ilgeobolkka/global/security/AuthenticatedReader.java)를
  `@AuthenticationPrincipal`로 받습니다.
- 공개 오류는 [ErrorCode](../../../../src/main/java/com/example/ilgeobolkka/global/exception/ErrorCode.java)와
  [GlobalExceptionHandler](../../../../src/main/java/com/example/ilgeobolkka/global/exception/GlobalExceptionHandler.java)가
  소유합니다.
- AI Controller·DTO·Security matcher는 없습니다.

## 입력과 산출물

- endpoint: `POST /api/books/{bookId}/ai-route-generations`
- endpoint: `GET /api/ai-route-generations/{generationId}`
- 산출물: `AiRouteGenerationController`, `AiRouteGenerationApiFacade`, request/response/item DTO,
  API exception mapping
- W01에 넘길 것: 상태별 JSON 필드·HTTP status·Retry-After가 고정된 계약 테스트

## 수정 허용 파일

- 새 generation Controller·API Facade·API DTO·API exception
- 기존 `ErrorCode`, `GlobalExceptionHandler`, `SecurityConfig`의 AI generation 관련 최소 변경.
  앞 파동 작업이 이미 정의한 코드(예: S01의 `AI_ROUTE_GENERATION_CONSUMED`)는 재사용하고 다시 추가하지
  않습니다([공유 파일 소유권](../README.md#공유-파일-소유권))
- 기존 G05~G07 정책을 바꾸지 않는 범위의 generation request/view 조회 보강과 기본 예산 멱등 지문 구분
- 생성 조립 결과의 비용 상태를 임시 항목까지 전달하는 최소 보강과 V6 migration
- 새 `AiRouteGenerationApiMySqlIntegrationTest`, 조건부 경로 테스트

## 구현 조건

1. Principal readerId와 path bookId·generationId만 신뢰하고 request body의 독자 식별자를 받지 않습니다.
2. UUID `Idempotency-Key`, purpose·budget/depth 형식은 DTO와 G01 command에서 중복 없이 검증합니다.
3. 새 완료는 201, replay 완료는 200, 기존 GENERATING은 202; GET도 GENERATING 202·final 200입니다.
4. 모든 상태에서 API 계약 필드를 생략하지 않고 nullable·빈 items 규칙을 지킵니다.
5. NO_ROUTE는 정상 결과이며 failure envelope로 바꾸지 않습니다.
6. 다른 독자·만료 generation은 동일한 404이고 목적·상태 존재 여부를 노출하지 않습니다.
7. daily limit은 429+다음 UTC 자정까지 Retry-After, provider 실패는 계약의 503 code로 매핑합니다.
8. `AI_ROUTE_ENABLED=false`면 Controller·화면 조건이 false이고 기존 API는 계속 동작합니다.

## 테스트

- POST/GET 정상 ROUTE·NO_ROUTE·GENERATING·SAVED JSON 전체 필드
- 201/200/202, malformed key/input 400, unsupported 422, key reused 409, owner/expiry 404, limit 429, 503들
- 명시 예산과 `null` 기본 예산의 멱등 충돌, 잔액 변경 뒤 같은 `null` 기본 예산 재생
- 늦게 커밋된 과거 시각 권한 이력에도 생성 시점 비용 상태 유지, ROUTE 항목 수와 무관한 조회 수
- 다른 독자 generation의 동일 404 body와 Gateway/DB 변경 없음
- 비활성 context에서 두 route 404·기존 `/api/books` 정상
- CSRF 적용과 익명 401, 응답·로그의 provider 원문 비노출
- 명령: `./gradlew test --tests '*AiRouteGenerationApiMySqlIntegrationTest'`

## 제외 범위

- generation 저장 endpoint와 저장 경로 API
- HTML·polling JavaScript
- 기존 `AiRouteGenerationFacade` 내부 생성 정책 변경·API Facade의 Repository 직접 접근

## 완료 조건

- 두 endpoint의 성공·오류 matrix가 API 계약과 일치합니다.
- AIR-016 소유자 은닉과 AIR-018 feature flag 경계가 자동 검증됩니다.
- `./gradlew check`가 통과하고 ErrorCode 외 공용 코드 변경이 최소입니다.

## 인계

W01 담당자에게 MockMvc 계약 fixture와 예시 JSON을 전달합니다. Q03 담당자에게 비활성·활성 API 회귀
테스트 명령을 전달합니다.
