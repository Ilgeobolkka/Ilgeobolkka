# API 계약

[PRD 색인](./prd/README.md)으로 돌아갑니다. 이 문서는 현재 합의된 HTTP 경로, 요청·응답과 오류 형식의
정본입니다. 제품 규칙은 [제품 정책](./prd/product-policy.md), 기능 범위는
[기능 요구사항](./prd/requirements.md)을 따릅니다.

## 계약 상태

- 인증은 Spring Security 서버 세션 쿠키를 사용합니다.
- 프런트엔드와 API는 같은 Spring Boot 실행물의 same-origin으로 제공됩니다.
- 화면과 결제 흐름은 데스크톱 브라우저만 지원합니다.
- 아래 표의 경로와 응답은 합의된 범위입니다.
- 결제는 PortOne V2 테스트 채널까지만 구현하며 운영 실결제는 활성화하지 않습니다.
- 아래 표의 초기 MVP와 [AI 잉크 경로 2차 MVP 구현 계약](#ai-잉크-경로-2차-mvp-구현-계약)의
  JSON API·HTML 화면은 모두 구현됐습니다. 도서 탐색·인증, 페이지 대여·열람 세션·콘텐츠, 내 서재,
  잉크·소장 결제와 조회, PortOne V2 테스트 결제·웹훅, AI 경로 생성·저장·열람·피드백 경계를 포함합니다.

## 공통 규칙

- JSON API는 `/api` 아래에 둡니다.
- 인증이 필요한 요청의 독자 식별자는 서버 세션의 `Principal`에서 얻고 요청 값으로 받지 않습니다.
- 상태 변경 요청은 CSRF 토큰을 검증합니다. 브라우저가 서버에 직접 제출하는 Thymeleaf 폼은 hidden 필드,
  JavaScript `fetch`는 서버 렌더링 페이지의 `_csrf`, `_csrf_header` meta 태그를 사용하며 별도 토큰 API는 제공하지 않습니다.
- `POST /api/webhooks/portone`만 브라우저 세션과 CSRF 대신 PortOne V2 웹훅 서명을 검증합니다.
- JSON 요청은 `Content-Type: application/json`을 사용하고 JSON 응답은 UTF-8로 인코딩합니다. 페이지 콘텐츠와
  바디가 없는 웹훅·smoke 응답은 예외입니다.
- 성공 응답은 공통 래퍼 없이 엔드포인트별 DTO를 JSON 최상위 바디로 반환합니다.
- 날짜와 시각은 UTC 기준 ISO 8601 문자열로 반환합니다.
- 공개 오류 코드는 예외 클래스 이름에서 만들지 않고 명시적인 값으로 관리합니다.
- `/api`와 `/api/**` 응답은 서버가 새로 발급한 UUID를 `X-Request-Id` 헤더로 반환합니다. 클라이언트가
  보낸 같은 이름의 헤더는 신뢰하지 않으며, 장애 문의에서는 이 값으로 서버 로그를 연결합니다.

### 페이지 범위와 정렬

- `GET /api/books`, `GET /api/ink/ledger`, `GET /api/ownership-payments`, `GET /api/ai-routes`의
  `page`는 필수인 1부터 시작하는 정수입니다.
- 정수가 아니거나 0 이하면 `400 INVALID_INPUT`입니다. 전체 범위를 초과한 양수는 `200 OK`와
  빈 배열을 반환합니다.
- 한 페이지는 10건으로 고정하고 클라이언트가 페이지 크기나 정렬을 지정하지 않습니다.
- 페이지 응답은 요청한 `page`, 전체 페이지 수 `totalPages`, 전체 건수 `totalCount`를 함께 반환합니다.
  전체 건수가 0이면 `totalPages=0`입니다.

## HTML 화면 경로

화면은 `/api`와 분리한 다음 same-origin 경로에서 Thymeleaf로 렌더링합니다. 화면 내부의 동적 요청은
아래 [엔드포인트](#엔드포인트)를 사용하며 HTML Controller가 REST Controller를 HTTP로 호출하지 않습니다.

| 메서드·경로 | 화면 | 인증 |
| --- | --- | --- |
| `GET /` | `GET /books`로 이동 | 아니오 |
| `GET /books` | 도서 목록·검색·페이지 이동 | 아니오 |
| `GET /books/{bookId}` | 도서 상세와 소장 결제 진입 | 선택 |
| `GET /signup` | 회원가입 | 아니오 |
| `GET /login` | 로그인 | 아니오 |
| `GET /books/{bookId}/viewer?page={page?}` | 선택 페이지의 현재 도서 뷰어 | 예 |
| `GET /ink` | 잉크 잔액·구매·내역 | 예 |
| `GET /ownership-payments` | 완료된 소장 결제 내역 | 예 |
| `GET /library` | 내 서재 | 예 |

- 인증이 필요한 HTML 경로에 로그인하지 않은 사용자가 접근하면 저장된 요청을 만들지 않고 `/login`으로
  이동합니다. 로그인·로그아웃 성공 뒤의 기본 이동 경로는 `/books`입니다.
- 뷰어의 `page`를 생략하면 1페이지를 선택합니다. 도서 상세는 사용자가 선택한 페이지를, 내 서재는 마지막
  열람 페이지를 전달합니다.
- 뷰어는 도서 상세나 내 서재에서, 소장 결제는 도서 상세에서 진입합니다. 전역 내비게이션에는 도서 탐색,
  인증, 잉크, 소장 결제 내역과 내 서재만 둡니다.
- 잉크와 소장 결제 결과는 결제를 시작한 `/ink` 또는 `/books/{bookId}`에서 표시하며 별도 HTML 결과
  경로를 만들지 않습니다.

## 엔드포인트

| 메서드·경로 | 요청 | 성공 | 응답의 주요 필드 | 인증 |
| --- | --- | --- | --- | --- |
| `POST /api/auth/signup` | `email`, `password` | 201 | `readerId`, `email` | 아니오 |
| `POST /api/auth/login` | `email`, `password` | 200 | `readerId`, `email` | 아니오 |
| `POST /api/auth/logout` | 없음 | 200 | `readerId` | 예 |
| `GET /api/smoke` | 없음 | 204 | 없음 | 아니오 |
| `GET /api/books?page={page}&keyword={keyword?}` | 쿼리 파라미터 | 200 | `books[]`, `page`, `totalPages`, `totalCount` | 아니오 |
| `GET /api/books/{bookId}` | 경로 파라미터 | 200 | 도서 기본 정보, `owned` | 선택 |
| `POST /api/books/{bookId}/reading-sessions` | `pageNumber` | 201 | 세션과 페이지 열기 결과 | 예 |
| `PATCH /api/reading-sessions/current/page` | 헤더 `X-Viewer-Session-Id`, 바디 `pageNumber` | 200 | 페이지 열기 결과 | 예 |
| `GET /api/reading-sessions/current/pages/{pageNumber}/content` | 헤더 `X-Viewer-Session-Id`, 경로 파라미터 | 200 | 요청 페이지 텍스트 또는 이미지 | 예 |
| `GET /api/library` | 없음 | 200 | `entries[]` | 예 |
| `GET /api/ink/balance` | 없음 | 200 | `balance` | 예 |
| `GET /api/ink/ledger?page={page}` | 쿼리 파라미터 | 200 | `entries[]`, `page`, `totalPages`, `totalCount` | 예 |
| `POST /api/ink/purchases` | 없음 | 201 | 결제 준비 정보 | 예 |
| `POST /api/ink/purchases/{paymentId}/complete` | 경로 파라미터 | 200·202 | `paymentId`, `status`, `grantedInk`, `inkBalance` | 예 |
| `POST /api/books/{bookId}/ownership-payments` | 경로 파라미터 | 200·201 | 결제 준비 정보 | 예 |
| `POST /api/ownership-payments/{paymentId}/complete` | 경로 파라미터 | 200·202 | `paymentId`, `status`, `bookId`, `owned` | 예 |
| `GET /api/ownership-payments?page={page}` | 쿼리 파라미터 | 200 | `payments[]`, `page`, `totalPages`, `totalCount` | 예 |
| `POST /api/webhooks/portone` | PortOne V2 웹훅 바디·서명 헤더 | 200 | 없음 | 웹훅 서명 |

## AI 잉크 경로 2차 MVP 구현 계약

이 절은 [AI 잉크 경로 PRD](./prd/ai-ink-route.md)를 구현한 현재 HTTP 계약입니다. 인증 독자는 항상 서버
세션의 `Principal`에서 식별합니다.

### HTML 화면

| 메서드·경로 | 화면 | 인증 |
| --- | --- | --- |
| `GET /books/{bookId}/ai-route` | 독서 목적·예산 또는 깊이 입력과 임시 경로 미리보기 | 예 |
| `GET /ai-routes/{routeId}` | 저장 경로 상세·경로 순서 열람·피드백 | 예 |

도서 상세는 지원 도서에만 AI 경로 화면 링크를 표시하고 내 서재는 저장 경로와 현재 경로를 함께 표시합니다.
다른 독자의 `routeId`나 `generationId`는 존재 여부를 구분하지 않고 `404 RESOURCE_NOT_FOUND`로 응답합니다.

### JSON 엔드포인트

| 메서드·경로 | 요청 | 성공 | 응답 | 목적 |
| --- | --- | --- | --- | --- |
| `POST /api/books/{bookId}/ai-route-generations` | UUID `Idempotency-Key` 헤더, 생성 입력 | 200·201·202 | 생성 결과 | 새 임시 경로 생성 또는 같은 요청 재조회 |
| `GET /api/ai-route-generations/{generationId}` | 경로 파라미터 | 200·202 | 생성 결과 | 소유자의 유효한 임시 상태·결과 조회 |
| `POST /api/ai-route-generations/{generationId}/routes` | 바디 없음 | 200·201 | 저장 경로 상세 | 임시 결과를 한 번 저장하고 현재 경로로 지정 |
| `GET /api/ai-routes?page={page}` | 공통 `page` 쿼리 파라미터 | 200 | `routes[]`, 페이지 정보 | 소유자의 저장 경로를 `createdAt DESC, id DESC`로 조회 |
| `GET /api/ai-routes/{routeId}` | 경로 파라미터 | 200 | 저장 경로 상세 | 소유자의 경로·진행·현재 비용 조회 |
| `PUT /api/books/{bookId}/ai-routes/current` | `routeId` | 200 | 저장 경로 상세 | 같은 책의 저장 경로를 현재 경로로 지정 |
| `POST /api/ai-routes/{routeId}/pages/{pageNumber}/content` | `X-Viewer-Session-Id` 헤더 | 200 | 기존 페이지 콘텐츠 | 기존 페이지 열기 성공 뒤 콘텐츠를 제공하고 경로 항목을 열람 완료로 기록 |
| `PUT /api/ai-routes/{routeId}/feedback` | `rating` | 200 | `routeId`, `rating`, `feedbackAt` | 완료 경로의 선택형 피드백 생성·변경 |
| `DELETE /api/ai-routes/{routeId}` | 경로 파라미터 | 204 | 없음 | 경로·항목·진행·피드백 삭제 |

임시 결과는 목록 API를 제공하지 않습니다.

경로 페이지 열기는 새 과금 API를 만들지 않고 기존 페이지 열기 유스케이스를 사용합니다. 새 뷰어에서 첫
경로 페이지를 열 때는 `POST /api/books/{bookId}/reading-sessions`, 현재 뷰어에서 다음 경로 페이지로
이동할 때는 `PATCH /api/reading-sessions/current/page`에 대상 `pageNumber`를 보냅니다. 이 요청이 신규
대여·잉크 차감과 현재 열람 세션 위치 변경을 원자적으로 완료한 뒤, 응답의 `viewerSessionId`를
`X-Viewer-Session-Id`로 보내 경로 페이지 콘텐츠를 조회합니다.

경로 페이지 콘텐츠 `POST`는 경로 소유권·항목 포함 여부, 현재 열람 세션 위치와 기존 소장·활성 대여 권한을
다시 검증하고 콘텐츠 제공과 `openedAt`·경로 완료 상태 기록을 함께 완료합니다. 이 `POST`는 공통 규칙에
따라 CSRF 토큰을 검증하되 잉크를 차감하거나 대여·열람 세션·서재 위치를 생성·변경하지 않습니다. 페이지
열기 요청이 실패했거나 열기 뒤 콘텐츠 요청 전에 권한이 만료되면 콘텐츠와 진행 상태를 제공하거나 변경하지
않습니다.

### 생성 입력과 결과

생성 요청 바디는 다음 공통 필드와 서로 배타적인 입력 한 종류를 사용합니다.

| 필드 | 형식 | 규칙 |
| --- | --- | --- |
| `purpose` | 문자열 | [독서 목적 입력 계약](./prd/ai-ink-route.md#독서-목적)에 맞게 서버가 정규화·검증 |
| `maxAdditionalInk` | 정수 또는 `null` | 비소장 도서에서만 사용하며 [비소장 예산 정책](./prd/ai-ink-route.md#비소장-도서)을 따름 |
| `depth` | 문자열 또는 `null` | 소장 도서에서만 `QUICK`, `BALANCED`, `DEEP` 중 하나 |

`maxAdditionalInk`와 `depth`를 함께 보내거나 대상 도서의 소장 상태와 맞지 않으면 `400 INVALID_INPUT`입니다.
같은 독자·`Idempotency-Key`에 도서·콘텐츠 버전·정규화한 목적·예산 또는 깊이가 다르면
`409 AI_ROUTE_IDEMPOTENCY_KEY_REUSED`입니다.

생성 결과는 다음 필드를 생략하지 않습니다.

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| `generationId` | UUID | 서버가 발급한 임시 결과 식별자 |
| `status` | 문자열 | `GENERATING`, `ROUTE`, `NO_ROUTE`, `SAVED` 중 하나 |
| `bookId`, `contentVersion` | 숫자, 문자열 | 생성에 사용한 도서와 콘텐츠 버전 |
| `purpose` | 문자열 | 서버가 정규화한 독서 목적 |
| `expiresAt` | UTC 시각 또는 `null` | `GENERATING`에서는 `null`, 그 밖에는 임시 상태·결과 만료 시각 |
| `routeId` | 숫자 또는 `null` | `SAVED`에서만 저장 경로 식별자 |
| `remainingDailyGenerations` | 정수 | 응답 시점의 남은 계정별 생성 횟수 |
| `noRouteReason` | 문자열 또는 `null` | `NO_ROUTE`에서만 `NO_RELEVANT_PAGES`, `INSUFFICIENT_BUDGET`, `INSUFFICIENT_DEPTH` 중 하나 |
| `minimumRequiredInk` | 정수 또는 `null` | 예산 부족 `NO_ROUTE`에서만 값이 있음 |
| `items` | 배열 | `ROUTE`에서만 경로 항목, 그 밖에는 빈 배열 |

각 `items[]`는 `position`, `pageNumber`, `relevance`, `prerequisite`, `role`, `estimatedMinutes`,
`guide`, `additionalCostStatus`를 포함합니다. `relevance`는 `HIGH`·`MEDIUM`, `role`은
`PREREQUISITE`·`CORE`·`EXAMPLE`·`COUNTERPOINT`·`CONCLUSION`, `additionalCostStatus`는
`ONE_INK`·`ACTIVE_RENTAL`·`OWNED` 중 하나입니다. 임시 결과는 접근권한이나 잉크 차감을 만들지 않습니다.
생성 결과의 `additionalCostStatus`는 생성 시점 값이며, `GET /api/ai-route-generations/{generationId}`로
다시 조회해도 현재 권한으로 재계산하지 않습니다(저장 거부 뒤 재조회도 같습니다). 재계산은 저장 경로 상세
조회에서만 수행합니다.

`NO_RELEVANT_PAGES`와 `INSUFFICIENT_DEPTH`는 `minimumRequiredInk=null`이고,
`INSUFFICIENT_BUDGET`은 선택한 예산보다 큰 최소 추가 잉크를 `minimumRequiredInk`로 반환합니다.
`INSUFFICIENT_DEPTH`는 소장 경로의 후보와 선수 폐쇄를 선택한 깊이 상한에 담을 수 없다는 뜻입니다.
`NO_ROUTE`가 아닌 상태에서는 두 필드가 모두 `null`입니다.

새 요청이 완료되면 `201`, 같은 멱등 요청의 완료 결과를 반환하면 `200`, 먼저 시작한 같은 요청이 아직
진행 중이면 `202`와 `GENERATING`을 반환합니다. `NO_ROUTE`는 오류가 아닌 정상 결과입니다. 실패한 키는
보관 기간 동안 재실행하지 않고 최초 오류를 재현합니다. 만료 정리 뒤에는 과거 키 사용 여부를 보존하지 않아
같은 키도 새 요청으로 처리되지만 클라이언트는 새 실행마다 새 키를 사용합니다. 보관 기간 안에 경로로 저장한
키의 재조회는 `SAVED`와 `routeId`를 반환하며 경로를 삭제한 뒤에는 `409 AI_ROUTE_GENERATION_CONSUMED`입니다.

### 저장 경로 결과와 상태 변경

저장 경로 상세는 생성 결과의 도서·목적·항목에 `routeId`, `current`, `createdAt`, `completedAt`,
`rating`을 더합니다. 각 항목은 현재 권한으로 다시 계산한 `additionalCostStatus`와 콘텐츠 제공 성공 시각인
`openedAt`을 포함합니다. 저장 뒤 페이지와 순서는 바꾸지 않습니다.

저장 성공은 새 경로를 만들면 `201`, 이미 소비한 `generationId`의 재시도면 기존 경로와 `200`입니다.
현재 경로 지정·삭제는 같은 독자·도서 단위로 직렬화하며 삭제 뒤 현재 경로 재지정은
[저장과 생명주기](./prd/ai-ink-route.md#저장과-생명주기)를 따릅니다. 경로 페이지 콘텐츠 `POST`만
`openedAt`과 경로 완료 상태를 기록하며 생성·미리보기·저장·상세 조회는 진행을 바꾸지 않습니다.

저장 시점에 현재 대여·소장 권한으로 다시 계산한 추가 잉크가 임시 결과의 생성 예산을 넘으면
([저장과 생명주기 3단계](./prd/ai-ink-route.md#저장과-생명주기)) `409 AI_ROUTE_ENTITLEMENT_CHANGED`로
거부합니다. 이때 `generationId`는 **소비하지 않습니다.** 경로·현재 경로를 만들지 않고 임시 결과는 원래
만료 시각까지 `ROUTE` 상태로 남아 다시 조회할 수 있습니다. 저장은 매 요청마다 그 시점의 권한으로 추가
잉크를 다시 계산하므로 권한이 그대로인 재시도는 같은 오류를 반환하며, 최초 거부를 기록해 이후 저장을 막는
상태는 두지 않습니다. 따라서 만료 전 권한이 예산 이하로 회복되면 같은 `generationId` 저장이 성공할 수
있지만, 이는 재계산의 결과일 뿐 클라이언트에 안내하는 복구 경로가 아닙니다.

독자에게는 대여·소장 상태가 바뀌어 필요한 잉크가 생성 시점보다 늘었으므로 **경로를 다시 생성해야 한다고만
안내합니다**(화면 조건은 [W01](./implementation/ai-route/web/W01-generation-page.md)). 이 응답에는 재계산한
비용·권한 상세를 포함하지 않습니다. 콘텐츠 버전 변경(`AI_ROUTE_CONTENT_CHANGED`), 입력
오류(`INVALID_INPUT`), 잉크 부족(`INSUFFICIENT_INK`)은 원인이 다르므로 이 상황에 대신 사용하지 않습니다.

피드백 `rating`은 `HELPFUL`, `NEUTRAL`, `NOT_HELPFUL` 중 하나입니다. 완료하지 않았거나 소유하지 않은
경로에는 저장하지 않습니다.

### 오류

| 상황 | HTTP 상태 | 코드 |
| --- | --- | --- |
| 멱등 키 형식 오류·생성 입력 조합 오류 | 400 | `INVALID_INPUT` |
| 미지원 도서·외부 전송 권리·데이터 정책 프로필 미충족 | 422 | `AI_ROUTE_NOT_SUPPORTED` |
| 같은 멱등 키의 다른 입력·저장 전 콘텐츠 버전 변경 | 409 | `AI_ROUTE_IDEMPOTENCY_KEY_REUSED`, `AI_ROUTE_CONTENT_CHANGED` |
| 저장 뒤 경로를 삭제한 생성 결과 재사용 | 409 | `AI_ROUTE_GENERATION_CONSUMED` |
| 저장 전 권한 변동으로 추가 잉크가 생성 예산 초과 | 409 | `AI_ROUTE_ENTITLEMENT_CHANGED` |
| 미완료 경로에 피드백 시도 | 409 | `AI_ROUTE_NOT_COMPLETED` |
| 만료한 임시 결과 | 404 | `RESOURCE_NOT_FOUND` |
| 계정별 생성 횟수 초과 | 429 | `AI_ROUTE_DAILY_LIMIT_EXCEEDED` |
| OpenAI 지출·사용량 한도 또는 크레딧 소진 | 503 | `AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE` |
| OpenAI 일시 오류·출력 검증 최종 실패·전체 시간 제한 | 503 | `AI_ROUTE_PROVIDER_UNAVAILABLE`, `AI_ROUTE_INVALID_OUTPUT`, `AI_ROUTE_GENERATION_TIMEOUT` |

`AI_ROUTE_CONTENT_CHANGED`는 2차 MVP에서 `contentVersion`을 재발급하지 않으므로
([재평가와 지원 활성화 순서](./prd/ai-ink-route.md#재평가와-지원-활성화-순서)) 도달 가능한 사용자
경로가 없습니다. 운영자 오적재를 저장 시점에 탐지하는 불변식 방어 검사로 계약을 유지하며, 일반 사용자
흐름으로 설계하거나 화면 안내를 만들지 않습니다.

`429`는 PRD가 정한 다음 초기화 시각까지의 `Retry-After`를 포함합니다. 외부 오류 응답에는 공급자 조직·
프로젝트·비용, 프롬프트, 분석 텍스트와 응답 원문을 포함하지 않습니다. `AI_ROUTE_ENABLED=false`이면 AI
HTML·JSON 경로를 등록하지 않으며 기존 도서·뷰어·결제 기능은 계속 제공합니다.

## 초기 MVP 기대 응답 형태

아래 JSON은 필드 이름, 중첩 구조와 `null` 가능성을 보여 주는 예시입니다. ID와 시각 등 값 자체를
고정하지 않으며, 오류 응답은 [오류 응답](#오류-응답)의 공통 형태를 사용합니다.

성공 JSON은 아래에 정의한 필드를 생략하지 않습니다. 값이 없는 nullable 필드는 빈 문자열이나 0으로
바꾸지 않고 JSON `null`로 반환합니다.

- 도서 목록·상세·내 서재의 `coverImagePath`와 도서 상세의 `description`은 메타데이터가 없으면 `null`
- 익명 도서 상세의 `owned`는 `null`
- 소장 도서 페이지 열기의 `rentedAt`, `expiresAt`은 `null`
- 소장 도서 서재 항목의 `rentedAt`, `expiresAt`, `activeRental`은 `null`
- 잉크 지급 내역의 `bookTitle`, `pageNumber`, `rentedAt`, `expiresAt`은 `null`

#### 인증

`POST /api/auth/signup`의 `201 Created`와 `POST /api/auth/login`의 `200 OK`는 같은 형태입니다.

```json
{
  "readerId": 1,
  "email": "reader@example.com"
}
```

`POST /api/auth/logout`의 `200 OK`는 무효화하기 전에 식별한 독자를 반환합니다.

```json
{
  "readerId": 1
}
```

#### 부팅 smoke

`GET /api/smoke`는 애플리케이션의 HTTP 요청 처리를 확인하기 위한 비민감 경로입니다. 정상 응답은
`204 No Content`이며 바디, DB 상태, 설정값과 내부 경로를 반환하지 않습니다. DB 연결과 Flyway·JPA 검증은
이 경로를 호출하기 전에 완료되는 애플리케이션 기동 과정에서 확인합니다.

#### 도서 목록과 상세

`GET /api/books?page=1`의 `200 OK`:

```json
{
  "books": [
    {
      "bookId": 1,
      "category": "소설",
      "coverImagePath": "/assets/covers/book-1.jpg",
      "title": "샘플 도서",
      "author": "샘플 작가",
      "bookPrice": 12000
    }
  ],
  "page": 1,
  "totalPages": 10,
  "totalCount": 100
}
```

`GET /api/books/1`의 `200 OK`:

```json
{
  "bookId": 1,
  "category": "소설",
  "coverImagePath": "/assets/covers/book-1.jpg",
  "title": "샘플 도서",
  "author": "샘플 작가",
  "description": "도서 소개입니다.",
  "totalPageCount": 240,
  "bookPrice": 12000,
  "owned": false
}
```

익명 상세 조회에서는 `owned`만 `null`이고 나머지 형태는 같습니다.

#### 페이지 열기와 콘텐츠

`POST /api/books/1/reading-sessions`의 `201 Created`와
`PATCH /api/reading-sessions/current/page`의 `200 OK`는 같은 형태입니다. 아래는 새 대여를 만든 경우입니다.

```json
{
  "viewerSessionId": "0a7b39a9-a4a8-4dd9-94df-14933c378047",
  "bookId": 1,
  "pageNumber": 12,
  "owned": false,
  "deductedInk": 1,
  "inkBalance": 99,
  "rentedAt": "2026-07-27T10:00:00Z",
  "expiresAt": "2026-08-26T10:00:00Z",
  "contentType": "TEXT"
}
```

활성 대여 재사용은 `deductedInk=0`과 기존 대여 시각을, 소장 도서는 `owned=true`, `deductedInk=0`,
`rentedAt=null`, `expiresAt=null`을 반환합니다.

`GET /api/reading-sessions/current/pages/12/content`의 `200 OK`는 JSON이 아닙니다.

- `TEXT`: `Content-Type: text/plain;charset=UTF-8`와 UTF-8 텍스트 바디
- `IMAGE`: `Content-Type: image/jpeg` 또는 `image/png`와 해당 이미지 바이트

#### 내 서재

`GET /api/library`의 `200 OK`:

```json
{
  "entries": [
    {
      "bookId": 1,
      "coverImagePath": "/assets/covers/book-1.jpg",
      "title": "대여 중인 도서",
      "category": "소설",
      "lastPageNumber": 12,
      "rentedAt": "2026-07-27T10:00:00Z",
      "expiresAt": "2026-08-26T10:00:00Z",
      "activeRental": true,
      "owned": false
    },
    {
      "bookId": 2,
      "coverImagePath": "/assets/covers/book-2.jpg",
      "title": "소장한 도서",
      "category": "에세이",
      "lastPageNumber": 1,
      "rentedAt": null,
      "expiresAt": null,
      "activeRental": null,
      "owned": true
    }
  ]
}
```

#### 잉크 잔액과 내역

`GET /api/ink/balance`의 `200 OK`:

```json
{
  "balance": 99
}
```

`GET /api/ink/ledger?page=1`의 `200 OK`:

```json
{
  "entries": [
    {
      "type": "DEDUCTION",
      "amount": 1,
      "balanceAfter": 99,
      "bookTitle": "샘플 도서",
      "pageNumber": 12,
      "rentedAt": "2026-07-27T10:00:00Z",
      "expiresAt": "2026-08-26T10:00:00Z",
      "occurredAt": "2026-07-27T10:00:00Z"
    },
    {
      "type": "GRANT",
      "amount": 100,
      "balanceAfter": 100,
      "bookTitle": null,
      "pageNumber": null,
      "rentedAt": null,
      "expiresAt": null,
      "occurredAt": "2026-07-27T09:00:00Z"
    }
  ],
  "page": 1,
  "totalPages": 1,
  "totalCount": 2
}
```

#### 결제 준비

`POST /api/ink/purchases`와 `POST /api/books/1/ownership-payments`는 대상과 `orderName`,
`totalAmount`만 다르고 같은 준비 응답을 사용합니다. 새 시도는 `201 Created`, 기존 소장 `PENDING` 재사용은
`200 OK`입니다. 소장 결제의 `orderName`은 `읽어볼까 도서 소장`, `totalAmount`는 대여 이력과
잉크 잔액을 반영하지 않은 도서 원가 전액입니다.

```json
{
  "paymentId": "f3d40d77-84d8-4a4f-b6a3-a54ebd70cf4c",
  "storeId": "store-example",
  "channelKey": "channel-key-example",
  "orderName": "읽어볼까 100잉크",
  "totalAmount": 1000,
  "currency": "CURRENCY_KRW"
}
```

#### 잉크 결제 완료

`POST /api/ink/purchases/{paymentId}/complete`가 검증된 결제를 처음 반영하거나 이미 처리한 성공을
재사용한 `200 OK`:

```json
{
  "paymentId": "f3d40d77-84d8-4a4f-b6a3-a54ebd70cf4c",
  "status": "PAID",
  "grantedInk": 100,
  "inkBalance": 199
}
```

아직 완료되지 않은 결제의 `202 Accepted`:

```json
{
  "paymentId": "f3d40d77-84d8-4a4f-b6a3-a54ebd70cf4c",
  "status": "PENDING",
  "grantedInk": 0,
  "inkBalance": 99
}
```

#### 소장 결제 완료와 내역

`POST /api/ownership-payments/{paymentId}/complete`가 검증된 결제를 처음 반영하거나 이미 처리한 성공을
재사용한 `200 OK`:

```json
{
  "paymentId": "8e68dfba-1327-4bb1-8b88-3d6046367012",
  "status": "PAID",
  "bookId": 1,
  "owned": true
}
```

아직 완료되지 않은 결제의 `202 Accepted`는 같은 형태에서 `status="PENDING"`, `owned=false`입니다.

`GET /api/ownership-payments?page=1`의 `200 OK`:

```json
{
  "payments": [
    {
      "paymentId": "8e68dfba-1327-4bb1-8b88-3d6046367012",
      "bookId": 1,
      "bookTitle": "샘플 도서",
      "amountWon": 12000,
      "paidAt": "2026-07-27T11:00:00Z",
      "owned": true
    }
  ],
  "page": 1,
  "totalPages": 1,
  "totalCount": 1
}
```

#### PortOne 웹훅

`POST /api/webhooks/portone`의 정상 `200 OK`는 바디를 반환하지 않습니다. 서명 실패와 일시 오류는
각각 `400 INVALID_WEBHOOK_SIGNATURE`와 [오류 응답](#오류-응답)에서 정의한 `5xx` 정책을 따릅니다.

### 회원가입과 로그인

- 회원가입과 로그인 바디의 `email`, `password`는 필수 문자열입니다. 익명 사용자의 상태 변경
  요청이어도 서버가 렌더링한 화면의 CSRF 토큰을 함께 보내야 합니다.
- 회원가입은 정규화한 이메일과 0잉크 계좌를 같은 트랜잭션에서 생성하고 `201 Created`를
  반환하지만 로그인 세션은 만들지 않습니다. 응답의 `email`은 정규화된 값입니다.
- 정규화한 이메일은 비어 있지 않은 일반 이메일 형식과 최대 255자를 만족해야 합니다. 비밀번호는
  [계정과 인증 정책](./prd/product-policy.md#계정과-인증)의 길이·조합 규칙을 그대로 적용합니다.
  입력 규칙 위반은 `400 INVALID_INPUT`, 중복 이메일은 `409 EMAIL_ALREADY_EXISTS`입니다.
- 로그인 성공은 세션 ID를 교체하고 `200 OK`를 반환합니다. 존재하지 않는 이메일과 잘못된
  비밀번호는 모두 `401 INVALID_CREDENTIALS`와 같은 공개 메시지를 사용합니다.
- 로그아웃은 응답용 `readerId`를 확보한 뒤 로그인 세션·세션 쿠키와 현재 `ReadingSession`을
  함께 무효화하고 `200 OK`를 반환합니다.

### 도서 목록과 상세

도서 목록의 `books[]`는 다음 필드를 제공합니다.

- `bookId`, `category`, `coverImagePath`, `title`, `author`, `bookPrice`

도서 상세는 다음 필드를 제공합니다.

- `bookId`, `category`, `coverImagePath`, `title`, `author`, `description`
- `totalPageCount`, `bookPrice`
- 로그인하지 않은 경우 `owned`는 `null`, 로그인한 경우 소장 여부는 `true` 또는 `false`
- AI 경로 feature가 활성화되면 지원 여부 `aiRouteSupported`는 `true` 또는 `false`,
  비활성화되면 응답에서 생략

`bookPrice`는 원화 단위의 0보다 큰 정수입니다. `coverImagePath`의 문자열 값은 공개 표지 자산의
same-origin 경로이며 원본 PDF 경로나 비공개 페이지 이미지 저장소 주소가 아닙니다. 상세 조회 시 인증된
세션이 있으면 `owned`를 계산하고, 없으면 `null`로 응답합니다.

목록과 검색은 한 페이지에 10권을 제공합니다. `page`는 1부터 시작하며 0 이하는
`400 INVALID_INPUT`, 전체 범위를 초과한 양수는 `200 OK`와 빈 `books`를 반환합니다. 검색어는 앞뒤
공백만 제거하고 내부 공백은 보존하며, 빈 값은 전체 목록으로 처리합니다. 제목·저자 부분 일치 검색은
영문 대소문자를 구분하지 않고 `%`, `_`는 SQL 와일드카드가 아닌 일반 문자로 처리합니다.

정렬은 `category ASC, title ASC, id ASC`로 고정합니다.

### 페이지 열기

세션 생성과 페이지 이동은 같은 페이지 열기 유스케이스를 호출합니다. 응답은 다음 필드를 제공합니다.

- `viewerSessionId`, `bookId`, `pageNumber`
- `owned`, `deductedInk`, `inkBalance`
- `rentedAt`, `expiresAt`, `contentType`

- `pageNumber`는 1 이상인 정수입니다. 0 이하는 `400 INVALID_INPUT`, 도서의
  `totalPageCount`를 넘거나 실제 `BookPage`가 없으면 `404 RESOURCE_NOT_FOUND`입니다.
- `contentType`은 `TEXT` 또는 `IMAGE`, `deductedInk`는 이 요청에서 차감한 수량인 0 또는 1,
  `inkBalance`는 처리 후 현재 잔액입니다. 이 응답에 페이지 본문이나 저장소 경로는 넣지 않습니다.
- 온라인 소장 도서는 `owned=true`, `deductedInk=0`이고 `rentedAt`, `expiresAt`은 `null`입니다.
  활성 대여 재사용은 `owned=false`, `deductedInk=0`과 기존 대여 시각을 반환합니다. 새 대여는
  `owned=false`, `deductedInk=1`과 새 대여 시각을 반환합니다.
- 새 뷰어의 `ReadingSession` 교체, 잉크 차감·원장·대여, `LibraryEntry` 생성·최종 위치
  갱신은 페이지 열기가 성공했을 때만 커밋합니다. 잉크 부족 또는 다른 실패에서는 기존
  `ReadingSession`을 포함한 상태를 바꾸지 않습니다.

새 뷰어를 열면 서버가 UUID `viewerSessionId`를 발급해 독자당 하나인 현재 `ReadingSession`을
교체합니다. 브라우저는 이 값을 탭별 `sessionStorage`에 보관하고 이후 요청의
`X-Viewer-Session-Id` 헤더로 전달합니다. 이 값은 뷰어 교체 판정용이며 인증 자격 증명이 아닙니다.

`X-Viewer-Session-Id`가 누락되거나 UUID 형식이 아니면 `400 INVALID_INPUT`, 현재 열람 세션이
없으면 `404 RESOURCE_NOT_FOUND`, 유효한 헤더 값이 현재 세션과 다르면 `409 VIEWER_SESSION_REPLACED`로
거부합니다. 잉크가 부족하면 `422 INSUFFICIENT_INK`를 반환하고 상태와 콘텐츠를 제공하지
않습니다.

### 페이지 콘텐츠

- 현재 서버 세션, `X-Viewer-Session-Id`, `ReadingSession`의 현재 페이지와 경로의 `pageNumber`, 요청
  페이지의 온라인 소장 또는 활성 대여를 함께 검증합니다.
- `TEXT`는 UTF-8 텍스트, `IMAGE`는 실제 `image/jpeg` 또는 `image/png` 바이트를 반환합니다.
- `TEXT`의 `Content-Type`은 `text/plain;charset=UTF-8`, `IMAGE`의 `Content-Type`은 저장한 형식과 일치하는
  `image/jpeg` 또는 `image/png`입니다.
- 한 요청에는 현재 원본 PDF 페이지와 연결된 콘텐츠 하나만 반환합니다.
- 원본 PDF 경로, S3 버킷·객체 키와 다른 페이지 위치를 응답에 포함하지 않습니다.
- `Cache-Control: private, no-store`를 사용합니다.
- 콘텐츠 `GET`은 잉크를 차감하거나 대여·열람 세션·서재 위치를 생성·갱신하지 않습니다.
  경로의 페이지가 현재 세션 위치와 다르거나, 페이지 열기 후 콘텐츠 요청 전에 대여가
  만료되는 등 소장·대여 권한이 없으면 `403 ACCESS_DENIED`입니다.

### 내 서재와 잉크 내역

내 서재의 `entries[]`는 `bookId`, `coverImagePath`, `title`, `category`, `lastPageNumber`, `rentedAt`,
`expiresAt`, `activeRental`, `owned`를 제공합니다. 대여 정보는 마지막 열람 페이지 한 건만 나타내며 전체
대여 페이지 목록은 반환하지 않습니다. 동일 페이지를 여러 번 대여했다면 `rentedAt DESC, id DESC`의
첫 대여를 사용해 현재 활성 여부를 서버 시각으로 계산합니다.

AI 경로 feature가 활성화되면 각 항목에 `routes[]`(저장 시각·ID 내림차순의 `routeId`,
`purpose`)와 선택 필드 `currentRouteId`를 추가합니다. 현재 경로가 없으면 `currentRouteId`는
응답에서 생략합니다. feature가 비활성화되면 두 필드와 AI 경로만 저장한 책 항목을 모두
생략해 기존 서재 응답을 유지합니다. `purpose`는 HTML로 해석하지 않고 텍스트로 표시합니다.

페이지를 아직 열지 않고 AI 경로만 저장한 책도 feature 활성 서재에 책당 한 항목으로
표시합니다. 이때 첫 뷰어 진입을 위해 `lastPageNumber=1`이고 `owned=false`, `rentedAt`,
`expiresAt`, `activeRental`은 `null`입니다. 이 값은 1페이지를 이미 읽었다는 의미가 아니며,
화면은 `AI 경로 저장`과 `첫 페이지 읽기`로 구분해 표시합니다. 서재 저장 위치를 생성하지
않고 경로의 최신 `createdAt DESC`와 `bookId DESC`를 이 항목의 서재 정렬 기준으로 사용합니다.

온라인 소장 도서는 소장 전 대여 이력이 있어도 `owned=true`이고 `rentedAt`, `expiresAt`,
`activeRental`은 `null`입니다. 열람 이력 없이 소장으로 처음 서재에 추가된 도서는 `lastPageNumber=1`로
시작합니다. 서재 목록은 최근에 갱신된 항목부터 `updatedAt DESC, id DESC`로 정렬합니다.

`GET /api/ink/balance`는 현재 독자의 0 이상인 `balance`를 반환합니다.

잉크 내역은 한 페이지에 10개를 `occurredAt DESC, id DESC`로 제공합니다. 각 항목은 `type`, `amount`,
`balanceAfter`, `bookTitle?`, `pageNumber?`, `rentedAt?`, `expiresAt?`, `occurredAt`을 포함합니다.
`type`은 `GRANT` 또는 `DEDUCTION`이고 `amount`는 부호 없는 변경량이므로 각각 100 또는 1입니다.
`GRANT`의 도서·페이지·대여 필드는 `null`, `DEDUCTION`의 해당 필드는 연결된 대여 값입니다.

### PortOne V2 테스트 결제

잉크 이용권 결제와 온라인 소장 결제는 서로 다른 준비·완료 API와 테이블을 사용합니다. 두 준비 API는
서버가 애플리케이션 전체에서 고유한 UUID `paymentId`로 `PENDING` 시도를 만든 뒤 다음 공통 필드를
반환합니다.

- `paymentId`, `storeId`, `channelKey`, `orderName`, `totalAmount`, `currency="CURRENCY_KRW"`

브라우저는 이 값으로 PortOne V2 `PortOne.requestPayment`를 호출합니다. `storeId`와 `channelKey`는
환경별 공개 설정이며, PortOne API secret과 웹훅 secret은 서버에만 둡니다. MVP의 `channelKey`는
PortOne 테스트 채널만 가리키며 운영 실결제 채널은 설정하지 않습니다. 결제 수단은 응답 필드에 넣지
않고 브라우저가 공통값 `payMethod="CARD"`를 추가합니다.

소장 결제 준비 시 이미 소장한 도서는 `409 BOOK_ALREADY_OWNED`로 거부합니다. 같은 독자·도서에
`PENDING` 시도가 있으면 새 행을 만들지 않고 기존 결제 준비 정보를 반환하며, 기존 시도가 `FAILED`일
때만 새 `paymentId`를 발급합니다. 준비 트랜잭션은 `InkAccount`를 잠근 뒤 소장·`PENDING`을
다시 확인합니다. 새 시도를 만들면 `201 Created`, 기존 `PENDING`을 재사용하면 `200 OK`입니다.
잉크 이용권 준비는 요청마다 새 `PENDING`을 만들고 `201 Created`를 반환합니다.

브라우저 완료 API와 서명을 검증한 PortOne `Transaction.Paid` 웹훅은 같은 멱등 완료 로직을 호출합니다.
`Transaction.Failed`는 서버 재조회로 최종 실패를 확인한 뒤 처리합니다.
서버는 브라우저·웹훅 바디만 신뢰하지 않고 PortOne 결제를 다시 조회해 다음 값을 모두 확인합니다.

- `paymentId`와 결제 상태 `PAID`
- 준비 시 저장한 금액과 응답 금액의 일치
- 브라우저 요청의 `CURRENCY_KRW`에 대응하는 서버 조회 통화 `KRW`
- 준비한 결제 종류와 대상 도서의 일치

검증에 성공한 첫 요청만 결제 시도를 `PAID`로 바꾸고, 잉크 구매는 100잉크 지급·원장을, 소장 결제는
`BookOwnership`을 같은 DB 트랜잭션에서 한 번 생성합니다. 브라우저 완료와 웹훅의 도착 순서는 가정하지
않습니다. PortOne이 최종 실패를 반환하거나 금액·통화·식별자가 준비 기록과 다를 때만 `FAILED`로
기록합니다. 조회 장애와 아직 완료되지 않은 상태는 `PENDING`을 유지하고, `FAILED` 뒤 재시도만 새
`paymentId`를 발급합니다. `PENDING`·`FAILED` 시도는 감사와 멱등성 판단을 위해 보존합니다.

브라우저 완료 API의 응답은 서버 재조회 결과를 다음과 같이 표현합니다.

| 재조회 결과 | HTTP | 결제 상태 | JSON 응답 |
| --- | --- | --- | --- |
| 최초 검증 성공 또는 이미 처리한 성공 재시도 | 200 | `PAID` | 잉크: `grantedInk=100`, 현재 `inkBalance`; 소장: 대상 `bookId`, `owned=true` |
| 아직 완료되지 않음 | 202 | `PENDING` 유지 | 잉크: `grantedInk=0`, 현재 `inkBalance`; 소장: 대상 `bookId`, `owned=false` |
| 최종 실패 또는 금액·통화·식별자 불일치 | 422 | `FAILED`로 전이 | 공통 오류 바디 |
| PortOne 조회 일시 장애 | 503 | `PENDING` 유지 | 공통 오류 바디 |

`PAID` 재시도는 기존 도메인 효과를 재사용하고 새 지급·소장을 만들지 않습니다. 이미 `FAILED`인
`paymentId`를 다시 완료하거나 준비한 결제 종류·독자·도서와 완료 경로가 맞지 않으면
`409 PAYMENT_STATE_CONFLICT`입니다. 존재하지 않는 `paymentId`는 `404 RESOURCE_NOT_FOUND`입니다.

웹훅은 `Transaction.Paid`와 `Transaction.Failed`만 상태 처리 대상으로 삼습니다. 그 밖의 정상 서명
이벤트와 알 수 없는 유형은 상태를 바꾸지 않고 `200 OK`, 서명 누락·불일치는
`400 INVALID_WEBHOOK_SIGNATURE`, PortOne 조회 장애나 내부 일시 오류는 재전송을 위해 `5xx`로 응답합니다.
정상 서명된 처리 대상 이벤트라도 `paymentId`가 이 애플리케이션이 발급하는 UUID 형식이 아니면 결제 ID
영역 밖의 이벤트로 보고 상태를 바꾸지 않은 채 `200 OK`로 응답합니다. UUID 형식이지만 대응하는 내부 결제
준비 기록이 없으면 `404 RESOURCE_NOT_FOUND`로 응답해 일시적인 기록 불일치에 대한 PortOne 재전송을
허용하며, 결제 상태와 도메인 효과는 만들지 않습니다.

소장 결제 내역은 `PAID`만 한 페이지에 10개씩 `paidAt DESC, id DESC`로 제공합니다. 각 항목은
`paymentId`, `bookId`, `bookTitle`, `amountWon`, `paidAt`, `owned`를 포함합니다. `PENDING`·`FAILED`는
결제 결과 화면에만 표시하며 내역 목록에는 포함하지 않습니다. 잉크 구매 완료는 별도 결제 내역을 만들지
않고 잉크 내역의 `GRANT +100`으로 표시합니다.

## 오류 응답

```json
{
  "code": "INSUFFICIENT_INK",
  "message": "잉크가 부족합니다."
}
```

| 상황 | HTTP 상태 | 대표 코드 |
| --- | --- | --- |
| 요청 형식·입력 규칙 위반 | 400 | `INVALID_INPUT` |
| 웹훅 서명 누락·불일치 | 400 | `INVALID_WEBHOOK_SIGNATURE` |
| 인증 정보 없음·무효 | 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| CSRF 또는 접근 권한 검증 실패 | 403 | `INVALID_CSRF_TOKEN`, `ACCESS_DENIED` |
| 도서·페이지·현재 열람 세션 없음 | 404 | `RESOURCE_NOT_FOUND` |
| 중복 이메일·이미 소장·교체된 뷰어·현재 상태 충돌 | 409 | `EMAIL_ALREADY_EXISTS`, `BOOK_ALREADY_OWNED`, `VIEWER_SESSION_REPLACED`, `PAYMENT_STATE_CONFLICT` |
| 잉크 부족·결제 검증 실패 | 422 | `INSUFFICIENT_INK`, `PAYMENT_VERIFICATION_FAILED`, `INVALID_PAYMENT_AMOUNT` |
| PortOne 조회 일시 장애 | 503 | `PAYMENT_PROVIDER_UNAVAILABLE` |
| 예기치 못한 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` |

`message`는 사용자에게 노출 가능한 한국어 설명만 담고 비밀번호, 로그인 세션 ID, `viewerSessionId`,
PortOne secret과 내부 저장소 주소를 포함하지 않습니다. 필드 단위 오류 배열은 MVP 계약에
추가하지 않습니다.

## 결정 근거

결정의 배경과 대안은 [ADR 색인](./adr/README.md)에서 확인합니다. 인증은 ADR-0007·0009, 페이지
대여는 ADR-0010, 콘텐츠 전달은 ADR-0013, 테스트 결제 연동은 ADR-0012를 따릅니다. 카테고리 우선 정렬은
[제품 정책](./prd/product-policy.md#카테고리와-탐색), AI 잉크 경로 생성 구조는 ADR-0014를 따릅니다.
