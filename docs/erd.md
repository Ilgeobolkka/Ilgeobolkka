# 읽어볼까 MVP ERD

[PRD 색인](./prd/README.md)으로 돌아갑니다. 이 문서는 합의된 제품 정책을 구현하기 위한 목표 논리 데이터
모델의 정본입니다. 테이블·컬럼·인덱스 변경은 Flyway migration으로 구현합니다.

## 구현 상태

현재 `V1` migration이 아래 잉크·30일 대여·도서 원가 직접 결제 모델의 11개 테이블과 핵심 제약을
생성합니다. 아직 JPA Entity와 도메인 기능은 구현 전이므로, 물리 스키마가 준비됐다는 의미이며 제품
기능이 완료됐다는 의미는 아닙니다.

## 외래 키 관계

```mermaid
erDiagram
    READER ||--o| INK_ACCOUNT : owns
    READER ||--o| READING_SESSION : has_current
    READER ||--o{ INK_PURCHASE : purchases
    READER ||--o{ PAGE_RENTAL : rents
    READER ||--o{ OWNERSHIP_PAYMENT : pays
    READER ||--o{ LIBRARY_ENTRY : keeps

    BOOK ||--o{ BOOK_PAGE : contains
    BOOK ||--o{ OWNERSHIP_PAYMENT : paid_for

    BOOK_PAGE ||--o{ PAGE_RENTAL : grants_access_to
    BOOK_PAGE ||--o{ READING_SESSION : current_in
    BOOK_PAGE ||--o{ LIBRARY_ENTRY : last_read_in
    INK_PURCHASE |o--o| INK_LEDGER : grants
    PAGE_RENTAL |o--o| INK_LEDGER : charges
    OWNERSHIP_PAYMENT ||--o| BOOK_OWNERSHIP : grants

    READER {
        bigint id PK
        varchar_255 email UK
        varchar_255 password_hash
        datetime_6 created_at
    }
    BOOK {
        bigint id PK
        varchar_100 category
        varchar_255 title
        varchar_255 author
        varchar_2000 description
        varchar_500 cover_image_path
        int total_page_count
        int price_won
    }
    BOOK_PAGE {
        bigint id PK
        bigint book_id FK
        int page_number
        varchar_20 content_type
        text text_content
        varchar_500 image_path
    }
    READING_SESSION {
        bigint id PK
        bigint reader_id FK
        bigint book_id FK
        int current_page_number
        char_36 viewer_session_id UK
        datetime_6 updated_at
    }
    INK_ACCOUNT {
        bigint id PK
        bigint reader_id FK
        int balance
    }
    INK_PURCHASE {
        bigint id PK
        bigint reader_id FK
        char_36 payment_id UK
        varchar_20 status
        int amount_won
        int granted_ink
        datetime_6 created_at
        datetime_6 paid_at
    }
    INK_LEDGER {
        bigint id PK
        bigint reader_id FK
        varchar_20 type
        int amount
        int balance_after
        bigint ink_purchase_id FK
        bigint page_rental_id FK
        datetime_6 occurred_at
    }
    PAGE_RENTAL {
        bigint id PK
        bigint reader_id FK
        bigint book_page_id FK
        datetime_6 rented_at
        datetime_6 expires_at
    }
    OWNERSHIP_PAYMENT {
        bigint id PK
        bigint reader_id FK
        bigint book_id FK
        char_36 payment_id UK
        varchar_20 status
        int amount_won
        datetime_6 created_at
        datetime_6 paid_at
    }
    BOOK_OWNERSHIP {
        bigint id PK
        bigint reader_id FK
        bigint book_id FK
        bigint ownership_payment_id FK
        datetime_6 created_at
    }
    LIBRARY_ENTRY {
        bigint id PK
        bigint reader_id FK
        bigint book_id FK
        int last_page_number
        datetime_6 updated_at
    }
```

Mermaid에서 괄호가 있는 SQL 타입을 안정적으로 표시하기 위해 `varchar_255`, `char_36`, `datetime_6`처럼
표기했습니다. 각각 물리 스키마의 `VARCHAR(255)`, `CHAR(36)`, `DATETIME(6)`을 뜻합니다. 관계선은 실제
외래 키를 나타냅니다. `reading_session` 및 `library_entry`의 `(book_id, current_page_number)`,
`(book_id, last_page_number)`는 각각 `book_page(book_id, page_number)`를 참조하므로 `BOOK_PAGE`와 연결합니다.

`ink_ledger`는 지급 원인인 `ink_purchase`와 차감 원인인 `page_rental` 중 정확히 하나만 참조합니다. 따라서
두 원인 관계의 `|o`는 원장 한 건에서 해당 원인이 없거나 하나임을, `o|`는 하나의 원인이 원장에 연결되지
않았거나 한 번만 연결됨을 뜻합니다.

