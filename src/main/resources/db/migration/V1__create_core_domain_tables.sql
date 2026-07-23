-- ADR-0007(핵심 도메인 ERD)에 정의된 8개 테이블을 FK 의존 순서로 생성한다.
-- reader, book -> reading_consent, reading_session, confirmed_page, library_entry, point_account -> point_ledger
--
-- 시각 컬럼은 모두 DATETIME(6)으로 마이크로초까지 저장한다. 정밀도를 생략한 DATETIME은 소수부를
-- 반올림해 0초 정밀도가 되어(MySQL 8.4 fractional-seconds), ADR-0001의 "5.999초 거절 / 6.000초
-- 승인" 열람 확정 경계를 정확히 판정할 수 없다. 저장 시각은 UTC 기준이다(application.yaml의
-- hibernate.jdbc.time_zone=UTC, conventions.md "설정, 시간, 로그").

CREATE TABLE reader (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT uk_reader_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE book (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    title             VARCHAR(255)  NOT NULL,
    author            VARCHAR(255)  NOT NULL,
    description       VARCHAR(2000) NULL,
    cover_image_path  VARCHAR(500)  NULL,
    total_page_count  INT           NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ReadingConsent: 최초 열람 동의(CNS-001). (reader_id, book_id) 유니크로 사용자·도서별 최초 한 번만 동의를 보장한다.
CREATE TABLE reading_consent (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id     BIGINT   NOT NULL,
    book_id       BIGINT   NOT NULL,
    consented_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_reading_consent_reader_book UNIQUE (reader_id, book_id),
    CONSTRAINT fk_reading_consent_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_reading_consent_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ReadingSession: 사용자당 하나의 온라인 열람 세션(VIEW-002). reader_id 유니크로 사용자당 최대 1행만 존재한다.
CREATE TABLE reading_session (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id             BIGINT       NOT NULL,
    book_id               BIGINT       NOT NULL,
    current_page_number   INT          NOT NULL,
    page_opened_at        DATETIME(6) NOT NULL,
    session_token         VARCHAR(255) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_reading_session_reader UNIQUE (reader_id),
    CONSTRAINT fk_reading_session_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_reading_session_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ConfirmedPage: 열람 확정 페이지(BILL-002). (reader_id, book_id, page_number) 유니크 제약이
-- INV-002(사용자·도서·페이지별 최초 1회만 차감)를 DB 수준에서 보장한다.
CREATE TABLE confirmed_page (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id      BIGINT   NOT NULL,
    book_id        BIGINT   NOT NULL,
    page_number    INT      NOT NULL,
    confirmed_at   DATETIME(6) NOT NULL,
    CONSTRAINT uk_confirmed_page_reader_book_page UNIQUE (reader_id, book_id, page_number),
    CONSTRAINT fk_confirmed_page_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_confirmed_page_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- LibraryEntry: 내 서재 항목(LIB-001). (reader_id, book_id) 유니크, 누적 사용 포인트 컬럼은 두지 않는다(PRD 6.5).
CREATE TABLE library_entry (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id                   BIGINT   NOT NULL,
    book_id                     BIGINT   NOT NULL,
    last_confirmed_page_number  INT      NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    CONSTRAINT uk_library_entry_reader_book UNIQUE (reader_id, book_id),
    CONSTRAINT fk_library_entry_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_library_entry_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- PointAccount: 독자별 현재 포인트 잔액(PTS-001, INV-001, INV-004). reader_id 유니크로 독자당 한 행만 존재하고,
-- CHECK 제약으로 잔액이 0 미만이 되지 않도록 DB 수준에서도 보장한다.
CREATE TABLE point_account (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id  BIGINT NOT NULL,
    balance    INT    NOT NULL,
    CONSTRAINT uk_point_account_reader UNIQUE (reader_id),
    CONSTRAINT fk_point_account_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT ck_point_account_balance_non_negative CHECK (balance >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- PointLedger: 포인트 지급·차감 내역(PTS-001, PTS-002, INV-005). 차감 항목만 book_id, page_number를 채운다.
-- 이 테이블에 대한 수정·삭제 API는 애플리케이션 계약으로만 금지하며(INV-005), 스키마는 컬럼 존재만 보장한다.
CREATE TABLE point_ledger (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id       BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    amount          INT          NOT NULL,
    balance_after   INT          NOT NULL,
    book_id         BIGINT       NULL,
    page_number     INT          NULL,
    occurred_at     DATETIME(6) NOT NULL,
    CONSTRAINT fk_point_ledger_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_point_ledger_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT ck_point_ledger_type CHECK (type IN ('GRANT', 'DEDUCTION'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
