# S01 `generationId` 단일 저장

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 6 / 담당 C
- 선행: [F02 JPA 기반](../foundation/F02-jpa-mapping.md),
  [G06 생명주기](../generation/G06-generation-lifecycle.md),
  [GATE-AIR-03 권한 변동 오류](../00-implementation-gates.md#gate-air-03-저장-전-권한-변동-오류)
- 후속: [S03 현재 경로·삭제](./S03-current-delete.md), [W01 생성 화면](../web/W01-generation-page.md)

## 목표

소유자의 유효한 ROUTE generation을 클라이언트 경로 입력 없이 한 번만 저장하고 같은 transaction에서 새
route를 현재 경로로 지정합니다. 저장 재시도는 같은 route를 반환하고 현재 선택을 다시 바꾸지 않습니다.

## 정본 링크

- [저장과 생명주기 3~6단계](../../../prd/ai-ink-route.md#저장과-생명주기)
- [저장 경로 결과](../../../api-spec.md#저장-경로-결과와-상태-변경)
- [ERD 목표 트랜잭션](../../../erd.md#목표-트랜잭션과-삭제-경계)
- 필수 시나리오: [T-AIR-004·005·019](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- F02가 generation·route·item·current Entity를, G06이 만료·SAVED 전이를 제공합니다.
- 기존 도메인 잠금 조합은
  [ReadingFacade](../../../../src/main/java/com/example/ilgeobolkka/reading/facade/ReadingFacade.java)를 참고하지만
  페이지 열기 Facade를 호출하지 않습니다.
- 권한 변동 저장 거부의 공개 code는 GATE-AIR-03 해제 값만 사용합니다.

## 입력과 산출물

- endpoint: `POST /api/ai-route-generations/{generationId}/routes`, body 없음
- 산출물: `AiRouteSaveFacade`, `AiRouteSaveService`, `AiRouteSaveController`, 저장 route response DTO
- 첫 저장: 201; 같은 generation 재시도: 200+동일 route
- S03/W01에 넘길 것: routeId·current·items와 저장 재시도 계약

## 수정 허용 파일

- 새 save Facade·Service·Controller·DTO·exception
- generation·route·current Repository에 이 작업 전용 owner/lock query
- G06 lifecycle API는 호출만 하고 Entity status 직접 변경 금지
- 새 `AiRouteSaveMySqlIntegrationTest`

## 구현 조건

1. readerId+generationId로 잠금 조회하고 다른 독자·만료는 같은 404로 처리합니다.
2. ROUTE의 bookId·contentVersion·item·purpose를 서버 저장값에서만 읽고 request body로 받지 않습니다.
3. 현재 Book contentVersion이 다르면 `AI_ROUTE_CONTENT_CHANGED`로 거부합니다.
4. 현재 소장·활성 대여로 추가 비용을 다시 계산하고 생성 예산 초과는 GATE-AIR-03의 확정 오류로 거부합니다.
5. 같은 reader·book current PK를 원자적 upsert/lock하고 route·items·current·G06 SAVED를 한 transaction에
   처리합니다.
6. generationId UK로 동시 저장을 route 한 건으로 수렴시키고 재시도는 저장 route를 다시 현재로 만들지 않습니다.
7. 저장은 잉크·원장·대여·열람 세션·서재 위치를 변경하지 않습니다.

## 테스트

- 정상 첫 저장 201과 같은 generation 순차·동시 재시도 200+route 한 건
- 다른 독자·만료·NO_ROUTE·FAILED·contentVersion 변경·권한 비용 증가 거부
- 두 generation 동시 저장에서 route는 각각 존재하고 current는 완료 순서의 한 건
- 재시도 사이 다른 route를 current로 지정했을 때 재시도가 current를 되돌리지 않음
- 저장 전후 잉크·대여·세션·서재 불변과 rollback 주입
- 명령: `./gradlew test --tests '*AiRouteSaveMySqlIntegrationTest'`

## 제외 범위

- 목록·상세·현재 변경·삭제
- 콘텐츠 제공·openedAt·feedback
- HTML 저장 버튼

## 완료 조건

- T-AIR-004·005·019의 저장 부분이 실제 MySQL 동시성으로 통과합니다.
- 같은 generation은 기한 없이 route 하나와 연결되고 G06 최소 상태만 만료까지 남습니다.
- `./gradlew check`가 통과합니다.

## 인계

S03에 current lock/upsert 방식과 route 삭제 시 G06 CONSUMED 호출 계약을, W01에 201/200 응답 fixture를
전달합니다.
