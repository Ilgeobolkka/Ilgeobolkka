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
    INK_PURCHASE ||--o| INK_LEDGER : grants
    PAGE_RENTAL ||--o| INK_LEDGER : charges
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
외래 키를 나탅니다. `reading_session` 및 `library_entry`의 `(book_id, current_page_number)`,
`(book_id, last_page_number)`는 각각 `book_page(book_id, page_number)`를 참조하므로 `BOOK_PAGE`와 연결합니다.

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
