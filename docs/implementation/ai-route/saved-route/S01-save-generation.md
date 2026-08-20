# S01 `generationId` 단일 저장

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 6 / 담당 C
- 선행: [F02 JPA 기반](../foundation/F02-jpa-mapping.md),
  [G06 생명주기](../generation/G06-generation-lifecycle.md),
  [해제된 저장 거부 오류 계약](../../../api-spec.md#저장-경로-결과와-상태-변경)
- 후속: [S03 현재 경로·삭제](./S03-current-delete.md), [W01 생성 화면](../web/W01-generation-page.md)

## 목표

소유자의 유효한 ROUTE generation을 클라이언트 경로 입력 없이 한 번만 저장하고 같은 transaction에서 새
route를 현재 경로로 지정합니다. 저장 재시도는 같은 route를 반환하고 현재 선택을 다시 바꾸지 않습니다.

## 정본 링크

- [저장과 생명주기 3~6단계](../../../prd/ai-ink-route.md#저장과-생명주기)
- [저장 경로 결과](../../../api-spec.md#저장-경로-결과와-상태-변경)
- [ERD 목표 트랜잭션](../../../erd.md#목표-트랜잭션과-삭제-경계)
- 필수 시나리오: [T-AIR-004·005·019·020](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- F02가 generation·route·item·current Entity를, G06이 만료·SAVED 전이를 제공합니다.
- 기존 도메인 잠금 조합은
  [ReadingFacade](../../../../src/main/java/com/example/ilgeobolkka/reading/facade/ReadingFacade.java)를 참고하지만
  페이지 열기 Facade를 호출하지 않습니다.
- 권한 변동 저장 거부는 GATE-AIR-03에서 확정한 `409 AI_ROUTE_ENTITLEMENT_CHANGED`만 사용합니다.
  이 endpoint가 반환하는 오류 코드의 정의 주체는 아래 수정 허용 파일을 따릅니다.

## 입력과 산출물

- endpoint: `POST /api/ai-route-generations/{generationId}/routes`, body 없음
- 산출물: `AiRouteSaveFacade`, `AiRouteSaveService`, `AiRouteSaveController`, 저장 route response DTO
- 첫 저장: 201; 같은 generation 재시도: 200+동일 route
- S03/W01에 넘길 것: routeId·current·items와 저장 재시도 계약

## 수정 허용 파일

- 새 save Facade·Service·Controller·DTO·exception
- `ErrorCode`에 이 endpoint가 반환하는 `AI_ROUTE_ENTITLEMENT_CHANGED`·`AI_ROUTE_CONTENT_CHANGED`·
  `AI_ROUTE_GENERATION_CONSUMED`(모두 409) 상수와 `GlobalExceptionHandler`의 대응 매핑
  ([api-spec 목표 오류](../../../api-spec.md#목표-오류) 기준). `AI_ROUTE_GENERATION_CONSUMED`는 생성
  endpoint의 멱등 재조회도 반환하지만 이 작업이 먼저 진행하므로 여기서 정의하고 G08이 재사용합니다.
  이 endpoint가 반환하지 않는 AI 오류 코드는 추가하지 않습니다
- generation·route·current Repository에 이 작업 전용 owner/lock query
- G06 lifecycle API는 호출만 하고 Entity status 직접 변경 금지
- 새 `AiRouteSaveMySqlIntegrationTest`

## 구현 조건

1. readerId+generationId로 잠금 조회하고 다른 독자·만료는 같은 404로 처리합니다.
2. ROUTE의 bookId·contentVersion·item·purpose를 서버 저장값에서만 읽고 request body로 받지 않습니다.
3. 현재 Book contentVersion이 다르면 `AI_ROUTE_CONTENT_CHANGED`로 거부합니다. 2차 MVP는
   [`contentVersion`을 재발급하지 않으므로](../../../prd/ai-ink-route.md#재평가와-지원-활성화-순서) 이
   거부는 도달 가능한 사용자 경로가 아니라 불변식 방어 검사입니다. 계약을 제거하지 말고 테스트도 값을
   직접 조작한 불변식 검사로 작성합니다.
4. 현재 소장·활성 대여로 추가 비용을 다시 계산하고 생성 예산을 넘으면 `409 AI_ROUTE_ENTITLEMENT_CHANGED`로
   거부합니다. 이때 generation을 소비하지 않고 route·current를 만들지 않아 임시 결과가 만료 전까지
   `ROUTE`로 남습니다([GATE-AIR-03](../00-implementation-gates.md#gate-air-03-저장-전-권한-변동-오류)).
5. 같은 reader·book current PK를 원자적 upsert/lock하고 route·items·current·G06 SAVED를 한 transaction에
   처리합니다.
6. 같은 generation의 동시 저장은 generation 행 잠금(`PESSIMISTIC_WRITE`)으로 직렬화해 route 한 건으로
   수렴시킵니다. 진 쪽은 잠금을 얻은 시점에 `SAVED`를 읽어 이미 만들어진 route를 그대로 반환하므로 UK
   위반을 잡아 수렴시키는 경로를 따로 두지 않으며, `uk_ai_reading_route_generation`은 그 뒤를 받치는
   백스톱입니다. 재시도는 저장 route를 다시 현재로 만들지 않습니다.
7. 저장은 잉크·원장·대여·열람 세션·서재 위치를 변경하지 않습니다.

## 테스트

- 정상 첫 저장 201과 같은 generation 순차·동시 재시도 200+route 한 건
- 다른 독자·만료·NO_ROUTE·FAILED·contentVersion 변경 거부
- 권한 변동 상황은 주입한 `Clock`으로 만든다(test-strategy 시간 경계 관례). 절차 1의 만료 404가 절차 4보다
  먼저이므로, 30일 대여의 잔여 기간이 15분 미만이 되도록 시계를 맞춘 뒤 생성해 대여 만료가 임시 결과
  만료보다 먼저 오게 하고, 대여만 만료된 시점에 저장한다. 비용이 예산을 넘으면
  `409 AI_ROUTE_ENTITLEMENT_CHANGED`, 저장값 기준으로 generation이 아직 `ROUTE`이고 route·current가
  생기지 않았으며 그 요청으로 잉크·대여·내역이 바뀌지 않음을 확인
- 위 거부 뒤 권한이 그대로면 재시도도 같은 409. 이어서 같은 15분 창 안에서 그 페이지를 다시 대여해 비용이
  예산 이하로 내려가면 같은 `generationId` 저장이 다시 가능함을 확인(거부를 기록하는 상태를 두지 않음,
  T-AIR-020). 공개 `GET` 재조회 응답은 [G08](../generation/G08-generation-api.md) 범위라 여기서 단언하지 않음
- 거부 응답 바디의 필드 집합이 정확히 `code`·`message`뿐이고 재계산 비용·권한·페이지 상세 필드가 없음을
  확인(필드가 추가되면 실패하도록 전체 키 집합을 단언)
- 두 generation 동시 저장에서 route는 각각 존재하고 current는 완료 순서의 한 건
- 재시도 사이 다른 route를 current로 지정했을 때 재시도가 current를 되돌리지 않음
- 저장 전후 잉크·대여·세션·서재 불변과 rollback 주입
- 명령: `./gradlew test --tests '*AiRouteSaveMySqlIntegrationTest'`

## 제외 범위

- 목록·상세·현재 변경·삭제
- 콘텐츠 제공·openedAt·feedback
- HTML 저장 버튼

## 완료 조건

- T-AIR-004·005·019·020의 저장 부분이 실제 MySQL 동시성으로 통과합니다.
- 같은 generation은 기한 없이 route 하나와 연결되고 G06 최소 상태만 만료까지 남습니다.
- `./gradlew check`가 통과합니다.

## 인계

S03에 current 지정 방식과 route 삭제 시 G06 CONSUMED 호출 계약을, W01에 201/200 응답 fixture를
전달합니다.

- current 지정은 `AiRouteCurrentRepository.selectAsCurrent`의 PK 원자 upsert(`INSERT ... ON DUPLICATE
  KEY UPDATE`)로 확정했습니다. 저장 Facade가 `READ_COMMITTED`로 열려 없는 행을 잠금 조회해도 잠금이
  남지 않으므로, 조회로 잠그고 없으면 insert하는 방식은 동시 첫 저장에서 중복 키 오류가 됩니다. S03의
  현재 경로 변경도 같은 문장을 씁니다.
- 저장은 generation 행을 `findOwnedNotExpiredForUpdate`로 먼저 잠그고 route insert → current upsert
  순서로 진행합니다. S03이 삭제에서 같은 행들을 잡을 때 순서를 맞춰야 교착하지 않습니다.
- `AiRouteCurrent.select`·`changeRoute`와 그 소유자 가드는 upsert를 쓰면서 production 호출자가 없어졌고
  실제 불변식은 복합 FK `fk_ai_route_current_route`가 지킵니다. S03도 같은 upsert를 쓰면 제거 대상이므로
  S03에서 판단합니다(F02 산출물이라 S01의 수정 허용 파일이 아닙니다).
