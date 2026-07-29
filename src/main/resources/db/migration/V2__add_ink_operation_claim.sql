CREATE INDEX idx_ink_ledger_reader_occurred_id
    ON ink_ledger (reader_id, occurred_at DESC, id DESC);

CREATE TABLE ink_operation_claim (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    reader_id        BIGINT      NOT NULL,
    ink_purchase_id  BIGINT      NULL,
    page_rental_id   BIGINT      NULL,
    claim_token      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT uk_ink_operation_claim_purchase UNIQUE (ink_purchase_id),
    CONSTRAINT uk_ink_operation_claim_rental UNIQUE (page_rental_id),
    CONSTRAINT uk_ink_operation_claim_token UNIQUE (claim_token),
    CONSTRAINT ck_ink_operation_claim_source CHECK (
        (ink_purchase_id IS NOT NULL AND page_rental_id IS NULL)
        OR
        (ink_purchase_id IS NULL AND page_rental_id IS NOT NULL)
    ),
    CONSTRAINT fk_ink_operation_claim_purchase
        FOREIGN KEY (reader_id, ink_purchase_id)
        REFERENCES ink_purchase (reader_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ink_operation_claim_rental
        FOREIGN KEY (reader_id, page_rental_id)
        REFERENCES page_rental (reader_id, id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO ink_operation_claim
    (reader_id, ink_purchase_id, claim_token)
SELECT reader_id, ink_purchase_id, UUID()
FROM ink_ledger
WHERE ink_purchase_id IS NOT NULL;

INSERT INTO ink_operation_claim
    (reader_id, page_rental_id, claim_token)
SELECT reader_id, page_rental_id, UUID()
FROM ink_ledger
WHERE page_rental_id IS NOT NULL;
