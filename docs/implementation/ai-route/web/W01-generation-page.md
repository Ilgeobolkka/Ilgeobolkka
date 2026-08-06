# W01 AI 경로 생성 화면

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 8 / 담당 B
- 선행: [G08 생성 API](../generation/G08-generation-api.md), [S01 generation 저장](../saved-route/S01-save-generation.md)
- 후속: [W03 도서·서재 연결](./W03-book-library-integration.md), [Q03 출시 회귀](../release/Q03-release-regression.md)

## 목표

로그인 독자가 한 도서의 목적·예산 또는 깊이를 입력하고 생성 상태를 polling해 ROUTE·NO_ROUTE를 확인한 뒤
검증된 generationId만 저장하는 Thymeleaf 화면을 만듭니다.

## 정본 링크

- [목표 HTML 화면](../../../api-spec.md#목표-html-화면)
- [독서 목적·예산 입력](../../../prd/ai-ink-route.md#독서-목적과-예산-입력)
- [저장과 생명주기](../../../prd/ai-ink-route.md#저장과-생명주기)
- [Thymeleaf·fetch 규칙](../../../conventions.md#thymeleaf-공통-셸과-브라우저-호출)
- 필수 시나리오: [T-AIR-001·013·018](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 공통 셸과 CSRF fetch는 [request-json.js](../../../../src/main/resources/static/js/common/request-json.js)에 있습니다.
- 기존 [book-detail.html](../../../../src/main/resources/templates/pages/book-detail.html)은 W03이 링크를 추가합니다.
- AI 생성 template·PageController·JavaScript는 없습니다.

## 입력과 산출물

- 화면: `GET /books/{bookId}/ai-route`
- 산출물: `AiRouteGenerationPageController`, `pages/ai-route-generation.html`
- 산출물: `static/js/ai-route/generation-page.js`, DOM 독립 상태·표시 helper
- W03/Q03에 넘길 것: 화면 경로, feature flag 조건과 수동 브라우저 시나리오

## 수정 허용 파일

- 새 page Controller·template·AI generation JavaScript·필요 최소 CSS
- 공통 shell·request-json은 호출만 하고 수정하지 않음
- 새 `AiRouteGenerationPageTest`

## 구현 조건

1. 비소장은 현재 balance로 기본값·5/10/15/전부 선택을 만들고 owned는 QUICK/BALANCED/DEEP만 표시합니다.
2. client length 검사는 보조일 뿐이며 server 200 code point·배타 입력 검증 오류를 그대로 표시합니다.
3. 생성 버튼의 새 실행마다 UUID key를 만들고 같은 실행 retry/polling에만 재사용합니다.
4. 201/200 ROUTE·NO_ROUTE, 202 GENERATING을 분기하고 polling은 GET generation만 호출합니다.
5. preview는 API의 page·relevance·prerequisite·role·estimatedMinutes·guide·cost status만 표시합니다.
6. purpose·guide·오류 message는 DOM `textContent` 또는 Thymeleaf escaped output만 사용합니다.
7. 저장 버튼은 generationId만 S01 endpoint로 보내며 page·순서·guide를 다시 전송하지 않습니다.
8. feature flag false면 PageController Bean이 없고 OpenAI 설정·분석 text를 HTML에 넣지 않습니다.

## 테스트

- MockMvc 인증·feature flag·bookId model·CSRF meta·module 경로
- 소장/비소장 입력 DOM과 201/200/202/NO_ROUTE fixture별 표시 helper
- HTML 모양 purpose·guide가 `innerHTML` 경로 없이 text로 표시되는지 정적/브라우저 확인
- UUID의 새 실행/동일 retry 재사용과 저장 request body 비어 있음 확인
- 수동 브라우저: 입력→202 polling→preview→저장, 두 NO_ROUTE, 429·503 오류
- 명령: `./gradlew test --tests '*AiRouteGenerationPageTest'`

## 제외 범위

- 도서 상세 링크·내 서재 표시
- 저장 route 읽기·삭제·feedback 화면
- streaming·비교 경로·새 frontend dependency

## 완료 조건

- 화면이 G08·S01 API 계약만 사용하고 공급자나 Entity를 알지 않습니다.
- 목적 XSS·CSRF·멱등 key lifecycle이 검증됩니다.
- `./gradlew check`가 통과합니다.

## 인계

W03에 생성 화면 route와 지원 도서 link 표시 조건을 전달합니다. Q03에 브라우저 fixture와 실패 표시
체크리스트를 전달합니다.
