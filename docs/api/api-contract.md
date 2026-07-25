# 읽어볼까 API 명세

## 문서 상태

- 상태: 새 잉크·30일 대여 정책 반영 완료
- 기준일: 2026-07-25
- 구현 상태: Controller와 DTO 구현 전

이 문서는 클라이언트가 관찰할 수 있는 HTTP 엔드포인트, 요청·응답 필드와 성공·실패 동작의 정본입니다.
제품 범위와 정책은 [`PRD 색인`](../prd/README.md), 데이터 구조는 [`목표 데이터 모델`](../data/erd.md), Java/Spring 구현 방식은 [`백엔드 구현 컨벤션`](../conventions.md)을 따릅니다.

API 구조와 처리 원칙의 근거는 다음 ADR에 기록합니다.

- [`ADR-0015`](../adr/active/api-auth/0015-separate-reading-session-open-and-page-move.md): 열람 세션 생성과 페이지 이동 API 분리
- [`ADR-0016`](../adr/active/api-auth/0016-distinguish-api-error-status-by-failure-semantics.md): 실패 성격별 HTTP 상태
- [`ADR-0018`](../adr/active/api-auth/0018-use-minimal-api-error-body.md): `code`와 `message` 오류 바디
- [`ADR-0019`](../adr/active/billing/0019-charge-ink-when-page-opens.md): 페이지를 열 때 잉크 즉시 차감
- [`ADR-0020`](../adr/active/billing/0020-use-universal-ink-page-pass.md): 전 도서 공통 잉크 이용권
- [`ADR-0021`](../adr/active/billing/0021-rent-each-page-for-thirty-days.md): 페이지별 30일 대여
- [`ADR-0022`](../adr/active/billing/0022-serialize-page-rental-with-pessimistic-locks.md): 페이지 대여 동시성 제어
- [`ADR-0023`](../adr/active/content/0023-use-pdf-page-mapped-hybrid-content.md): 원본 PDF 페이지별 혼합 콘텐츠 제공

## 1. 공통 규칙

- API 기본 경로는 `/api`입니다.
- JSON 요청과 응답의 미디어 타입은 `application/json`입니다. 응답으로 받은 이미지 URL은 실제 이미지 형식의 미디어 타입을 반환합니다.
- 성공 응답은 공통 래퍼로 감싸지 않고 엔드포인트별 필드를 JSON 최상위에 반환합니다.
- 목록의 `page` 쿼리 파라미터와 도서의 `pageNumber`는 1부터 시작합니다.
- 도서 목록과 검색 결과는 페이지당 10권으로 고정합니다.
- 잉크 내역의 페이지 크기는 서버가 고정하며 정확한 값은 구현 전에 확정합니다.
- `consentedAt`, `rentalStartedAt`, `rentalExpiresAt`, `occurredAt` 같은 시각은 RFC 3339 형식의 UTC 문자열로 반환합니다.
  예: `2026-07-25T01:00:00Z`
- 인증이 필요한 API는 요청 본문이나 경로의 사용자 ID가 아니라 서버가 인증한 독자를 사용합니다.
- 쿠키, `Authorization` 헤더 또는 서버 세션 중 어떤 방식으로 인증 정보를 전달할지는 아직 결정하지 않았습니다.

## 2. 요구사항과 엔드포인트