## 테이블 명세

아래 명세는 현재 `V1` migration의 물리 컬럼을 텍스트로 풀어 쓴 것입니다. `NULL`은 컬럼의 `NULL` 허용
여부이며, 복합 키와 값 사이 제약은 이어지는 [핵심 제약조건](#핵심-제약조건)에서 함께 설명합니다.

### `reader`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 독자 식별자 |
| `email` | `VARCHAR(255)` | 아니오 | UK | 정규화해 저장하는 로그인 이메일 |
| `password_hash` | `VARCHAR(255)` | 아니오 | - | 적응형 단방향 함수로 인코딩한 비밀번호 |
| `created_at` | `DATETIME(6)` | 아니오 | - | 계정 생성 시각(UTC) |

### `book`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK, `(id, price_won)` UK 구성 | 도서 식별자 |
| `category` | `VARCHAR(100)` | 아니오 | - | 목록 정렬에 사용하는 카테고리 |
| `title` | `VARCHAR(255)` | 아니오 | - | 도서 제목 |
| `author` | `VARCHAR(255)` | 아니오 | - | 저자명 |
| `description` | `VARCHAR(2000)` | 예 | - | 도서 상세 소개 |
| `cover_image_path` | `VARCHAR(500)` | 예 | - | 공개 표지 자산의 same-origin 경로 |
| `total_page_count` | `INT` | 아니오 | - | 원본 PDF 기준 전체 페이지 수 |
| `price_won` | `INT` | 아니오 | `(id, price_won)` UK 구성 | 온라인 소장에 사용하는 고정 원가(원) |

### `book_page`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 변환 페이지 식별자 |
| `book_id` | `BIGINT` | 아니오 | FK → `book.id`, `(book_id, page_number)` UK 구성 | 소속 도서 |
| `page_number` | `INT` | 아니오 | `(book_id, page_number)` UK 구성 | 원본 PDF와 같은 페이지 번호 |
| `content_type` | `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | - | `TEXT` 또는 `IMAGE` |
| `text_content` | `TEXT` | 예 | - | 텍스트 페이지 본문 |
| `image_path` | `VARCHAR(500)` | 예 | - | 이미지 페이지의 비공개 저장소 경로 |

### `reading_session`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 현재 열람 세션 식별자 |
| `reader_id` | `BIGINT` | 아니오 | UK, FK → `reader.id` | 독자당 하나인 현재 열람 세션의 소유자 |
| `book_id` | `BIGINT` | 아니오 | 복합 FK → `book_page(book_id, page_number)` | 현재 도서 |
| `current_page_number` | `INT` | 아니오 | 복합 FK → `book_page(book_id, page_number)` | 현재 원본 PDF 페이지 번호 |
| `viewer_session_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | UK | 서버가 발급한 뷰어 교체 판정용 UUID |
| `updated_at` | `DATETIME(6)` | 아니오 | - | 현재 위치 갱신 시각(UTC) |

### `ink_account`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 잉크 계정 식별자 |
| `reader_id` | `BIGINT` | 아니오 | UK, FK → `reader.id` | 잉크 계정 소유자 |
| `balance` | `INT` | 아니오 | - | 현재 사용 가능한 잉크 잔액 |

### `ink_purchase`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK, `(reader_id, id)` UK 구성 | 잉크 구매 시도 식별자 |
| `reader_id` | `BIGINT` | 아니오 | FK → `reader.id`, `(reader_id, id)` UK 구성 | 구매 독자 |
| `payment_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | UK | 서버가 발급한 PortOne 결제 UUID |
| `status` | `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | - | `PENDING`, `PAID`, `FAILED` 중 하나 |
| `amount_won` | `INT` | 아니오 | - | 결제 준비 금액(원) |
| `granted_ink` | `INT` | 아니오 | - | 검증 성공 시 지급할 잉크 수량 |
| `created_at` | `DATETIME(6)` | 아니오 | - | 결제 시도 생성 시각(UTC) |
| `paid_at` | `DATETIME(6)` | 예 | - | `PAID`가 된 시각(UTC) |

### `page_rental`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK, `(reader_id, id)` UK 구성 | 페이지 대여 기간 식별자 |
| `reader_id` | `BIGINT` | 아니오 | FK → `reader.id`, `(reader_id, id)` UK 구성 | 대여 독자 |
| `book_page_id` | `BIGINT` | 아니오 | FK → `book_page.id` | 대여한 원본 PDF 페이지 |
| `rented_at` | `DATETIME(6)` | 아니오 | - | 1잉크 차감이 완료된 대여 시작 시각(UTC) |
| `expires_at` | `DATETIME(6)` | 아니오 | - | 대여 만료 시각(UTC) |

### `ink_ledger`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 변경 불가능한 잉크 내역 식별자 |
| `reader_id` | `BIGINT` | 아니오 | 두 복합 FK 구성 | 지급 또는 차감 원인과 같은 독자 |
| `type` | `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | - | `GRANT` 또는 `DEDUCTION` |
| `amount` | `INT` | 아니오 | - | 부호 없는 지급·차감 수량 |
| `balance_after` | `INT` | 아니오 | - | 이 내역 반영 직후 잔액 |
| `ink_purchase_id` | `BIGINT` | 예 | UK, 복합 FK → `ink_purchase(reader_id, id)` | `GRANT`의 지급 원인 |
| `page_rental_id` | `BIGINT` | 예 | UK, 복합 FK → `page_rental(reader_id, id)` | `DEDUCTION`의 차감 원인 |
| `occurred_at` | `DATETIME(6)` | 아니오 | - | 잉크 변경 발생 시각(UTC) |

### `ownership_payment`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK, `(reader_id, book_id, id)` UK 구성 | 소장 결제 시도 식별자 |
| `reader_id` | `BIGINT` | 아니오 | FK → `reader.id`, 복합 UK 구성 | 결제 독자 |
| `book_id` | `BIGINT` | 아니오 | 복합 FK → `book(id, price_won)`, 복합 UK 구성 | 소장 대상 도서 |
| `payment_id` | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | UK | 서버가 발급한 PortOne 결제 UUID |
| `status` | `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin` | 아니오 | - | `PENDING`, `PAID`, `FAILED` 중 하나 |
| `amount_won` | `INT` | 아니오 | 복합 FK → `book(id, price_won)` | 준비 시 저장한 도서 원가(원) |
| `created_at` | `DATETIME(6)` | 아니오 | - | 결제 시도 생성 시각(UTC) |
| `paid_at` | `DATETIME(6)` | 예 | - | `PAID`가 된 시각(UTC) |

### `book_ownership`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 기간 없는 온라인 소장 권한 식별자 |
| `reader_id` | `BIGINT` | 아니오 | `(reader_id, book_id)` UK, 복합 FK 구성 | 소장 독자 |
| `book_id` | `BIGINT` | 아니오 | `(reader_id, book_id)` UK, 복합 FK 구성 | 소장 도서 |
| `ownership_payment_id` | `BIGINT` | 아니오 | UK, 복합 FK → `ownership_payment(reader_id, book_id, id)` | 권한을 부여한 결제 |
| `created_at` | `DATETIME(6)` | 아니오 | - | 소장 권한 생성 시각(UTC) |

### `library_entry`

| 컬럼 | 물리 타입 | NULL | 키·참조 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 아니오 | PK | 내 서재 항목 식별자 |
| `reader_id` | `BIGINT` | 아니오 | FK → `reader.id`, `(reader_id, book_id)` UK 구성 | 서재 소유자 |
| `book_id` | `BIGINT` | 아니오 | 복합 FK → `book_page(book_id, page_number)`, 복합 UK 구성 | 서재 도서 |
| `last_page_number` | `INT` | 아니오 | 복합 FK → `book_page(book_id, page_number)` | 마지막으로 성공한 원본 PDF 페이지 번호 |
| `updated_at` | `DATETIME(6)` | 아니오 | - | 마지막 위치 갱신 시각(UTC) |

## 핵심 제약조건

| 대상 | 제약 |
| --- | --- |
| `reader` | 정규화한 `email` 고유 |
| `book` | `(id, price_won)` 고유, `total_page_count > 0`, `price_won > 0`, 비어 있지 않은 `category` |
| `book_page` | `(book_id, page_number)` 고유, `page_number > 0`, `TEXT`·`IMAGE` 중 정확히 한 콘텐츠 형식 |
| `reading_session` | `reader_id`·`viewer_session_id` 각각 고유, `current_page_number > 0`, `(book_id, current_page_number)`로 실제 `book_page` 참조 |
| `ink_account` | `reader_id` 고유, `balance >= 0` |
| `ink_purchase` | `payment_id` 고유, `PENDING`·`PAID`·`FAILED`, `PAID`만 `paid_at` 필수, 1,000원·100잉크 조합만 허용 |
| `ink_ledger` | `ink_purchase_id`·`page_rental_id` 각각 고유, 지급 100잉크 또는 대여 차감 1잉크와 원인 하나만 연결, `balance_after >= 0`, 원인과 같은 `reader_id` 보장 |
| `page_rental` | `rented_at < expires_at`, 과거 대여를 보존하므로 같은 페이지의 여러 기간 허용 |
| `ownership_payment` | `payment_id` 고유, `PENDING`·`PAID`·`FAILED`, `PAID`만 `paid_at` 필수, `(book_id, amount_won)`으로 도서 원가 일치 |
| `book_ownership` | `(reader_id, book_id)`·`ownership_payment_id` 각각 고유, 결제의 독자·도서와 소장의 독자·도서 일치 |
| `library_entry` | `(reader_id, book_id)` 고유, `last_page_number > 0`, `(book_id, last_page_number)`로 실제 `book_page` 참조 |

물리 외래 키의 삭제·수정 동작은 기본값인 `RESTRICT`이며 핵심 이력을 연쇄 삭제하지 않습니다.
모든 시각 컬럼은 UTC로 읽고 쓰는 `DATETIME(6)`입니다. `viewer_session_id`와 두 결제 테이블의
`payment_id`는 대소문자가 별개인 ASCII 값으로 비교합니다.

## 애플리케이션이 보장하는 불변식

물리 제약만으로 표현할 수 없거나 역사 테이블을 유지하려면 트랜잭션 판정이 필요한 규칙은
애플리케이션이 다음과 같이 보장합니다.

- 이메일은 정규화한 뒤 저장하고, `viewerSessionId`와 `paymentId`는 서버가 UUID로 발급합니다.
  `paymentId`는 두 결제 테이블 전체에서 고유하게 사용합니다.
- 회원가입은 `Reader`와 0잉크 `InkAccount`를 하나의 트랜잭션에서 함께 만듭니다.
- `Book.totalPageCount`는 원본 PDF 페이지 수와 같고 `BookPage.pageNumber`는 1부터
  `totalPageCount`까지 빈번호 없이 한 번씩 존재해야 합니다.
- `PageRental.expiresAt`은 `rentedAt`에서 제품 정책의 대여 기간을 더한 값입니다. 활성 대여 중복
  차감은 `InkAccount`를 잠근 뒤 소장·대여를 다시 조회해 방지합니다.
- 새 대여의 잔액 차감, `InkLedger`, `PageRental`, `LibraryEntry`는 하나의 트랜잭션에서
  함께 성공하거나 함께 실패합니다. 원장과 대여·결제 이력은 수정·삭제하지 않습니다.
- 소장 결제 준비는 `InkAccount`를 잠그고 이미 소장했는지와 같은 독자·도서의 `PENDING`을
  확인합니다. 기존 `PENDING`은 재사용하고 `FAILED`만 있을 때 새 시도를 만듭니다.
- 오직 서버 검증을 통과한 `PAID` 결제만 같은 트랜잭션에서 `InkLedger` 지급 또는
  `BookOwnership`을 하나 만듭니다. DB 외래 키는 결제 상태가 `PAID`인지까지 판정하지 않습니다.
- 소장이 먼저 완료되면 새 대여를 만들지 않고, 대여가 먼저 완료되면 해당 1잉크를 환급하지
  않습니다. 소장으로 처음 서재에 추가하는 도서에 열람 위치가 없으면 `lastPageNumber=1`로
  시작하고, 이후 성공한 페이지 열기만 마지막 위치를 변경합니다.

모든 테이블의 기본 collation은 MySQL 8의 `utf8mb4_0900_ai_ci`입니다. 따라서 도서 제목·저자 검색은
영문 대소문자를 구분하지 않으며, `%`와 `_`의 리터럴 처리는 조회 쿼리에서 별도로 이스케이프합니다.

## 콘텐츠 저장 규칙

- `Book.totalPageCount`와 해당 도서의 `BookPage` 수는 원본 PDF 페이지 수와 같아야 합니다.
- `BookPage.contentType`은 `TEXT` 또는 `IMAGE`입니다.
- `TEXT`는 `textContent`, `IMAGE`는 비공개 저장소의 `imagePath`만 사용합니다.
- 원본 PDF와 내부 저장소 주소는 공개 API에 포함하지 않습니다.

## 요구사항 추적

| 엔티티 | 요구사항 |
| --- | --- |
| `Reader`, `ReadingSession` | `AUTH-*`, `VIEW-002` |
| `Book`, `BookPage` | `CAT-*`, `VIEW-001`, `VIEW-003~005` |
| `InkAccount`, `InkPurchase`, `InkLedger` | `INK-*`, `PAY-*` |
| `PageRental` | `RENT-*` |
| `OwnershipPayment`, `BookOwnership` | `OWN-*`, `PAY-*` |
| `LibraryEntry` | `LIB-001` |

현행 데이터 구조를 선택한 배경은 [ADR 색인](./adr/README.md)에서 확인합니다. 최초 기준선 전에 폐기한
초안과 물리 모델은 Git 이력에서 확인합니다.
