CREATE INDEX idx_ink_ledger_reader_occurred_id
    ON ink_ledger (reader_id, occurred_at DESC, id DESC);
