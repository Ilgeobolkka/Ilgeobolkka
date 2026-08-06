# S04 경로 콘텐츠 제공·진행

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 8 / 담당 A
- 선행: [S02 경로 조회](./S02-route-query.md)
- 후속: [W02 경로 화면](../web/W02-route-detail-page.md), [Q03 출시 회귀](../release/Q03-release-regression.md)

## 목표

기존 페이지 열기 API가 성공한 viewer session에만 저장 경로 page content를 제공하고 실제 제공 성공과
openedAt·route completedAt을 한 transaction으로 기록합니다.

## 정본 링크

- [열람과 과금](../../../prd/ai-ink-route.md#열람과-과금)
- [진행과 피드백](../../../prd/ai-ink-route.md#진행과-피드백)
- [API 경로 페이지 열기 순서](../../../api-spec.md#목표-json-엔드포인트)
- [페이지 열기 정책](../../../prd/product-policy.md#페이지-열기-처리-순서와-원자성)
- 필수 시나리오: [T-AIR-006·016](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 기존 [ReadingController](../../../../src/main/java/com/example/ilgeobolkka/reading/controller/ReadingController.java)는
  session POST·page PATCH·content GET을 제공합니다.
- [ReadingSessionService](../../../../src/main/java/com/example/ilgeobolkka/reading/service/ReadingSessionService.java)와
  [PageContentService](../../../../src/main/java/com/example/ilgeobolkka/reading/service/PageContentService.java)가
  현재 viewer 위치·콘텐츠를 검증합니다.
- S02가 owner route·item 조회를 제공합니다.

## 입력과 산출물

- endpoint: `POST /api/ai-routes/{routeId}/pages/{pageNumber}/content`
- header: `X-Viewer-Session-Id`
- 산출물: `AiRouteContentFacade`, `AiRouteContentController`
- 응답: 기존 `PageContent`; 성공 시 item openedAt, 마지막 최초 성공 시 route completedAt

## 수정 허용 파일

- 새 route content Facade·Controller·exception
- route/item Repository의 owner+item lock query
- 기존 reading/ownership/rental Service public API만 호출; `ReadingFacade`·`ReadingController` 수정 금지
- 새 `AiRoutePageContentApiMySqlIntegrationTest`

## 구현 조건

1. readerId+routeId+pageNumber로 owner route item을 확인하고 다른 독자·비포함 page는 404로 통일합니다.
2. viewer session이 같은 reader·book·현재 pageNumber인지 확인합니다.
3. 현재 소장 또는 active rental을 다시 확인하고 없으면 content·progress 모두 거부합니다.
4. `PageContentService.read` 성공과 openedAt 최초 기록을 같은 transaction에서 처리합니다.
5. 마지막 미열람 item의 최초 제공만 route completedAt을 기록하고 재조회는 시각을 바꾸지 않습니다.
6. 이 POST는 CSRF를 검증하지만 잉크·대여·reading session·library를 만들거나 변경하지 않습니다.
7. 기존 page 열기 POST/PATCH 실패 또는 content 읽기 실패 시 progress만 남기지 않습니다.

## 테스트

- 기존 열기 POST/PATCH 성공 뒤 content+openedAt, 열기 실패·위치 불일치·권한 만료 뒤 전체 거부
- 같은 item 반복·동시 요청의 openedAt 최초값 보존
- 마지막 두 item 동시 제공에서 completedAt 한 번
- content storage 실패 rollback과 잉크·대여·세션·서재 불변
- 다른 독자·route 비포함 page의 동일 404, CSRF 누락 403
- 명령: `./gradlew test --tests '*AiRoutePageContentApiMySqlIntegrationTest'`

## 제외 범위

- 새 과금·page open API
- route 순서 강제·자동 다음 page
- feedback·HTML viewer

## 완료 조건

- T-AIR-006의 열기 성공/실패 × content 성공/실패 matrix가 통과합니다.
- content를 실제 제공한 item만 progress가 있고 추가 과금은 0건입니다.
- 기존 reading 통합 테스트와 `./gradlew check`가 통과합니다.

## 인계

W02에 기존 page open→새 content POST 순서와 viewerSessionId 계약을 전달합니다. Q03에 기존 viewer·과금
회귀 테스트를 전달합니다.
