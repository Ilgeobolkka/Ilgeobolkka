-- book_page.ai_route_candidate: 이 페이지를 AI 경로 후보 집합에 넣는가.
-- V2가 나간 뒤 ERD에 추가된 컬럼이라 V2를 고치지 않고 새 번호로 더한다.
-- 기존 행은 기본값 0으로 backfill된다 — initial-v1 페이지는 후보가 아니다.
ALTER TABLE book_page
    ADD COLUMN ai_route_candidate TINYINT(1) NOT NULL DEFAULT 0,
    -- 후보 페이지는 ERD가 요구하는 일곱 필드를 모두 갖고, 후보가 아닌 페이지는 임베딩을 갖지 않는다.
    -- 후보 검색이 이 값으로 대상을 고른 뒤 벡터 유효성을 검증하므로, 값과 벡터가 어긋난 행이
    -- 저장되면 조용히 건너뛰거나 실패하는 페이지가 생긴다. 오적재를 저장 시점에 막는다.
    ADD CONSTRAINT ck_book_page_candidate_metadata CHECK (
        (
            ai_route_candidate = 0
            AND embedding_model IS NULL
            AND embedding_dimensions IS NULL
            AND embedding_json IS NULL
        )
        OR (
            ai_route_candidate = 1
            AND ai_analysis_text IS NOT NULL
            AND ai_public_guide_topic IS NOT NULL
            AND estimated_reading_seconds IS NOT NULL
            AND embedding_model IS NOT NULL
            AND embedding_dimensions IS NOT NULL
            AND embedding_json IS NOT NULL
            AND duplicate_group_keys IS NOT NULL
        )
    );
