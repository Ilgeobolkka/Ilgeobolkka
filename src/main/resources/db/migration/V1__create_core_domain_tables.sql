-- 2026-07-26 승인한 잉크·30일 페이지 대여·도서 원가 직접 결제 소장 모델을 최초 기준선으로 생성한다.
-- 모든 시각은 UTC로 읽고 쓰며, 경계 판정을 보존하도록 DATETIME(6)을 사용한다.

CREATE TABLE reader (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_reader_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE book (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    category          VARCHAR(100)  NOT NULL,
    title             VARCHAR(255)  NOT NULL,
    author            VARCHAR(255)  NOT NULL,
    description       VARCHAR(2000) NULL,
    cover_image_path  VARCHAR(500)  NULL,
    total_page_count  INT           NOT NULL,
    price_won         INT           NOT NULL,
    CONSTRAINT uk_book_id_price UNIQUE (id, price_won),
    CONSTRAINT ck_book_category_not_blank CHECK (CHAR_LENGTH(TRIM(category)) > 0),
    CONSTRAINT ck_book_total_page_count_positive CHECK (total_page_count > 0),
    CONSTRAINT ck_book_price_won_positive CHECK (price_won > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE book_page (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id       BIGINT       NOT NULL,
    page_number   INT          NOT NULL,
    content_type  VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    text_content  TEXT         NULL,
    image_path    VARCHAR(500) NULL,
    CONSTRAINT uk_book_page_book_number UNIQUE (book_id, page_number),
    CONSTRAINT ck_book_page_number_positive CHECK (page_number > 0),
    CONSTRAINT ck_book_page_content_shape CHECK (
        (content_type = 'TEXT' AND text_content IS NOT NULL AND image_path IS NULL)
        OR
        (content_type = 'IMAGE' AND text_content IS NULL AND image_path IS NOT NULL)
    ),
    CONSTRAINT fk_book_page_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE reading_session (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id             BIGINT       NOT NULL,
    book_id               BIGINT       NOT NULL,
    current_page_number   INT          NOT NULL,
    viewer_session_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    CONSTRAINT uk_reading_session_reader UNIQUE (reader_id),
    CONSTRAINT uk_reading_session_viewer UNIQUE (viewer_session_id),
    CONSTRAINT ck_reading_session_current_page_positive CHECK (current_page_number > 0),
    CONSTRAINT fk_reading_session_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_reading_session_book_page
        FOREIGN KEY (book_id, current_page_number)
        REFERENCES book_page (book_id, page_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ink_account (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id  BIGINT NOT NULL,
    balance    INT    NOT NULL,
    CONSTRAINT uk_ink_account_reader UNIQUE (reader_id),
    CONSTRAINT ck_ink_account_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT fk_ink_account_reader FOREIGN KEY (reader_id) REFERENCES reader (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ink_purchase (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id     BIGINT       NOT NULL,
    payment_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_won    INT          NOT NULL,
    granted_ink   INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    paid_at       DATETIME(6)  NULL,
    CONSTRAINT uk_ink_purchase_payment UNIQUE (payment_id),
    CONSTRAINT uk_ink_purchase_reader_id UNIQUE (reader_id, id),
    CONSTRAINT ck_ink_purchase_package CHECK (amount_won = 1000 AND granted_ink = 100),
    CONSTRAINT ck_ink_purchase_status CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    CONSTRAINT ck_ink_purchase_paid_at CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL)
        OR
        (status IN ('PENDING', 'FAILED') AND paid_at IS NULL)
    ),
    CONSTRAINT fk_ink_purchase_reader FOREIGN KEY (reader_id) REFERENCES reader (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE page_rental (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id     BIGINT      NOT NULL,
    book_page_id  BIGINT      NOT NULL,
    rented_at     DATETIME(6) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    CONSTRAINT uk_page_rental_reader_id UNIQUE (reader_id, id),
    CONSTRAINT ck_page_rental_period CHECK (rented_at < expires_at),
    CONSTRAINT fk_page_rental_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_page_rental_book_page FOREIGN KEY (book_page_id) REFERENCES book_page (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ink_ledger (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id        BIGINT      NOT NULL,
    type             VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount           INT         NOT NULL,
    balance_after    INT         NOT NULL,
    ink_purchase_id  BIGINT      NULL,
    page_rental_id   BIGINT      NULL,
    occurred_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_ink_ledger_purchase UNIQUE (ink_purchase_id),
    CONSTRAINT uk_ink_ledger_rental UNIQUE (page_rental_id),
    CONSTRAINT ck_ink_ledger_balance_after_non_negative CHECK (balance_after >= 0),
    CONSTRAINT ck_ink_ledger_entry_shape CHECK (
        (type = 'GRANT' AND amount = 100 AND ink_purchase_id IS NOT NULL AND page_rental_id IS NULL)
        OR
        (type = 'DEDUCTION' AND amount = 1 AND ink_purchase_id IS NULL AND page_rental_id IS NOT NULL)
    ),
    CONSTRAINT fk_ink_ledger_purchase
        FOREIGN KEY (reader_id, ink_purchase_id)
        REFERENCES ink_purchase (reader_id, id),
    CONSTRAINT fk_ink_ledger_rental
        FOREIGN KEY (reader_id, page_rental_id)
        REFERENCES page_rental (reader_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ownership_payment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id   BIGINT       NOT NULL,
    book_id     BIGINT       NOT NULL,
    payment_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount_won  INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    paid_at     DATETIME(6)  NULL,
    CONSTRAINT uk_ownership_payment_provider_id UNIQUE (payment_id),
    CONSTRAINT uk_ownership_payment_owner UNIQUE (reader_id, book_id, id),
    CONSTRAINT ck_ownership_payment_status CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    CONSTRAINT ck_ownership_payment_paid_at CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL)
        OR
        (status IN ('PENDING', 'FAILED') AND paid_at IS NULL)
    ),
    CONSTRAINT fk_ownership_payment_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_ownership_payment_book_price
        FOREIGN KEY (book_id, amount_won)
        REFERENCES book (id, price_won)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE book_ownership (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id             BIGINT      NOT NULL,
    book_id               BIGINT      NOT NULL,
    ownership_payment_id  BIGINT      NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_book_ownership_reader_book UNIQUE (reader_id, book_id),
    CONSTRAINT uk_book_ownership_payment UNIQUE (ownership_payment_id),
    CONSTRAINT fk_book_ownership_payment
        FOREIGN KEY (reader_id, book_id, ownership_payment_id)
        REFERENCES ownership_payment (reader_id, book_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE library_entry (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id         BIGINT      NOT NULL,
    book_id           BIGINT      NOT NULL,
    last_page_number  INT         NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    CONSTRAINT uk_library_entry_reader_book UNIQUE (reader_id, book_id),
    CONSTRAINT ck_library_entry_last_page_positive CHECK (last_page_number > 0),
    CONSTRAINT fk_library_entry_reader FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_library_entry_book_page
        FOREIGN KEY (book_id, last_page_number)
        REFERENCES book_page (book_id, page_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
