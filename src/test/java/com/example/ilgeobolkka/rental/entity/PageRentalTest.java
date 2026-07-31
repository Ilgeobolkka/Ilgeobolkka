package com.example.ilgeobolkka.rental.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PageRentalTest {

    @Test
    void 대여를_시작하면_만료_시각은_대여_시각의_30일_뒤다() {
        Instant rentedAt = Instant.parse("2026-07-25T14:00:00Z");

        PageRental rental = PageRental.start(1L, 2L, rentedAt);

        assertEquals(rentedAt, rental.getRentedAt());
        assertEquals(Instant.parse("2026-08-24T14:00:00Z"), rental.getExpiresAt());
    }

    @Test
    void 만료_1밀리초_전에는_활성이다() {
        PageRental rental = PageRental.start(1L, 2L, Instant.parse("2026-07-25T14:00:00Z"));

        assertTrue(rental.isActive(Instant.parse("2026-08-24T13:59:59.999Z")));
    }

    @Test
    void 정확한_만료_시각부터는_만료다() {
        PageRental rental = PageRental.start(1L, 2L, Instant.parse("2026-07-25T14:00:00Z"));

        assertFalse(rental.isActive(Instant.parse("2026-08-24T14:00:00Z")));
    }

    @Test
    void 정확한_대여_시각부터는_활성이다() {
        Instant rentedAt = Instant.parse("2026-07-25T14:00:00Z");

        PageRental rental = PageRental.start(1L, 2L, rentedAt);

        assertTrue(rental.isActive(rentedAt));
    }

    @Test
    void 대여_시작_1밀리초_전에는_아직_활성이_아니다() {
        PageRental rental = PageRental.start(1L, 2L, Instant.parse("2026-07-25T14:00:00Z"));

        assertFalse(rental.isActive(Instant.parse("2026-07-25T13:59:59.999Z")));
    }
}
