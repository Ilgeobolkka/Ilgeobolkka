ALTER TABLE ai_route_generation_item
    ADD COLUMN additional_cost_status
        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER role;

-- V6 이전 임시 결과는 당시 구현이 사용하던 이력 재구성 규칙으로 한 번만 보정한다.
UPDATE ai_route_generation_item item
JOIN ai_route_generation generation
  ON generation.generation_id = item.generation_id
 AND generation.book_id = item.book_id
SET item.additional_cost_status = CASE
    WHEN EXISTS (
        SELECT 1
        FROM book_ownership ownership
        WHERE ownership.reader_id = generation.reader_id
          AND ownership.book_id = generation.book_id
          AND ownership.created_at <= generation.created_at
    ) THEN 'OWNED'
    WHEN EXISTS (
        SELECT 1
        FROM page_rental rental
        WHERE rental.reader_id = generation.reader_id
          AND rental.book_page_id = item.book_page_id
          AND rental.rented_at <= generation.created_at
          AND rental.expires_at > generation.created_at
    ) THEN 'ACTIVE_RENTAL'
    ELSE 'ONE_INK'
END;

ALTER TABLE ai_route_generation_item
    MODIFY COLUMN additional_cost_status
        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT ck_ai_route_generation_item_additional_cost_status CHECK (
        additional_cost_status IN ('ONE_INK', 'ACTIVE_RENTAL', 'OWNED')
    );
