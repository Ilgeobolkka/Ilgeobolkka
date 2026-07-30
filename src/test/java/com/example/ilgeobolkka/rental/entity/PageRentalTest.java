package com.example.ilgeobolkka.rental.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PageRentalTest {

    /** 정책 "대여 기간은 차감이 완료된 서버 시각부터 30일"을 확인한다. */
    @Test
    void 대여를_생성하면_만료_시각은_대여_시각으로부터_30일_뒤이다() {
        Instant rentedAt = Instant.parse("2026-07-30T00:00:00.000000Z");

        PageRental rental = PageRental.rent(1L, 2L, rentedAt);

        assertEquals(rentedAt, rental.getRentedAt());
        assertEquals(rentedAt.plus(Duration.ofDays(30)), rental.getExpiresAt());
    }
}