| 요구사항 ID | 메서드 · 경로 | 외부 동작 |
| --- | --- | --- |
| AUTH-001 | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout` | 이메일·비밀번호 회원가입, 로그인, 로그아웃 |
| AUTH-002 | `POST /api/auth/signup` | 회원가입 시 잉크 잔액 0인 계정을 함께 생성 |
| CAT-001 | `GET /api/books` | 카테고리 기준 도서 목록을 10권씩 제공 |
| CAT-002 | `GET /api/books?keyword=...` | 하나의 검색어로 제목 또는 저자 부분 일치 검색 |
| CAT-003 | `GET /api/books/{bookId}` | 도서 기본 정보, 카테고리와 원본 PDF 기준 페이지 수 제공 |
| CNS-001 | `GET /api/books/{bookId}/reading-consent`, `POST /api/books/{bookId}/reading-consent` | 동의 여부 확인과 최초 열람 동의 등록 |
| VIEW-001 | `POST /api/books/{bookId}/reading-sessions`, `PATCH /api/reading-sessions/current/page` | 새 뷰어 열기와 현재 세션의 페이지 이동 |
| VIEW-002 | `POST /api/books/{bookId}/reading-sessions` | 새 세션을 열고 기존 세션 무효화 |
| BILL-001 | `POST /api/books/{bookId}/reading-sessions`, `PATCH /api/reading-sessions/current/page` | 유효한 대여가 없는 페이지를 열기 전에 1잉크 즉시 차감 |
| BILL-002 | 위 페이지 열기 API | 대여 기간의 같은 페이지를 추가 차감 없이 제공 |
| BILL-003 | 위 페이지 열기 API | 만료된 페이지에 1잉크를 차감하고 새 30일 대여 시작 |
| BILL-004 | 위 페이지 열기 API | 잉크 부족 시 차감·대여·페이지 제공을 모두 차단 |
| BILL-005 | 위 페이지 열기 API | 잉크 차감·내역·대여·마지막 위치를 한 트랜잭션으로 처리 |
| BILL-006 | 위 페이지 열기 API | 같은 페이지의 동시·중복 요청에서 유효한 대여 한 건당 한 번만 차감 |
| LIB-001 | 위 페이지 열기 API, `GET /api/library` | 성공한 페이지를 마지막 위치로 저장하고 내 서재 조회 |
| INK-001 | 위 페이지 열기 API, `GET /api/ink/balance`, `GET /api/ink/ledger` | 잉크 잔액·내역과 페이지 차감을 일치시킴 |
| INK-002 | `GET /api/ink/ledger` | 도서·페이지별 차감과 대여 만료 상세 제공 |
| CONT-001 | 외부 엔드포인트 없음 | 배포 전 원본 PDF와 변환 페이지 수·번호를 적재 검증 |
| CONT-002 | 위 페이지 열기 API | 텍스트·이미지·혼합 콘텐츠 중 현재 한 페이지만 제공 |
| CONT-003 | 위 페이지 열기 API와 응답의 `imageUrl` | 원본 PDF와 권한 없는 페이지 콘텐츠의 직접 접근 차단 |

`POST /api/reading-sessions/current/confirmations`와 6초 열람 확정 요청은 제공하지 않습니다.
페이지 열기 요청 자체가 필요한 잉크 차감과 대여 시작을 수행합니다.

## 3. 엔드포인트 요약

| 메서드 · 경로 | 요청 | 성공 응답 | 인증 |
| --- | --- | --- | --- |
| `POST /api/auth/signup` | 본문: `email`, `password` | `201 Created`: `readerId`, `email`, `inkBalance` | 아니오 |
| `POST /api/auth/login` | 본문: `email`, `password` | `200 OK`: `readerId`, `email` | 아니오 |
| `POST /api/auth/logout` | 없음 | `204 No Content` | 예 |
| `GET /api/books` | 쿼리: `page`, `keyword?` | `200 OK`: `books[]`, 페이지 정보 | 아니오 |
| `GET /api/books/{bookId}` | 없음 | `200 OK`: 도서 상세 | 아니오 |
| `GET /api/books/{bookId}/reading-consent` | 없음 | `200 OK`: `agreed`, `consentedAt?` | 예 |
| `POST /api/books/{bookId}/reading-consent` | 없음 | `201 Created`: `bookId`, `consentedAt` | 예 |
| `POST /api/books/{bookId}/reading-sessions` | 본문: `pageNumber` | `200 OK`: 새 세션과 페이지 열기 결과 | 예 |
| `PATCH /api/reading-sessions/current/page` | 본문: `sessionToken`, `pageNumber` | `200 OK`: 페이지 열기 결과 | 예 |
| 응답의 `imageUrl`에 대한 `GET` | 없음 | `200 OK`: 페이지 이미지 바이너리 | 예, 유효한 페이지 대여 |
| `GET /api/library` | 없음 | `200 OK`: `entries[]` | 예 |
| `GET /api/ink/balance` | 없음 | `200 OK`: `balance` | 예 |
| `GET /api/ink/ledger` | 쿼리: `page` | `200 OK`: `entries[]`, 페이지 정보 | 예 |

`POST /api/books/{bookId}/reading-sessions`는 사용자당 하나인 현재 세션을 생성하거나 교체하므로,
새 리소스 위치를 뜻하는 `201 Created` 대신 페이지 열기 결과와 함께 `200 OK`를 반환합니다.

## 4. 계정

### 회원가입

요청:

```json
{
  "email": "reader@example.com",
  "password": "password"
}
```

성공 응답:

```json
{
  "readerId": 1,
  "email": "reader@example.com",
  "inkBalance": 0
}
```

- 일반 회원가입 사용자는 잉크 잔액 0으로 시작합니다.
- 잉크 계정 생성까지 회원가입과 함께 성공하거나 함께 실패해야 합니다.
- 이미 가입된 이메일이면 `409 Conflict`와 `EMAIL_ALREADY_EXISTS`를 반환합니다.

### 로그인과 로그아웃

- 로그인 이메일 또는 비밀번호가 일치하지 않으면 `401 Unauthorized`와 `INVALID_CREDENTIALS`를 반환합니다.
- 어떤 값이 틀렸는지는 응답에서 구분하지 않습니다.
- 로그인 성공 후 인증 정보를 전달하는 구체적인 방식은 아직 정하지 않았습니다.
- 로그아웃 성공 응답에는 본문을 반환하지 않습니다.

## 5. 도서 목록과 상세

### 도서 목록

`GET /api/books?page=1&keyword=소설`

```json
{
  "books": [
    {
      "bookId": 1,
      "title": "예시 도서",
      "author": "예시 작가",
      "category": "소설",
      "coverImageUrl": "/api/book-covers/1"
    }
  ],
  "page": 1,
  "totalPages": 10,
  "totalCount": 100
}
```

- `keyword`가 없으면 전체 목록, 있으면 제목 또는 저자에 부분 일치하는 목록을 반환합니다.
- 결과는 서버가 정한 카테고리 순서를 기준으로 정렬합니다.
- 카테고리 목록과 같은 카테고리 안의 세부 정렬 순서는 아직 정하지 않았습니다.
- `coverImageUrl`은 애플리케이션 URL이며 로컬 파일 경로나 비공개 S3 객체 키를 노출하지 않습니다.

### 도서 상세

`GET /api/books/{bookId}`

응답:

```json
{
  "bookId": 1,
  "title": "예시 도서",
  "author": "예시 작가",
  "description": "도서 소개",
  "category": "소설",
  "coverImageUrl": "/api/book-covers/1",
  "totalPageCount": 120
}
```

`totalPageCount`는 표지를 제외한 원본 PDF 페이지 수입니다.

## 6. 최초 열람 동의

- `GET /api/books/{bookId}/reading-consent`는 동의하지 않은 경우 `{"agreed": false}`를 반환합니다.
- 동의한 경우 `{"agreed": true, "consentedAt": "..."}`를 반환합니다.
- `POST /api/books/{bookId}/reading-consent`는 페이지를 처음 열 때 1잉크를 즉시 차감하고
  페이지별 30일 대여를 시작하는 정책에 독자가 동의했음을 기록합니다.
- 동의 등록 자체는 잉크를 차감하거나 페이지 대여를 만들지 않습니다.
- 이미 동의한 도서에 다시 동의를 요청하면 `409 Conflict`와 `READING_CONSENT_ALREADY_EXISTS`를 반환합니다.

## 7. 열람 세션과 페이지 열기

### 새 열람 세션

`POST /api/books/{bookId}/reading-sessions`

```json
{
  "pageNumber": 10
}
```

- 새 세션 토큰을 발급하고 같은 독자의 기존 세션을 무효화합니다.
- 동의하지 않은 도서이면 세션과 페이지 대여를 만들지 않고 `403 Forbidden`과 `READING_CONSENT_REQUIRED`를 반환합니다.
- 진입 페이지를 열 수 없는 경우 새 세션 생성과 기존 세션 교체도 반영하지 않습니다.

### 현재 세션의 페이지 이동

`PATCH /api/reading-sessions/current/page`

```json
{
  "sessionToken": "opaque-session-token",
  "pageNumber": 11
}
```

- 활성 세션이 없으면 `404 Not Found`와 `READING_SESSION_NOT_FOUND`를 반환합니다.
- `sessionToken`이 현재 토큰과 다르면 `409 Conflict`와 `READING_SESSION_REPLACED`를 반환합니다.
- 존재하지 않거나 도서의 전체 페이지 수를 벗어난 `pageNumber`이면 현재 페이지를 바꾸지 않습니다.
- 현재 페이지와 같은 `pageNumber`를 요청해도 아래 페이지 대여 규칙으로 다시 확인합니다.

### 페이지 열기 성공 응답

세션 생성과 페이지 이동은 같은 형식의 페이지 열기 결과를 반환합니다.

```json
{
  "sessionToken": "opaque-session-token",
  "bookId": 1,
  "pageNumber": 10,
  "content": {
    "type": "HYBRID",
    "blocks": [
      {
        "type": "TEXT",
        "text": "페이지에서 추출한 본문"
      },
      {
        "type": "IMAGE",
        "imageUrl": "/api/page-images/1001"
      }
    ]
  },
  "access": {
    "deductedInk": 1,
    "balanceAfter": 99,
    "rentalStartedAt": "2026-07-25T01:00:00Z",
    "rentalExpiresAt": "2026-08-24T01:00:00Z"
  }
}
```

- `deductedInk`는 이번 요청에서 실제 차감했으면 `1`, 유효한 대여를 재사용했으면 `0`입니다.
- `balanceAfter`는 요청 처리가 끝난 시점의 현재 잉크 잔액입니다.
- `rentalStartedAt`과 `rentalExpiresAt`은 이번 요청이 사용한 대여 기록의 시각입니다.
- 유효한 대여를 재사용하면 기존 대여의 시작·만료 시각을 반환합니다.
- 만료된 페이지를 다시 열면 1잉크를 차감하고 새 대여의 시작·만료 시각을 반환합니다.

### 페이지 열기 처리 규칙

1. 도서, 페이지 번호, 최초 동의와 현재 세션을 검증합니다.
2. 백엔드 서버 시각을 기준으로 해당 페이지의 유효한 대여를 확인합니다.
3. 유효한 대여가 있으면 잉크를 차감하지 않습니다.
4. 유효한 대여가 없으면 잉크 잔액을 확인합니다.
5. 잔액이 1 이상이면 1잉크 차감, 잉크 내역, 30일 대여와 마지막 열람 위치를 함께 저장합니다.
6. 잔액이 0이면 아무 상태도 바꾸지 않고 `422 Unprocessable Content`와 `INSUFFICIENT_INK`를 반환합니다.
7. 트랜잭션 성공 뒤 권한을 얻은 현재 페이지 콘텐츠만 반환합니다.

잉크 잔액, 차감 내역, 대여 기록, 현재 세션 페이지와 마지막 열람 위치는 하나의 트랜잭션으로 모두 성공하거나 모두 실패합니다.
같은 페이지 요청이 동시에 여러 번 도착해도 유효한 대여 한 건에는 1잉크만 차감합니다.

트랜잭션 커밋 뒤 응답이 유실되거나 콘텐츠 전달이 일시적으로 실패하면 클라이언트는 같은 페이지를 다시 요청할 수 있습니다.
이미 만들어진 유효한 대여를 사용하므로 추가 잉크는 차감하지 않습니다.
내부 잠금 순서는 [`ADR-0022`](../adr/active/billing/0022-serialize-page-rental-with-pessimistic-locks.md)를 따릅니다.

## 8. 페이지 콘텐츠

`content.type`은 다음 값을 사용합니다.

| 값 | 의미 |
| --- | --- |
| `TEXT` | 모든 `blocks`가 텍스트 |
| `IMAGE` | 모든 `blocks`가 이미지 |
| `HYBRID` | 텍스트와 이미지 블록을 함께 사용 |

`content.blocks`는 화면에 표시할 순서대로 반환합니다.

- `TEXT` 블록은 `text`만 가집니다. `text`는 HTML이 아닌 일반 문자열입니다.
- `IMAGE` 블록은 `imageUrl`만 가집니다.
- 클라이언트는 배열 순서를 바꾸거나 서로 다른 원본 PDF 페이지의 블록을 합치지 않습니다.
- `imageUrl`은 애플리케이션이 제공하는 URL이며 원본 저장소 경로를 노출하지 않습니다.
- 클라이언트는 `imageUrl`의 경로 구조를 해석하거나 직접 조립하지 않고 응답값을 그대로 요청합니다.
- 이미지 요청 시에도 인증된 독자의 해당 페이지 대여가 아직 유효한지 백엔드에서 다시 확인합니다.
- 대여가 없거나 만료된 이미지 URL을 직접 요청하면 `403 Forbidden`과 `PAGE_RENTAL_REQUIRED`를 반환합니다.
- 원본 PDF 전체를 반환하는 엔드포인트는 제공하지 않습니다.

## 9. 내 서재

`GET /api/library` 응답:

```json
{
  "entries": [
    {
      "bookId": 1,
      "title": "예시 도서",
      "coverImageUrl": "/api/book-covers/1",
      "lastOpenedPageNumber": 10
    }
  ]
}
```

- 한 페이지 이상 성공적으로 대여한 도서를 자동으로 추가합니다.
- 유효한 대여를 재사용해 다른 페이지를 연 경우에도 마지막 열람 페이지를 갱신합니다.
- 대여가 만료되어도 내 서재 항목을 자동으로 삭제하지 않습니다.
- 누적 사용 잉크는 내 서재 응답에 포함하지 않습니다.

## 10. 잉크 잔액과 내역

### 잉크 잔액

`GET /api/ink/balance`

```json
{
  "balance": 99
}
```

### 잉크 내역

`GET /api/ink/ledger?page=1`

```json
{
  "entries": [
    {
      "type": "DEDUCTION",
      "amount": -1,
      "balanceAfter": 99,
      "bookTitle": "예시 도서",
      "pageNumber": 10,
      "occurredAt": "2026-07-25T01:00:00Z",
      "rentalExpiresAt": "2026-08-24T01:00:00Z"
    }
  ],
  "page": 1,
  "totalPages": 1,
  "totalCount": 1
}
```

- 최신 내역부터 반환합니다.
- `GRANT`의 `amount`는 양수, `DEDUCTION`의 `amount`는 음수입니다.
- 지급 내역에는 `bookTitle`, `pageNumber`, `rentalExpiresAt`을 반환하지 않습니다.
- 페이지 차감 내역은 도서 제목, 페이지 번호, 차감량, 차감 시각, 차감 후 잔액과 대여 만료 시각을 반환합니다.
- 생성된 잉크 내역은 수정하거나 삭제하지 않습니다.
- 현재 잔액은 모든 `amount`의 합계와 일치해야 합니다.

MVP에는 실제 PG 결제와 잉크 자동 충전 API를 포함하지 않습니다.

## 11. 오류 응답

모든 오류는 다음 형식으로 반환합니다.

```json
{
  "code": "INSUFFICIENT_INK",
  "message": "잉크가 부족합니다."
}
```

- `code`는 API 계약에 등록된 `SCREAMING_SNAKE_CASE` 값입니다.
- `message`는 사용자에게 표시할 수 있는 한국어 설명입니다.
- 토큰, 비밀번호, DB 접속 정보와 내부 예외 메시지는 두 필드에 포함하지 않습니다.

| HTTP 상태 | `code` | 적용 조건 |
| --- | --- | --- |
| `400 Bad Request` | `INVALID_REQUEST` | 비어 있거나 형식이 잘못된 값, 0 이하인 `page` 또는 `pageNumber` |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | 인증 정보가 없거나 유효하지 않음 |
| `401 Unauthorized` | `INVALID_CREDENTIALS` | 로그인 이메일 또는 비밀번호 불일치 |
| `403 Forbidden` | `READING_CONSENT_REQUIRED` | 최초 열람 동의 없이 세션 생성 요청 |
| `403 Forbidden` | `PAGE_RENTAL_REQUIRED` | 유효한 대여 없이 페이지 이미지 직접 요청 |
| `404 Not Found` | `BOOK_NOT_FOUND` | 존재하지 않는 `bookId` |
| `404 Not Found` | `PAGE_NOT_FOUND` | 존재하지 않거나 원본 PDF 페이지 범위를 벗어난 `pageNumber` |
| `404 Not Found` | `READING_SESSION_NOT_FOUND` | 활성 열람 세션이 없음 |
| `409 Conflict` | `READING_SESSION_REPLACED` | 교체된 `sessionToken` 사용 |
| `409 Conflict` | `READING_CONSENT_ALREADY_EXISTS` | 이미 동의한 도서에 동의 재요청 |
| `409 Conflict` | `EMAIL_ALREADY_EXISTS` | 이미 가입된 이메일로 회원가입 |
| `422 Unprocessable Content` | `INSUFFICIENT_INK` | 유효한 대여가 없는 페이지를 열 잉크가 부족함 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버·DB·콘텐츠 저장소 오류 |

`GET /api/ink/ledger`, `GET /api/library`처럼 개인 데이터를 반환하는 API는 인증된 독자 본인의 범위만 조회합니다.
다른 독자의 식별자를 경로 또는 쿼리 파라미터로 받지 않습니다.

## 12. 아직 정하지 않은 내용

- 인증 정보의 발급·전달 방식과 로그인 응답의 추가 필드
- 필드 단위 검증 오류 배열
- 잉크 내역의 페이지당 개수
- 카테고리 목록, 카테고리 간 순서와 같은 카테고리 안의 세부 정렬
- 페이지 이미지 애플리케이션 URL의 구체적인 경로와 만료 방식

미결정 내용은 구현에서 임의로 고정하지 않습니다.
