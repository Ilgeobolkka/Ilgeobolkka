package com.example.ilgeobolkka.reading.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingSessionTest {

    @Test
    void 새_세션을_열면_전달한_책과_페이지_뷰어_ID로_초기화된다() {
        UUID viewerSessionId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-30T00:00:00Z");

        ReadingSession session = ReadingSession.open(1L, 2L, 3, viewerSessionId, openedAt);

        assertEquals(2L, session.getBookId());
        assertEquals(3, session.getCurrentPageNumber());
        assertEquals(viewerSessionId, session.getViewerSessionId());
        assertEquals(openedAt, session.getUpdatedAt());
    }

    @Test
    void 새_뷰어로_교체하면_책과_페이지와_뷰어_ID가_모두_바뀐다() {
        UUID firstViewer = UUID.randomUUID();
        UUID secondViewer = UUID.randomUUID();
        ReadingSession session = ReadingSession.open(
                1L, 2L, 3, firstViewer, Instant.parse("2026-07-30T00:00:00Z"));
        Instant replacedAt = Instant.parse("2026-07-30T01:00:00Z");

        session.replace(5L, 7, secondViewer, replacedAt);

        assertEquals(5L, session.getBookId());
        assertEquals(7, session.getCurrentPageNumber());
        assertEquals(secondViewer, session.getViewerSessionId());
        assertEquals(replacedAt, session.getUpdatedAt());
    }

    @Test
    void 현재_뷰어_ID와_같으면_일치로_판정한다() {
        UUID viewerSessionId = UUID.randomUUID();
        ReadingSession session = ReadingSession.open(
                1L, 2L, 3, viewerSessionId, Instant.parse("2026-07-30T00:00:00Z"));

        assertTrue(session.matchesViewer(viewerSessionId));
    }

    @Test
    void 다른_뷰어_ID면_불일치로_판정한다() {
        ReadingSession session = ReadingSession.open(
                1L, 2L, 3, UUID.randomUUID(), Instant.parse("2026-07-30T00:00:00Z"));

        assertFalse(session.matchesViewer(UUID.randomUUID()));
    }
}
