# W02 저장 경로 상세·읽기 화면

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 9 / 담당 B
- 선행: [S02 목록·상세](../saved-route/S02-route-query.md),
  [S03 현재·삭제](../saved-route/S03-current-delete.md),
  [S04 콘텐츠·진행](../saved-route/S04-content-progress.md),
  [S05 피드백](../saved-route/S05-feedback.md)
- 후속: [Q03 출시 회귀](../release/Q03-release-regression.md)

## 목표

저장 route의 고정 추천 순서를 표시하고 기존 page open API와 S04 content POST를 순서대로 호출해 경로
읽기·진행·완료·현재 지정·삭제·피드백을 한 화면에서 제공합니다.

## 정본 링크

- [목표 HTML 화면](../../../api-spec.md#목표-html-화면)
- [열람과 과금](../../../prd/ai-ink-route.md#열람과-과금)
- [진행과 피드백](../../../prd/ai-ink-route.md#진행과-피드백)
- [기존 viewer session 계약](../../../conventions.md#인증과-세션)
- 필수 시나리오: [T-AIR-006·016·017](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 기존 [viewer.js](../../../../src/main/resources/static/js/viewer/viewer.js)는 원본 연속 page navigation과
  viewerSessionId 저장 방식을 구현합니다.
- 기존 [viewer.html](../../../../src/main/resources/templates/pages/viewer.html)을 수정하거나 복제해 route
  화면으로 만들지 않습니다.
- S02~S05가 route JSON·content·feedback 계약을 제공합니다.

## 입력과 산출물

- 화면: `GET /ai-routes/{routeId}`
- 산출물: `AiRouteDetailPageController`, `pages/ai-route-detail.html`
- 산출물: `static/js/ai-route/route-detail-page.js`, route 순서·읽기 상태 helper
- Q03에 넘길 것: route 읽기 전체 브라우저 시나리오

## 수정 허용 파일

- 새 detail page Controller·template·AI detail JavaScript·필요 최소 CSS
- 기존 viewer·request-json은 공개 API만 재사용하고 파일 수정 금지
- 새 `AiRouteDetailPageTest`

## 구현 조건

1. GET detail의 position 순서와 current cost status·openedAt·completedAt을 표시합니다.
2. 첫 page는 기존 reading session POST, 다음 route page는 기존 current page PATCH로 먼저 엽니다.
3. open 성공 response의 viewerSessionId를 탭 `sessionStorage`에 저장하고 S04 POST header로 보냅니다.
4. S04 성공 뒤에만 item 완료 표시를 갱신하며 open/content 실패 시 낙관적 완료 처리하지 않습니다.
5. route 이전·다음은 추천 position, 원본 page 이동은 기존 viewer로 분리하고 비용을 사전 표시합니다.
6. 선수 page 건너뛰기는 안내 후 사용자가 계속할 수 있고 강제 순서 차단하지 않습니다.
7. current 지정·삭제는 S03, 완료 뒤 세 rating은 S05만 호출합니다.
8. 모든 문자열은 textContent/escaped output, 모든 변경 fetch는 공통 CSRF helper를 사용합니다.

## 테스트

- MockMvc owner route model·다른 독자 404·CSRF meta·module 경로
- route 순서와 원본 page 번호 분리, cost status별 버튼 label
- open success/fail × content success/fail DOM 상태, viewerSessionId header
- current PUT·delete 204 후 이동, 완료 전 feedback 비활성·완료 후 세 rating
- 목적·guide XSS와 CSRF 누락 실패 표시
- 수동 브라우저: 저장 route→1잉크 open→active rental 재열람→전체 완료→feedback
- 명령: `./gradlew test --tests '*AiRouteDetailPageTest'`

## 제외 범위

- 기존 viewer 내부에 route mode 추가
- route page 자동 open·강제 순서·독서 속도 학습
- 새 frontend framework·Node build

## 완료 조건

- 기존 과금 API가 성공하기 전 S04 content를 요청하지 않습니다.
- 실제 content 성공과 UI progress가 서버 openedAt과 일치합니다.
- 기존 viewer 회귀와 `./gradlew check`가 통과합니다.

## 인계

Q03에 전체 브라우저 흐름과 기존 viewer 회귀 지점을 전달합니다. W03은 이 template·script를 수정하지 않고
route 링크만 생성합니다.
