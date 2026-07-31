package com.example.ilgeobolkka.ownership.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookOwnershipTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-01T01:00:00Z");

    @Test
    void 소장을_생성하면_독자_도서_결제를_그대로_보관한다() {
        BookOwnership ownership = BookOwnership.create(1L, 2L, 3L, CREATED_AT);

        assertAll(
                () -> assertEquals(1L, ownership.getReaderId()),
                () -> assertEquals(2L, ownership.getBookId()),
                () -> assertEquals(3L, ownership.getOwnershipPaymentId()),
                () -> assertEquals(CREATED_AT, ownership.getCreatedAt()));
    }

    @Test
    void 소장_생성값은_유효해야_한다() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BookOwnership.create(0L, 2L, 3L, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BookOwnership.create(1L, 0L, 3L, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BookOwnership.create(1L, 2L, 0L, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BookOwnership.create(1L, 2L, 3L, null)));
    }
}
