# W03 도서 상세·내 서재 연결

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 9 / 담당 C
- 선행: [W01 생성 화면](./W01-generation-page.md), [S02 경로 조회](../saved-route/S02-route-query.md),
  [S03 현재·삭제](../saved-route/S03-current-delete.md)
- 후속: [Q03 출시 회귀](../release/Q03-release-regression.md)

## 목표

기존 도서 상세에는 지원·인증·feature 조건에 맞는 AI 생성 진입점을, 내 서재에는 저장 route와 현재 route
진입점을 외과적으로 연결합니다.

## 정본 링크

- [목표 HTML 화면 연결](../../../api-spec.md#목표-html-화면)
- [사용자와 지원 도서](../../../prd/ai-ink-route.md#사용자와-지원-도서)
- [저장과 생명주기](../../../prd/ai-ink-route.md#저장과-생명주기)
- [내 서재 사용자 흐름](../../../prd/user-flows.md#내-서재)

## 현재 구현 기준선

- [CommonPageController](../../../../src/main/java/com/example/ilgeobolkka/global/web/CommonPageController.java)가
  book detail·library template을 렌더링합니다.
- [book-detail.html](../../../../src/main/resources/templates/pages/book-detail.html)과
  [book-detail.js](../../../../src/main/resources/static/js/ownership/book-detail.js)는 소장 결제를 제공합니다.
- [LibraryFacade](../../../../src/main/java/com/example/ilgeobolkka/library/facade/LibraryFacade.java),
  [library.html](../../../../src/main/resources/templates/pages/library.html),
  [library.js](../../../../src/main/resources/static/js/library/library.js)는 기존 서재 기능을 소유합니다.

## 입력과 산출물

- 입력: book detail의 aiRouteSupported·로그인 상태·feature flag
- 입력: library book별 저장 route 요약과 current routeId
- 산출물: book detail 생성 링크와 비로그인 기능 설명
- 산출물: library route 목록/현재 route 표시와 W02 상세 링크

## 수정 허용 파일

- 기존 CommonPageController, Book detail response/assembler의 필요한 지원 flag 연결
- 기존 Library projection/DTO/Facade의 route 요약 연결
- `book-detail.html`·해당 page JS, `library.html`·해당 page JS의 최소 변경
- 새 `AiRouteBookLibraryIntegrationTest` 또는 기존 Book/Library 통합 테스트 보강

## 구현 조건

1. feature false면 기존 HTML·JSON에 AI 링크·빈 placeholder를 남기지 않습니다.
2. 지원 도서는 DB `ai_route_supported`로 판정하고 카테고리 이름·비소설 문자열로 추론하지 않습니다.
3. 비로그인은 기능 설명은 볼 수 있지만 생성 link는 login return flow로 연결하고 API를 호출하지 않습니다.
4. 로그인+지원 도서는 W01 route를, 미지원은 생성 control 없이 기존 상세를 유지합니다.
5. library는 기존 책 entry를 중복 만들지 않고 같은 book 안에 저장 route·current 표시를 추가합니다.
6. route 목적은 textContent/escaped output으로 표시하고 HTML attribute에 전문을 넣지 않습니다.
7. 기존 소장 결제·library 마지막 page·viewer 링크 동작을 변경하지 않습니다.

## 테스트

- feature on/off × 익명/로그인 × 지원/미지원 book의 링크·설명 matrix
- library의 route 0·1·여러 개, current 있음/없음과 W02 URL
- 다른 독자의 route가 book/library DTO에 없는지 확인
- 기존 소장 결제 button·last page·viewer link 회귀
- HTML 모양 purpose의 escaped 표시
- 명령: `./gradlew test --tests '*AiRouteBookLibraryIntegrationTest' --tests '*BookCatalogApiMySqlIntegrationTest' --tests '*LibraryApiMySqlIntegrationTest'`

## 제외 범위

- W01·W02 template/JavaScript 수정
- library 별도 AI 전용 API 신설
- route 자동 생성·추천·정렬 변경

## 완료 조건

- 기존 화면을 유지하면서 조건이 맞는 사용자에게만 AI 진입점이 연결됩니다.
- route owner 격리와 기존 결제·서재 회귀가 통과합니다.
- `./gradlew check`가 통과합니다.

## 인계

Q03에 feature·인증·지원 matrix와 기존 화면 회귀 테스트 명령을 전달합니다.
