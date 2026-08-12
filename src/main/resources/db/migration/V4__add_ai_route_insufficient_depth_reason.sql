ALTER TABLE ai_route_generation
    DROP CHECK ck_ai_route_generation_no_route_shape;

ALTER TABLE ai_route_generation
    DROP CHECK ck_ai_route_generation_no_route_reason;

ALTER TABLE ai_route_generation
    ADD CONSTRAINT ck_ai_route_generation_no_route_reason CHECK (
        no_route_reason IS NULL
        OR no_route_reason IN (
            'NO_RELEVANT_PAGES',
            'INSUFFICIENT_BUDGET',
            'INSUFFICIENT_DEPTH'
        )
    );

ALTER TABLE ai_route_generation
    ADD CONSTRAINT ck_ai_route_generation_no_route_shape CHECK (
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
                OR (
                    no_route_reason = 'INSUFFICIENT_DEPTH'
                    AND request_type = 'OWNED_DEPTH'
                    AND minimum_required_ink IS NULL
                )
            )
        )
        OR (
            status <> 'NO_ROUTE'
            AND no_route_reason IS NULL
            AND minimum_required_ink IS NULL
        )
    );
