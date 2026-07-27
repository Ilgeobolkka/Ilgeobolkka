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
- PortOne JVM SDK 의존성과 아래 Controller·도메인 기능은 아직 구현 전입니다.

## 공통 규칙

- JSON API는 `/api` 아래에 둡니다.
- 인증이 필요한 요청의 독자 식별자는 서버 세션의 `Principal`에서 얻고 요청 값으로 받지 않습니다.
- 상태 변경 요청은 CSRF 토큰을 검증합니다. Thymeleaf 폼은 hidden 필드, JavaScript `fetch`는 서버 렌더링
  페이지의 `_csrf`, `_csrf_header` meta 태그를 사용하며 별도 토큰 API는 제공하지 않습니다.
- `POST /api/webhooks/portone`만 브라우저 세션과 CSRF 대신 PortOne V2 웹훅 서명을 검증합니다.
- JSON 요청은 `Content-Type: application/json`을 사용하고 JSON 응답은 UTF-8로 인코딩합니다. 페이지 콘텐츠와
  바디가 없는 웹훅 응답은 예외입니다.
- 성공 응답은 공통 래퍼 없이 엔드포인트별 DTO를 JSON 최상위 바디로 반환합니다.
- 날짜와 시각은 UTC 기준 ISO 8601 문자열로 반환합니다.
- 공개 오류 코드는 예외 클래스 이름에서 만들지 않고 명시적인 값으로 관리합니다.

### 페이지 범위와 정렬

- `GET /api/books`, `GET /api/ink/ledger`, `GET /api/ownership-payments`의 `page`는 필수인
  1부터 시작하는 정수입니다.
- 정수가 아니거나 0 이하면 `400 INVALID_INPUT`입니다. 전체 범위를 초과한 양수는 `200 OK`와
  빈 배열을 반환합니다.
- 한 페이지는 10건으로 고정하고 클라이언트가 페이지 크기나 정렬을 지정하지 않습니다.
- 페이지 응답은 요청한 `page`, 전체 페이지 수 `totalPages`, 전체 건수 `totalCount`를 함께 반환합니다.
  전체 건수가 0이면 `totalPages=0`입니다.

## 엔드포인트

| 메서드·경로 | 요청 | 성공 | 응답의 주요 필드 | 인증 |
| --- | --- | --- | --- | --- |
| `POST /api/auth/signup` | `email`, `password` | 201 | `readerId`, `email` | 아니오 |
| `POST /api/auth/login` | `email`, `password` | 200 | `readerId`, `email` | 아니오 |
| `POST /api/auth/logout` | 없음 | 200 | `readerId` | 예 |
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

### 기대 응답 형태

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
`200 OK`입니다.

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
대여는 ADR-0010, 콘텐츠 전달은 ADR-0011, 테스트 결제 연동은 ADR-0012를 따릅니다. 카테고리 우선 정렬은
[제품 정책](./prd/product-policy.md#카테고리와-탐색)에서 관리합니다.
