# 0008. API 계약 초안을 정의한다

- 상태: 제안됨
- 날짜: 2026-07-22
- 결정자: AI 초안 — 검토 대기

## 맥락

ADR-0007에서 핵심 도메인 엔티티(ERD)를 정의했지만, 아직 Controller나 DTO 코드는 없는 골격
단계입니다(`src`에는 `IlgeobolkkaApplication`만 존재). `.agents/skills/adr/SKILL.md`는 "데이터
모델, API 계약, 모듈 경계 등 되돌리기 비싼 구조 결정"을 ADR 작성 시점으로 명시하고 있고, API
계약은 한 번 클라이언트가 사용하기 시작하면 바꾸는 비용이 크므로 실제 Controller를 작성하기 전에
계약을 먼저 정리해 검토받습니다.

`docs/conventions.md`의 "검증과 오류 응답" 절은 "공통 성공 응답 래퍼는 API 계약이 정해지기 전에
만들지 않는다. HTTP 상태 코드와 오류 형식은 이후 API 계약에서 먼저 합의한다"고 안내합니다. 이
ADR이 바로 그 "이후 API 계약" 문서이며, 여기서 성공/오류 응답 형식과 HTTP 상태 매핑 원칙을 처음
확정합니다.

아래 표는 PRD 7절 기능 요구사항 15개(AUTH-001~PTS-002)를 이 ADR이 정의하는 엔드포인트에 매핑한
것입니다. ADR-0007의 "요구사항/불변식 → 엔티티" 표와 같은 방식으로, 모든 요구사항 ID가 최소 한
엔드포인트에 근거를 두고 있습니다.

인증 구현 방식(토큰/세션 스킴)은 README "다음 결정" 2번에 따라 아직 미정입니다. 이 ADR은
엔드포인트별 "인증 필요 여부"만 표시하고, 구체적인 토큰 발급·전달 방식(쿠키, `Authorization`
헤더, 세션 저장소 등)은 이후 별도 결정으로 남기며 이 ADR의 범위가 아닙니다.

## 고려한 대안

- **목록 페이지네이션 — 1-based 페이지 번호 + 고정 페이지 크기 vs `offset`/`limit` 자유 지정**: PRD
  6.2, 6.6은 "한 페이지에 10권", "페이지 번호로 이동", "최신 내역부터 페이지네이션"만 요구하고
  클라이언트가 페이지 크기를 임의로 정할 필요를 요구하지 않습니다. `offset`/`limit`은 더 일반적이지만
  클라이언트가 임의의 큰 `limit`을 보내는 경우까지 서버가 방어해야 해 계약이 복잡해집니다. 도서
  목록·검색은 페이지당 10권 고정, 포인트 내역은 페이지당 개수를 서버가 고정값으로 정하는 1-based
  `page` 쿼리 파라미터 방식을 선택했습니다(AGENTS.md 2절, 단순함 우선).
