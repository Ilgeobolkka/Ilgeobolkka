-- ADR-0018의 90% 소장 정책을 지원한다. 승인된 V1은 수정하지 않고 기존 서재 행의 확정 페이지 수를
-- confirmed_page에서 역산한 뒤, 같은 행 안에서 검증할 수 있는 도메인 제약만 추가한다.

ALTER TABLE book
    ADD CONSTRAINT ck_book_total_page_count_positive CHECK (total_page_count > 0);

ALTER TABLE reading_session
    ADD CONSTRAINT ck_reading_session_current_page_positive CHECK (current_page_number > 0);

ALTER TABLE confirmed_page
    ADD CONSTRAINT ck_confirmed_page_number_positive CHECK (page_number > 0);

ALTER TABLE library_entry
    ADD COLUMN confirmed_page_count INT NOT NULL DEFAULT 0 AFTER last_confirmed_page_number;

UPDATE library_entry AS entry
SET confirmed_page_count = (
    SELECT COUNT(*)
    FROM confirmed_page AS confirmed
    WHERE confirmed.reader_id = entry.reader_id
      AND confirmed.book_id = entry.book_id
);

ALTER TABLE library_entry
    ADD CONSTRAINT ck_library_entry_last_page_positive CHECK (last_confirmed_page_number > 0),
    ADD CONSTRAINT ck_library_entry_confirmed_count_non_negative CHECK (confirmed_page_count >= 0);

ALTER TABLE point_ledger
    ADD CONSTRAINT ck_point_ledger_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT ck_point_ledger_balance_after_non_negative CHECK (balance_after >= 0),
    ADD CONSTRAINT ck_point_ledger_entry_shape CHECK (
        (type = 'GRANT' AND book_id IS NULL AND page_number IS NULL)
        OR
        (type = 'DEDUCTION' AND amount = 50 AND book_id IS NOT NULL AND page_number IS NOT NULL AND page_number > 0)
    );
