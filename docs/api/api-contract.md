# 읽어볼까 API 명세

이 문서는 클라이언트가 관찰할 수 있는 HTTP 엔드포인트, 요청·응답 필드, 인증 필요 여부와
성공·실패 동작의 정본입니다. 제공할 기능은 [`docs/prd.md`](../prd.md), 반드시 지킬 검증 계약은
[`docs/test-strategy.md`](../test-strategy.md), Java/Spring 구현 방식은
[`docs/conventions.md`](../conventions.md)를 따릅니다.

API와 요청 처리 구조를 선택한 이유는 다음 ADR에 분리해 기록합니다.

- [`ADR-0015`](../adr/active/api-auth/0015-separate-reading-session-open-and-page-move.md):
  열람 세션 생성과 페이지 이동 API 분리
- [`ADR-0016`](../adr/active/api-auth/0016-distinguish-api-error-status-by-failure-semantics.md):
  실패 성격별 HTTP 상태
- [`ADR-0017`](../adr/active/billing/0017-serialize-reading-confirmation-with-pessimistic-locks.md):
  열람 확정 동시성 제어
- [`ADR-0018`](../adr/active/api-auth/0018-use-minimal-api-error-body.md):
  `code`와 `message` 오류 바디

## 공통 규칙

- 목록의 `page` 쿼리 파라미터는 1부터 시작합니다.
- 도서 목록과 검색 결과는 페이지당 10권으로 고정합니다.
- 포인트 내역의 페이지 크기는 서버가 고정하며 정확한 값은 구현 전에 확정합니다.
- 성공 응답은 공통 래퍼로 감싸지 않고 각 엔드포인트의 응답 필드를 JSON 최상위에 반환합니다.
- `consentedAt`, `pageOpenedAt`, `confirmedAt`, `occurredAt` 같은 시각은
  RFC 3339 형식의 UTC 문자열로 반환합니다(예: `2026-07-24T12:34:56Z`).
- 아래 표의 `인증 필요`는 인증된 독자를 식별해야 한다는 뜻입니다. 쿠키, `Authorization` 헤더,
  토큰 또는 서버 세션 중 어떤 방식을 사용할지는 아직 결정하지 않았습니다.

## 요구사항과 엔드포인트