- **세션 오픈과 페이지 이동을 같은 엔드포인트로 둘지 vs 분리할지**: VIEW-002("새 뷰어가 열리면 기존
  세션 종료")는 `session_token`을 새로 발급해야 하는 사건이지만, VIEW-001("이전/다음/페이지 번호
  직접 이동")은 같은 세션 안에서 반복적으로 일어나는 사건입니다. 하나의 엔드포인트로 합치고
  `isNewSession` 같은 플래그로 분기하는 대안도 검토했으나, 이는 Controller가 서로 다른 두 의미를
  숨은 파라미터로 구분하게 만들어 conventions.md의 Controller 책임(경계만 담당)과 어긋납니다. 세션을
  새로 여는 `POST .../reading-sessions`와 같은 세션 내에서 페이지만 옮기는
  `PATCH .../reading-sessions/current/page`를 분리했습니다.
- **도메인 규칙 위반의 HTTP 상태 — 전부 `409 Conflict`로 통일 vs 성격에 따라 분리**: 세션 만료·중복
  동의처럼 "요청은 유효하지만 현재 리소스 상태와 충돌"하는 경우와, 잔액 부족처럼 "리소스 상태는
  정상이지만 비즈니스 규칙으로 거부"하는 경우를 모두 `409`로 묶으면 단순하지만, 클라이언트가 취해야
  할 다음 행동(전자는 최신 상태 재조회 후 재시도, 후자는 사용자에게 포인트 부족 안내)이 달라 상태
  코드 하나로는 구분이 어렵습니다. 상태 충돌은 `409`, 잔액 부족 같은 비즈니스 규칙 거부는 `422`로
  분리했습니다.
- **오류 응답 바디 — 필드 단위 검증 오류 배열 포함 vs 최소 필드만**: Bean Validation을 실제 Request
  DTO에 붙이기 전까지는 어떤 필드가 실패할 수 있는지 확정할 수 없습니다. `code`, `message` 최소
  필드만 이 ADR에서 정하고, 필드별 오류 목록(`errors[]` 등)은 실제 검증 규칙을 붙이는 구현
  시점에 필요하면 추가합니다(AGENTS.md 2절, conventions.md "주석과 과한 설계 제한").

## 결정

### 요구사항 ID → 엔드포인트 매핑

| 요구사항 ID | 메서드 · 경로 | 설명 | 관련 엔티티(ADR-0007) |
| --- | --- | --- | --- |
| AUTH-001 | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout` | 이메일·비밀번호 회원가입, 로그인, 로그아웃 | `Reader` |
| AUTH-002 | `POST /api/auth/signup` | 회원가입 시 초기 포인트 0P인 `PointAccount`를 함께 생성 | `Reader`, `PointAccount` |
| CAT-001 | `GET /api/books` | 가나다순 도서 목록, 10권씩 페이지네이션 | `Book` |
| CAT-002 | `GET /api/books` (쿼리 `keyword`) | 제목·저자 부분 일치 검색, 결과도 가나다순 10권씩 | `Book` |
| CAT-003 | `GET /api/books/{bookId}` | 표지·제목·저자·소개·전체 페이지 수만 포함하는 상세 | `Book` |
| CNS-001 | `GET /api/books/{bookId}/reading-consent`, `POST /api/books/{bookId}/reading-consent` | 동의 여부 확인, 최초 열람 동의 등록 | `ReadingConsent` |
| VIEW-001 | `POST /api/books/{bookId}/reading-sessions`, `PATCH /api/reading-sessions/current/page` | 뷰어 열기(첫 페이지), 이전/다음/직접 페이지 이동 | `ReadingSession` |
| VIEW-002 | `POST /api/books/{bookId}/reading-sessions` | 새 세션을 열면 독자당 1행인 `ReadingSession`을 새 토큰으로 교체 | `ReadingSession` |
| BILL-001 | `POST /api/reading-sessions/current/confirmations` | 서버 시각 기준 `page_opened_at` 경과가 6초 이상일 때만 확정 승인 | `ReadingSession`, `ConfirmedPage` |
| BILL-002 | `POST /api/reading-sessions/current/confirmations` | `ConfirmedPage`의 `(reader_id, book_id, page_number)` 유니크 제약으로 최초 1회만 50P 차감 | `ConfirmedPage`, `PointAccount`, `PointLedger` |
| BILL-003 | `POST /api/reading-sessions/current/confirmations` | 탭 전환·최소화는 별도 이벤트를 보내지 않으므로 `page_opened_at`이 유지되고, 확정 요청 시점의 서버 경과 시간만으로 판정 | `ReadingSession` |
| BILL-004 | `PATCH /api/reading-sessions/current/page` | 6초 전 페이지 이동/뷰어 종료 시 클라이언트가 확정 요청을 보내지 않아 차감이 발생하지 않음 | `ReadingSession` |
| BILL-005 | `POST /api/books/{bookId}/reading-sessions`, `PATCH /api/reading-sessions/current/page` | 새 페이지 진입 전 `PointAccount.balance`가 50P 미만이면 페이지 이미지를 반환하지 않고 차단 | `PointAccount` |
| BILL-006 | `POST /api/reading-sessions/current/confirmations` | 차감 실패 시 열람 미확정 상태를 유지하고, 동일 요청 재시도를 멱등하게 허용 | `ReadingSession`, `ConfirmedPage`, `PointAccount`, `PointLedger` |
| LIB-001 | `POST /api/reading-sessions/current/confirmations`(부수효과), `GET /api/library` | 최초 확정 시 서재 자동 추가, 목록·마지막 확정 페이지 조회 | `LibraryEntry` |
| PTS-001 | `POST /api/reading-sessions/current/confirmations`(부수효과), `GET /api/points/balance`, `GET /api/points/ledger` | 모든 증감을 내역으로 남기고 잔액=지급 합계-차감 합계를 일치시킴 | `PointAccount`, `PointLedger` |
| PTS-002 | `GET /api/points/ledger` | 책 제목·페이지 번호·차감 포인트·시각·차감 후 잔액을 포함한 페이지별 차감 상세 조회 | `PointLedger` |

### 엔드포인트 상세

아래 표의 "인증"은 인증된 독자 식별이 필요한지 여부만을 뜻하며, 구체적인 인증 스킴은 이 ADR의
범위가 아닙니다(맥락 절 참고).

| 메서드 · 경로 | 요청 DTO | 응답 DTO | 인증 |
| --- | --- | --- | --- |
| `POST /api/auth/signup` | `SignUpReaderRequest`(email, password) | `SignUpReaderResponse`(readerId, email) | 아니오 |
| `POST /api/auth/login` | `LoginReaderRequest`(email, password) | `LoginReaderResponse`(readerId, email) | 아니오 |
| `POST /api/auth/logout` | 없음 | `LogoutReaderResponse`(readerId) | 예 |
| `GET /api/books` (쿼리 `page`, `keyword?`) | 없음(쿼리 파라미터) | `ListBooksResponse`(books[], page, totalPages, totalCount) | 아니오 |
| `GET /api/books/{bookId}` | 없음 | `GetBookDetailResponse`(bookId, title, author, description, coverImagePath, totalPageCount) | 아니오 |
| `GET /api/books/{bookId}/reading-consent` | 없음 | `GetReadingConsentResponse`(agreed, consentedAt?) | 예 |
| `POST /api/books/{bookId}/reading-consent` | 없음(경로의 `bookId`와 인증된 독자로 식별) | `AgreeReadingConsentResponse`(bookId, consentedAt) | 예 |
| `POST /api/books/{bookId}/reading-sessions` | `OpenReadingSessionRequest`(pageNumber) | `OpenReadingSessionResponse`(sessionToken, bookId, pageNumber, pageImageUrl, pageOpenedAt) | 예 |
| `PATCH /api/reading-sessions/current/page` | `MoveReadingPageRequest`(sessionToken, pageNumber) | `MoveReadingPageResponse`(pageNumber, pageImageUrl, pageOpenedAt, alreadyConfirmed) | 예 |
| `POST /api/reading-sessions/current/confirmations` | `ConfirmReadingRequest`(sessionToken, bookId, pageNumber) | `ConfirmReadingResponse`(pageNumber, deductedAmount, balanceAfter, confirmedAt) | 예 |
| `GET /api/library` | 없음 | `GetLibraryResponse`(entries[]: bookId, title, lastConfirmedPageNumber) | 예 |
| `GET /api/points/balance` | 없음 | `GetPointBalanceResponse`(balance) | 예 |
| `GET /api/points/ledger` (쿼리 `page`) | 없음(쿼리 파라미터) | `GetPointLedgerResponse`(entries[]: type, amount, balanceAfter, bookTitle?, pageNumber?, occurredAt, page, totalPages, totalCount) | 예 |

`PATCH /api/reading-sessions/current/page`와 `POST /api/reading-sessions/current/confirmations`는
모두 인증된 독자의 활성 `ReadingSession`이 있어야 처리됩니다. 세션을 연 적이 없거나 다른 세션으로
이미 교체된 상태에서 두 엔드포인트를 호출하면 404 Not Found로 거부합니다(오류 응답 형식 절 참고).

`PATCH /api/reading-sessions/current/page`가 현재 세션의 `current_page_number`와 같은 `pageNumber`를
받으면 페이지 이동으로 처리하지 않습니다. `page_opened_at`을 그대로 유지하고 응답의 `pageOpenedAt`도
갱신 전 값을 그대로 반환해, 같은 페이지에 머무는 동안 다시 호출해도 6초 판정이 초기화되지 않게 합니다
(ADR-0001, ADR-0007의 `ReadingSession` 절, BILL-003과 같은 원칙).

`POST /api/reading-sessions/current/confirmations`(BILL-001~006)는 하나의 요청 안에서
INV-001~005와 다음과 같이 직접 연결됩니다.

- **INV-001 포인트 보존**: 차감 성공 시 `PointAccount.balance`와 `PointLedger.balanceAfter`를 같은
  값으로 갱신해 잔액과 내역 합계가 항상 일치하게 합니다.
- **INV-002 중복 차감 금지**: `ConfirmedPage`의 유니크 제약으로 같은 요청을 반복해도(T-BILL-007)
  차감 내역이 하나만 남습니다. 이미 확정된 페이지에 대한 확정 요청은 차감 없이 성공 응답만
  반환합니다(BILL-002, T-BILL-006). 이때 `ConfirmReadingResponse`의 `deductedAmount`는 `0`,
  `balanceAfter`는 요청 시점의 현재 `PointAccount.balance`를 그대로 반환합니다(처리 순서는 아래
  "확정 요청 처리 순서" 절 참고).
- **INV-003 원자성**: `PointAccount` 차감, `PointLedger` 생성, `ConfirmedPage` 생성,
  `LibraryEntry` 갱신을 하나의 트랜잭션에서 처리합니다(Facade/트랜잭션 절). 중간에 실패하면 전부
  롤백되어 열람도 확정되지 않습니다(BILL-006, T-ERR-001).
- **INV-004 음수 잔액 금지**: 차감 직전 `PointAccount` 행을 잠그고 잔액을 재확인해 0P 아래로
  내려가는 차감을 거부합니다(BILL-005).
- **INV-005 내역 불변**: `PointLedger`는 생성만 하고 이 API를 포함한 어떤 엔드포인트에서도 수정·삭제
  기능을 제공하지 않습니다.

또한 `sessionToken`이 현재 `ReadingSession` 행의 값과 다르면(이전 세션의 지연 요청, T-BILL-008)
확정 요청을 거부해 VIEW-002·BILL-002가 함께 지켜지도록 합니다. 같은 이유로 요청 바디의 `bookId`,
`pageNumber`가 서버 세션의 `current_page_number`·`book_id`와 다르면(예: 페이지 이동 직후 지연
도착한 이전 페이지의 확정 요청) 확정 요청을 거부합니다. `sessionToken` 불일치와 마찬가지로 409
Conflict로 응답합니다(오류 응답 형식 절).

### 확정 요청 처리 순서

`POST /api/reading-sessions/current/confirmations`는 INV-002·INV-003·INV-004를 함께 지키기 위해
Facade 트랜잭션 안에서 아래 순서로 처리합니다(conventions.md Facade와 트랜잭션 절).

1. 활성 `ReadingSession` 존재, `sessionToken` 일치, 요청 바디의 `bookId`·`pageNumber`와 세션의
   현재 페이지 일치를 검증합니다. 하나라도 어긋나면 404 또는 409로 거부하고 이후 단계를 진행하지
   않습니다.
2. 서버 기준 `page_opened_at` 경과가 6초 이상인지 검증합니다(BILL-001). 미만이면 409로 거부합니다.
3. `ConfirmedPage`에 `(reader_id, book_id, page_number)` 행이 이미 있는지 조회합니다. 있으면
   `PointAccount`를 잠그지 않고 차감 없이 성공 응답을 반환합니다(`deductedAmount=0`,
   `balanceAfter`는 현재 잔액, T-BILL-006·T-BILL-007).
4. 없으면 그때 `PointAccount` 행을 `SELECT ... FOR UPDATE`로 잠그고 잔액을 재확인한 뒤 차감,
   `PointLedger` 생성, `ConfirmedPage` 삽입, `LibraryEntry` 갱신을 같은 트랜잭션에서 수행합니다
   (INV-003, INV-004).
5. 3~4단계 사이의 동시 확정 경합으로 `ConfirmedPage` 삽입이 유니크 제약을 위반하면, 이미 다른
   요청이 같은 페이지를 확정한 것으로 간주해 3단계와 같은 응답을 반환합니다. 경합 발생 여부를
   같은 트랜잭션 안에서 즉시 판단해야 하므로 이 삽입에는 `saveAndFlush()`를 사용합니다
   (conventions.md Service 절의 "즉시 반영이 계약상 필요한 경우" 예외에 해당).

3단계에서 먼저 `ConfirmedPage`를 확인해 이미 확정된 페이지라면 `PointAccount`를 잠그지 않으므로,
재확인 요청이 몰려도 불필요한 행 잠금 경합이 생기지 않습니다. 이 처리 순서는 Spring Boot 4.1 +
Spring Data JPA + MySQL 8.4, ADR-0006의 Facade/트랜잭션 경계와 충돌 없이 MVP 범위에서 실현
가능합니다.

### 오류 응답 형식

공통 성공 응답 래퍼는 여전히 만들지 않습니다(conventions.md). 각 엔드포인트는 위 표의 응답 DTO를
그대로 JSON 최상위 바디로 반환합니다.

오류 응답은 `@RestControllerAdvice`가 모든 도메인 예외를 아래 공통 바디로 변환합니다.

```json
{
  "code": "INSUFFICIENT_POINT",
  "message": "포인트가 부족합니다."
}
```

- `code`: 실패한 규칙이 드러나는 SCREAMING_SNAKE_CASE 식별자(예외 클래스 이름 기반, 예:
  `InsufficientPointException` → `INSUFFICIENT_POINT`).
- `message`: 사용자에게 보여줘도 되는 한국어 설명. 토큰·비밀번호 등 민감값은 담지 않습니다
  (conventions.md "설정, 시간, 로그").
- 필드 단위 검증 오류 목록(`errors[]` 등)은 이 ADR에서 정하지 않으며, Request DTO에 Bean
  Validation을 실제로 붙이는 구현 시점에 필요하면 추가합니다.

도메인 예외 → HTTP 상태 매핑 원칙은 다음과 같습니다.

| 상황 | HTTP 상태 | 예 |
| --- | --- | --- |
| 요청 형식 오류(Bean Validation 실패, JSON 파싱 실패) | 400 Bad Request | 빈 `pageNumber`, 형식이 아닌 `email`, 0 이하인 `pageNumber` |
| 인증 정보 없음·무효 | 401 Unauthorized | 로그인하지 않고 `POST .../confirmations` 호출 |
| 인증은 됐지만 요청한 동작을 수행할 조건을 충족하지 못함 | 403 Forbidden | 최초 열람 동의(CNS-001) 없이 `POST /api/books/{bookId}/reading-sessions` 호출 |
| 요청 대상 리소스가 없음 | 404 Not Found | 존재하지 않는 `bookId`, `Book.totalPageCount`를 초과하는 `pageNumber`, 활성 `ReadingSession`이 없는 상태에서 `PATCH .../current/page` 또는 `POST .../confirmations` 호출 |
| 요청은 유효하나 현재 리소스 상태와 충돌 | 409 Conflict | 만료·교체된 `sessionToken`으로 확정 요청(T-BILL-008), 서버 경과 6초 미만 확정 시도(BILL-001), 이미 동의한 책에 재동의 요청, 요청 바디의 `bookId`·`pageNumber`가 서버 세션의 현재 페이지와 다른 확정 요청 |
| 요청·리소스 상태는 정상이나 비즈니스 규칙 위반 | 422 Unprocessable Entity | 잔액 부족으로 새 페이지 진입 차단(BILL-005) |
| 서버 내부 오류 | 500 Internal Server Error | 예기치 못한 예외, 차감 처리 중 DB 오류(T-ERR-001) |

다른 독자가 소유한 자원을 경로 파라미터로 지정해 조회하는 엔드포인트는 이 계약에 없습니다.
`GET /api/points/ledger`, `GET /api/library` 등은 모두 인증된 독자 식별자로만 범위를 지정하므로
다른 독자의 자원을 지정할 방법이 없고, 조회 결과는 항상 요청자 본인 것만 반환합니다(test-strategy.md
7절). 이후 다른 독자의 자원을 경로 파라미터로 노출하는 엔드포인트가 추가되면 위 403 행을 그 경우에도
적용합니다.

## 결과

- 이후 Controller·DTO·`@RestControllerAdvice`를 작성할 때 이 문서를 1차 기준으로 삼습니다.
- `conventions.md`의 "공통 성공 응답 래퍼는 API 계약이 정해지기 전에 만들지 않는다"는 조건을 이
  ADR로 충족했으므로, 이후 구현에서는 위에서 정한 성공/오류 응답 형식을 그대로 따릅니다.
- 인증 스킴(토큰 발급·전달 방식)이 확정되면 "인증 필요" 여부는 유지한 채 구체적인 헤더/쿠키 규약만
  별도 결정(README "다음 결정" 2번) 또는 새 ADR로 추가합니다. 이 문서를 고치지 않습니다.
- 세션 오픈과 페이지 이동을 분리한 결정은 실제 구현에서 프런트엔드가 두 요청을 매번 순차 호출하는
  비용이 크다고 판명되면 재검토가 필요합니다. 필요해지면 새 ADR로 대체 여부를 결정합니다
  (AGENTS.md 2절).
- 이 ADR은 `제안됨` 상태의 초안입니다. 결정자 검토와 승인 후에만 `상태: 승인됨`으로 바꾸고, 그 전까지
  실제 Controller 구현의 확정 근거로 사용하지 않습니다.
- 비즈니스 로직·예외 케이스·실현 가능성 관점 팀 검토(SCRUM-86) 완료, 발견 사항 반영. 오류 상태 매핑
  누락(CNS-001 미동의, 페이지 번호 범위, 활성 세션 없음, 확정 요청 바디 불일치)과 확정 요청 재시도
  응답 값을 이번 검토로 확정했습니다.
