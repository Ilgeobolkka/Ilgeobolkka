-- 독자별 이력이 커져도 최신 대여 1건과 원장 20건을 정렬 없이 앞에서부터 읽는다.
CREATE INDEX idx_page_rental_reader_page_latest
    ON page_rental (reader_id, book_page_id, rented_at DESC, id DESC);

CREATE INDEX idx_ink_ledger_reader_occurred
    ON ink_ledger (reader_id, occurred_at DESC, id DESC);