| 요구사항 ID | 메서드 · 경로 | 외부 동작 |
| --- | --- | --- |
| AUTH-001 | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout` | 이메일·비밀번호 회원가입, 로그인, 로그아웃 |
| AUTH-002 | `POST /api/auth/signup` | 회원가입 시 초기 포인트 0P 계정을 함께 생성 |
| CAT-001 | `GET /api/books` | 가나다순 도서 목록을 10권씩 제공 |
| CAT-002 | `GET /api/books` (`keyword`) | 제목·저자 부분 일치 검색 결과를 가나다순 10권씩 제공 |
| CAT-003 | `GET /api/books/{bookId}` | 표지·제목·저자·소개·전체 페이지 수만 제공 |
| CNS-001 | `GET /api/books/{bookId}/reading-consent`, `POST /api/books/{bookId}/reading-consent` | 동의 여부 확인과 최초 열람 동의 등록 |
| VIEW-001 | `POST /api/books/{bookId}/reading-sessions`, `PATCH /api/reading-sessions/current/page` | 뷰어 열기와 이전·다음·페이지 번호 이동 |
| VIEW-002 | `POST /api/books/{bookId}/reading-sessions` | 새 세션을 열고 기존 세션을 무효화 |
| BILL-001 | `POST /api/reading-sessions/current/confirmations` | 서버 시간으로 6초 이상 경과했을 때만 열람 확정 |
| BILL-002 | `POST /api/reading-sessions/current/confirmations` | 사용자·도서·페이지별 최초 한 번만 50P 차감 |
| BILL-003 | `POST /api/reading-sessions/current/confirmations` | 탭 전환·최소화와 무관하게 서버 경과 시간으로 판정 |
| BILL-004 | `PATCH /api/reading-sessions/current/page` | 6초 전 페이지 이동에는 기존 페이지를 확정하지 않음 |
| BILL-005 | `POST /api/books/{bookId}/reading-sessions`, `PATCH /api/reading-sessions/current/page` | 미확정 페이지 진입 전에 잔액을 검사하고 50P 미만이면 차단 |
| BILL-006 | `POST /api/reading-sessions/current/confirmations` | 실패 시 열람을 확정하지 않고 명시적 재시도 허용 |
| LIB-001 | `POST /api/reading-sessions/current/confirmations`, `GET /api/library` | 최초 확정 시 서재에 추가하고 마지막 확정 페이지 조회 |
| PTS-001 | `POST /api/reading-sessions/current/confirmations`, `GET /api/points/balance`, `GET /api/points/ledger` | 포인트 증감 내역과 현재 잔액 제공 |
| PTS-002 | `GET /api/points/ledger` | 페이지별 차감 상세 제공 |

## 엔드포인트

| 메서드 · 경로 | 요청 필드 | 응답 필드 | 인증 필요 |
| --- | --- | --- | --- |
| `POST /api/auth/signup` | `email`, `password` | `readerId`, `email` | 아니오 |
| `POST /api/auth/login` | `email`, `password` | `readerId`, `email` | 아니오 |
| `POST /api/auth/logout` | 없음 | `readerId` | 예 |
| `GET /api/books` | 쿼리: `page`, `keyword?` | `books[]` (`bookId`, `title`, `author`, `coverImageUrl`), `page`, `totalPages`, `totalCount` | 아니오 |
| `GET /api/books/{bookId}` | 없음 | `bookId`, `title`, `author`, `description`, `coverImageUrl`, `totalPageCount` | 아니오 |
| `GET /api/books/{bookId}/reading-consent` | 없음 | `agreed`, `consentedAt?` | 예 |
| `POST /api/books/{bookId}/reading-consent` | 없음 | `bookId`, `consentedAt` | 예 |
| `POST /api/books/{bookId}/reading-sessions` | `pageNumber` | `sessionToken`, `bookId`, `pageNumber`, `pageImageUrl`, `pageOpenedAt` | 예 |
| `PATCH /api/reading-sessions/current/page` | `sessionToken`, `pageNumber` | `pageNumber`, `pageImageUrl`, `pageOpenedAt`, `alreadyConfirmed` | 예 |
| `POST /api/reading-sessions/current/confirmations` | `sessionToken`, `bookId`, `pageNumber` | `pageNumber`, `deductedAmount`, `balanceAfter`, `confirmedAt` | 예 |
| `GET /api/library` | 없음 | `entries[]` (`bookId`, `title`, `lastConfirmedPageNumber`) | 예 |
| `GET /api/points/balance` | 없음 | `balance` | 예 |
| `GET /api/points/ledger` | 쿼리: `page` | `entries[]` (`type`: `GRANT` 또는 `DEDUCTION`, `amount`, `balanceAfter`, `bookTitle?`, `pageNumber?`, `occurredAt`), `page`, `totalPages`, `totalCount` | 예 |

`coverImageUrl`은 브라우저가 요청할 수 있는 애플리케이션 URL입니다. 로컬 파일 경로나 비공개 S3
객체 키를 응답에 직접 노출하지 않습니다.

## 계정

- 이미 가입된 이메일로 회원가입하면 `409 Conflict`와 `EMAIL_ALREADY_EXISTS`를 반환합니다.
- 로그인 이메일 또는 비밀번호가 일치하지 않으면 `401 Unauthorized`와 `INVALID_CREDENTIALS`를
  반환합니다. 어떤 값이 틀렸는지는 응답에서 구분하지 않습니다.

## 최초 열람 동의

- `POST /api/books/{bookId}/reading-consent`는 사용자·도서별 최초 한 번만 동의를 등록합니다.
- 이미 동의한 도서에 다시 동의를 요청하면 `409 Conflict`와
  `READING_CONSENT_ALREADY_EXISTS`를 반환합니다.

## 열람 세션

- `POST /api/books/{bookId}/reading-sessions`는 새 세션 토큰을 발급하고 같은 독자의 기존 세션을
  무효화합니다.
- `PATCH /api/reading-sessions/current/page`와
  `POST /api/reading-sessions/current/confirmations`는 활성 세션이 없으면 `404 Not Found`를 반환합니다.
- 활성 세션은 있지만 `sessionToken`이 현재 토큰과 다르면 `409 Conflict`를 반환합니다.
- 현재 페이지와 같은 `pageNumber`로 이동을 요청하면 페이지 이동으로 처리하지 않습니다.
  열람 시작 시각과 응답의 `pageOpenedAt`을 기존 값으로 유지합니다.
- 이미 열람 확정된 페이지는 현재 포인트 잔액과 관계없이 다시 열 수 있습니다.
- 열람 확정되지 않은 페이지는 진입 시점의 잔액이 50P 미만이면 이미지를 반환하지 않고
  `422 Unprocessable Content`를 반환합니다.

## 열람 확정

- 요청의 `sessionToken`, `bookId`, `pageNumber`는 서버의 현재 세션·페이지와 모두 일치해야 합니다.
  일치하지 않으면 `409 Conflict`를 반환합니다.
- 서버에 기록된 페이지 열림 시각으로부터 6초 미만이면 `409 Conflict`를 반환합니다.
- 처음 확정되는 페이지는 50P를 한 번 차감하고, 차감 내역·열람 확정·마지막 열람 위치를 함께
  반영합니다.
- 페이지 진입 뒤 다른 요청으로 잔액이 줄어 확정 직전 잔액이 50P 미만이면
  `422 Unprocessable Content`와 `INSUFFICIENT_POINT`를 반환합니다. 포인트 차감·차감 내역·열람
  확정·마지막 열람 위치는 모두 반영하지 않습니다.
- 이미 확정된 페이지의 요청은 성공으로 처리하며 `deductedAmount`는 `0`,
  `balanceAfter`는 요청 시점의 현재 잔액, `confirmedAt`은 해당 페이지의 최초 확정 시각을 반환합니다.
- 처리 중 하나라도 실패하면 포인트 차감·차감 내역·열람 확정·마지막 열람 위치를 모두 반영하지
  않습니다. 클라이언트는 같은 요청을 명시적으로 재시도할 수 있습니다.
- 위 결과를 보장하는 내부 잠금과 트랜잭션 전략은
  [`ADR-0017`](../adr/active/billing/0017-serialize-reading-confirmation-with-pessimistic-locks.md)을 따릅니다.

## 오류 응답

오류는 다음 JSON 형식으로 반환합니다.

```json
{
  "code": "INSUFFICIENT_POINT",
  "message": "포인트가 부족합니다."
}
```

- `code`: 아래 표에 등록된 안정적인 `SCREAMING_SNAKE_CASE` 값
- `message`: 사용자에게 표시할 수 있는 한국어 설명
- 토큰, 비밀번호, DB 접속 정보 등 민감값은 두 필드에 포함하지 않습니다.

| HTTP 상태 | `code` | 적용 조건 |
| --- | --- | --- |
| `400 Bad Request` | `INVALID_REQUEST` | 비어 있거나 형식이 잘못된 값, 0 이하인 `pageNumber` |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | 인증이 필요한 API에 인증 정보가 없거나 유효하지 않음 |
| `401 Unauthorized` | `INVALID_CREDENTIALS` | 로그인 이메일 또는 비밀번호 불일치 |
| `403 Forbidden` | `READING_CONSENT_REQUIRED` | 최초 열람 동의 없이 세션 생성 요청 |
| `404 Not Found` | `BOOK_NOT_FOUND` | 존재하지 않는 `bookId` |
| `404 Not Found` | `PAGE_NOT_FOUND` | 도서의 전체 페이지 수를 초과한 `pageNumber` |
| `404 Not Found` | `READING_SESSION_NOT_FOUND` | 활성 열람 세션이 없음 |
| `409 Conflict` | `READING_SESSION_REPLACED` | 교체된 `sessionToken` 사용 |
| `409 Conflict` | `READING_PAGE_MISMATCH` | 요청 도서·페이지가 현재 세션과 다름 |
| `409 Conflict` | `READING_CONFIRMATION_TOO_EARLY` | 서버 경과 시간이 6초 미만 |
| `409 Conflict` | `READING_CONSENT_ALREADY_EXISTS` | 이미 동의한 도서에 동의 재요청 |
| `409 Conflict` | `EMAIL_ALREADY_EXISTS` | 이미 가입된 이메일로 회원가입 |
| `422 Unprocessable Content` | `INSUFFICIENT_POINT` | 미확정 페이지 진입 또는 확정 시 포인트 부족 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버·DB 오류 |

`GET /api/points/ledger`, `GET /api/library`처럼 개인 데이터를 반환하는 API는 인증된 독자 본인의
범위만 조회합니다. 현재 계약에는 다른 독자의 식별자를 경로 파라미터로 지정하는 엔드포인트가 없습니다.

## 아직 정하지 않은 내용

- 인증 정보의 발급·전달 방식과 로그인 응답의 추가 필드
- 필드 단위 검증 오류 배열
- 포인트 내역의 페이지당 개수
- 각 생성·수정 API의 구체적인 성공 HTTP 상태
