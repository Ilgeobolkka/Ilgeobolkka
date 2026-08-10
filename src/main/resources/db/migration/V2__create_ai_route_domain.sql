-- AI 잉크 경로 2차 MVP의 콘텐츠 메타데이터와 생성·저장 경로 스키마를 추가한다.
-- V1 기존 도서는 GATE-AIR-01에서 확정한 initial-v1로 조건 없이 backfill한다.

ALTER TABLE book
    ADD COLUMN content_version VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'initial-v1',
    ADD COLUMN ai_route_supported BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_external_transfer_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_data_policy_version VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT ck_book_ai_flags_boolean CHECK (
        ai_route_supported IN (FALSE, TRUE)
        AND ai_external_transfer_allowed IN (FALSE, TRUE)
    ),
    ADD CONSTRAINT ck_book_ai_route_support CHECK (
        ai_route_supported = FALSE
        OR (
            ai_external_transfer_allowed = TRUE
            AND ai_data_policy_version IS NOT NULL
        )
    );

ALTER TABLE book_page
    ADD COLUMN ai_analysis_text MEDIUMTEXT NULL,
    ADD COLUMN ai_public_guide_topic VARCHAR(500) NULL,
    ADD COLUMN estimated_reading_seconds INT NULL,
    ADD COLUMN embedding_model VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN embedding_dimensions INT NULL,
    ADD COLUMN embedding_json JSON NULL,
    ADD COLUMN duplicate_group_keys JSON NULL,
    ADD CONSTRAINT uk_book_page_id_book UNIQUE (id, book_id),
    ADD CONSTRAINT ck_book_page_ai_reading_seconds_positive CHECK (
        estimated_reading_seconds IS NULL OR estimated_reading_seconds > 0
    ),
    ADD CONSTRAINT ck_book_page_embedding_shape CHECK (
        (
            embedding_model IS NULL
            AND embedding_dimensions IS NULL
            AND embedding_json IS NULL
        )
        OR (
            embedding_model IS NOT NULL
            AND embedding_dimensions IS NOT NULL
            AND embedding_dimensions > 0
            AND embedding_json IS NOT NULL
            AND JSON_SCHEMA_VALID(
                '{"type":"array","items":{"type":"number"}}',
                embedding_json
            )
            AND JSON_LENGTH(embedding_json) = embedding_dimensions
        )
    ),
    ADD CONSTRAINT ck_book_page_duplicate_groups_array CHECK (
        duplicate_group_keys IS NULL
        OR JSON_SCHEMA_VALID(
            '{"type":"array","items":{"type":"string"}}',
            duplicate_group_keys
        )
    );

