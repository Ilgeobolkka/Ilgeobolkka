package com.example.ilgeobolkka.library.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LibraryEntryTest {

    @Test
    void 새_서재_항목은_전달한_마지막_페이지로_생성된다() {
        Instant updatedAt = Instant.parse("2026-07-30T00:00:00Z");

        LibraryEntry entry = LibraryEntry.create(1L, 2L, 3, updatedAt);

        assertEquals(3, entry.getLastPageNumber());
        assertEquals(updatedAt, entry.getUpdatedAt());
    }

    @Test
    void 마지막_위치를_옮기면_페이지와_갱신_시각이_바뀐다() {
        LibraryEntry entry = LibraryEntry.create(1L, 2L, 3, Instant.parse("2026-07-30T00:00:00Z"));
        Instant movedAt = Instant.parse("2026-07-30T01:00:00Z");

        entry.moveTo(9, movedAt);

        assertEquals(9, entry.getLastPageNumber());
        assertEquals(movedAt, entry.getUpdatedAt());
    }
}