CREATE TABLE ai_route_prerequisite (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id                   BIGINT NOT NULL,
    prerequisite_page_number INT    NOT NULL,
    dependent_page_number    INT    NOT NULL,
    CONSTRAINT uk_ai_route_prerequisite_edge
        UNIQUE (book_id, prerequisite_page_number, dependent_page_number),
    CONSTRAINT ck_ai_route_prerequisite_distinct_pages CHECK (
        prerequisite_page_number <> dependent_page_number
    ),
    CONSTRAINT fk_ai_route_prerequisite_prerequisite_page
        FOREIGN KEY (book_id, prerequisite_page_number)
        REFERENCES book_page (book_id, page_number),
    CONSTRAINT fk_ai_route_prerequisite_dependent_page
        FOREIGN KEY (book_id, dependent_page_number)
        REFERENCES book_page (book_id, page_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_route_generation (
    generation_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    reader_id            BIGINT       NOT NULL,
    book_id              BIGINT       NOT NULL,
    content_version      VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_purpose   VARCHAR(200) NULL,
    request_type         VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    max_additional_ink   INT          NULL,
    depth                VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status               VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    no_route_reason      VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    minimum_required_ink INT          NULL,
    failure_code         VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    saved_route_id       BIGINT       NULL,
    created_at           DATETIME(6)  NOT NULL,
    completed_at         DATETIME(6)  NULL,
    expires_at           DATETIME(6)  NULL,
    CONSTRAINT uk_ai_route_generation_id_book UNIQUE (generation_id, book_id),
    CONSTRAINT uk_ai_route_generation_reader_idempotency
        UNIQUE (reader_id, idempotency_key),
    CONSTRAINT uk_ai_route_generation_saved_route UNIQUE (saved_route_id),
    CONSTRAINT ck_ai_route_generation_request_type CHECK (
        request_type IS NULL OR request_type IN ('INK_BUDGET', 'OWNED_DEPTH')
    ),
    CONSTRAINT ck_ai_route_generation_depth CHECK (
        depth IS NULL OR depth IN ('QUICK', 'BALANCED', 'DEEP')
    ),
    CONSTRAINT ck_ai_route_generation_status CHECK (
        status IN ('GENERATING', 'ROUTE', 'NO_ROUTE', 'FAILED', 'SAVED', 'CONSUMED')
    ),
    CONSTRAINT ck_ai_route_generation_no_route_reason CHECK (
        no_route_reason IS NULL
        OR no_route_reason IN ('NO_RELEVANT_PAGES', 'INSUFFICIENT_BUDGET')
    ),
    CONSTRAINT ck_ai_route_generation_request_shape CHECK (
        (
            status IN ('SAVED', 'CONSUMED')
            AND normalized_purpose IS NULL
            AND request_type IS NULL
            AND max_additional_ink IS NULL
            AND depth IS NULL
        )
        OR (
            status NOT IN ('SAVED', 'CONSUMED')
            AND normalized_purpose IS NOT NULL
            AND request_type IS NOT NULL
            AND (
                (
                    request_type = 'INK_BUDGET'
                    AND max_additional_ink IS NOT NULL
                    AND max_additional_ink >= 0
                    AND depth IS NULL
                )
                OR (
                    request_type = 'OWNED_DEPTH'
                    AND max_additional_ink IS NULL
                    AND depth IS NOT NULL
                )
            )
        )
    ),
    CONSTRAINT ck_ai_route_generation_no_route_shape CHECK (
        (
            status = 'NO_ROUTE'
            AND no_route_reason IS NOT NULL
            AND (
                (
                    no_route_reason = 'NO_RELEVANT_PAGES'
                    AND minimum_required_ink IS NULL
                )
                OR (
                    no_route_reason = 'INSUFFICIENT_BUDGET'
                    AND request_type = 'INK_BUDGET'
                    AND minimum_required_ink IS NOT NULL
                    AND minimum_required_ink > max_additional_ink
                )
            )
        )
        OR (
            status <> 'NO_ROUTE'
            AND no_route_reason IS NULL
            AND minimum_required_ink IS NULL
        )
    ),
    CONSTRAINT ck_ai_route_generation_failure_shape CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL)
    ),
    CONSTRAINT ck_ai_route_generation_saved_shape CHECK (
        (status = 'SAVED' AND saved_route_id IS NOT NULL)
        OR (status <> 'SAVED' AND saved_route_id IS NULL)
    ),
    CONSTRAINT ck_ai_route_generation_completion_shape CHECK (
        (
            status = 'GENERATING'
            AND completed_at IS NULL
            AND expires_at IS NULL
        )
        OR (
            status <> 'GENERATING'
            AND completed_at IS NOT NULL
            AND expires_at IS NOT NULL
            AND completed_at >= created_at
            AND expires_at > completed_at
        )
    ),
    CONSTRAINT fk_ai_route_generation_reader
        FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_ai_route_generation_book
        FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_route_generation_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    book_id        BIGINT       NOT NULL,
    book_page_id   BIGINT       NOT NULL,
    position       INT          NOT NULL,
    relevance      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prerequisite   BOOLEAN      NOT NULL,
    role           VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT uk_ai_route_generation_item_position UNIQUE (generation_id, position),
    CONSTRAINT uk_ai_route_generation_item_page UNIQUE (generation_id, book_page_id),
    CONSTRAINT ck_ai_route_generation_item_position_positive CHECK (position > 0),
    CONSTRAINT ck_ai_route_generation_item_relevance CHECK (
        relevance IN ('HIGH', 'MEDIUM')
    ),
    CONSTRAINT ck_ai_route_generation_item_prerequisite_boolean CHECK (
        prerequisite IN (FALSE, TRUE)
    ),
    CONSTRAINT ck_ai_route_generation_item_role CHECK (
        role IN ('PREREQUISITE', 'CORE', 'EXAMPLE', 'COUNTERPOINT', 'CONCLUSION')
    ),
    CONSTRAINT fk_ai_route_generation_item_generation_book
        FOREIGN KEY (generation_id, book_id)
        REFERENCES ai_route_generation (generation_id, book_id),
    CONSTRAINT fk_ai_route_generation_item_page
        FOREIGN KEY (book_page_id, book_id) REFERENCES book_page (id, book_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_reading_route (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    generation_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reader_id           BIGINT       NOT NULL,
    book_id             BIGINT       NOT NULL,
    content_version     VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_purpose  VARCHAR(200) NOT NULL,
    request_type        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    max_additional_ink  INT          NULL,
    depth               VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    completed_at        DATETIME(6)  NULL,
    feedback            VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
    feedback_at         DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uk_ai_reading_route_generation UNIQUE (generation_id),
    CONSTRAINT uk_ai_reading_route_id_book UNIQUE (id, book_id),
    CONSTRAINT uk_ai_reading_route_id_generation UNIQUE (id, generation_id),
    CONSTRAINT uk_ai_reading_route_owner UNIQUE (reader_id, book_id, id),
    CONSTRAINT ck_ai_reading_route_request_type CHECK (
        request_type IN ('INK_BUDGET', 'OWNED_DEPTH')
    ),
    CONSTRAINT ck_ai_reading_route_depth CHECK (
        depth IS NULL OR depth IN ('QUICK', 'BALANCED', 'DEEP')
    ),
    CONSTRAINT ck_ai_reading_route_request_shape CHECK (
        (
            request_type = 'INK_BUDGET'
            AND max_additional_ink IS NOT NULL
            AND max_additional_ink >= 0
            AND depth IS NULL
        )
        OR (
            request_type = 'OWNED_DEPTH'
            AND max_additional_ink IS NULL
            AND depth IS NOT NULL
        )
    ),
    CONSTRAINT ck_ai_reading_route_feedback CHECK (
        feedback IS NULL OR feedback IN ('HELPFUL', 'NEUTRAL', 'NOT_HELPFUL')
    ),
    CONSTRAINT ck_ai_reading_route_feedback_shape CHECK (
        (feedback IS NULL AND feedback_at IS NULL)
        OR (
            feedback IS NOT NULL
            AND feedback_at IS NOT NULL
            AND completed_at IS NOT NULL
        )
    ),
    CONSTRAINT fk_ai_reading_route_reader
        FOREIGN KEY (reader_id) REFERENCES reader (id),
    CONSTRAINT fk_ai_reading_route_book
        FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_reading_route_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id       BIGINT       NOT NULL,
    book_id        BIGINT       NOT NULL,
    book_page_id   BIGINT       NOT NULL,
    position       INT          NOT NULL,
    relevance      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prerequisite   BOOLEAN      NOT NULL,
    role           VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    opened_at      DATETIME(6)  NULL,
    CONSTRAINT uk_ai_reading_route_item_position UNIQUE (route_id, position),
    CONSTRAINT uk_ai_reading_route_item_page UNIQUE (route_id, book_page_id),
    CONSTRAINT ck_ai_reading_route_item_position_positive CHECK (position > 0),
    CONSTRAINT ck_ai_reading_route_item_relevance CHECK (
        relevance IN ('HIGH', 'MEDIUM')
    ),
    CONSTRAINT ck_ai_reading_route_item_prerequisite_boolean CHECK (
        prerequisite IN (FALSE, TRUE)
    ),
    CONSTRAINT ck_ai_reading_route_item_role CHECK (
        role IN ('PREREQUISITE', 'CORE', 'EXAMPLE', 'COUNTERPOINT', 'CONCLUSION')
    ),
    CONSTRAINT fk_ai_reading_route_item_route_book
        FOREIGN KEY (route_id, book_id) REFERENCES ai_reading_route (id, book_id),
    CONSTRAINT fk_ai_reading_route_item_page
        FOREIGN KEY (book_page_id, book_id) REFERENCES book_page (id, book_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_route_current (
    reader_id  BIGINT      NOT NULL,
    book_id    BIGINT      NOT NULL,
    route_id   BIGINT      NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (reader_id, book_id),
    CONSTRAINT uk_ai_route_current_route UNIQUE (route_id),
    CONSTRAINT fk_ai_route_current_route
        FOREIGN KEY (reader_id, book_id, route_id)
        REFERENCES ai_reading_route (reader_id, book_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_route_daily_usage (
    reader_id        BIGINT NOT NULL,
    usage_date       DATE   NOT NULL,
    generation_count INT    NOT NULL,
    PRIMARY KEY (reader_id, usage_date),
    CONSTRAINT ck_ai_route_daily_usage_count_non_negative CHECK (generation_count >= 0),
    CONSTRAINT fk_ai_route_daily_usage_reader
        FOREIGN KEY (reader_id) REFERENCES reader (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE ai_route_generation
    ADD CONSTRAINT fk_ai_route_generation_saved_route
        FOREIGN KEY (saved_route_id, generation_id)
        REFERENCES ai_reading_route (id, generation_id);
