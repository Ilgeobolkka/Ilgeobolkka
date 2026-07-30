package com.example.ilgeobolkka.ownership.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnershipPaymentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T01:00:00Z");

    @Test
    void 소장_결제를_준비하면_도서_원가와_PENDING_상태로_생성한다() {
        UUID paymentId = UUID.randomUUID();

        OwnershipPayment payment =
                OwnershipPayment.create(1L, 2L, paymentId, 15_000, CREATED_AT);

        assertAll(
                () -> assertEquals(1L, payment.getReaderId()),
                () -> assertEquals(2L, payment.getBookId()),
                () -> assertEquals(paymentId, payment.getPaymentId()),
                () -> assertEquals(OwnershipPaymentStatus.PENDING, payment.getStatus()),
                () -> assertEquals(15_000, payment.getAmountWon()),
                () -> assertEquals(CREATED_AT, payment.getCreatedAt()),
                () -> assertNull(payment.getPaidAt()));
    }

    @Test
    void 소장_결제_생성값은_유효해야_한다() {
        UUID paymentId = UUID.randomUUID();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> OwnershipPayment.create(0L, 2L, paymentId, 15_000, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> OwnershipPayment.create(1L, 0L, paymentId, 15_000, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> OwnershipPayment.create(1L, 2L, null, 15_000, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> OwnershipPayment.create(1L, 2L, paymentId, 0, CREATED_AT)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> OwnershipPayment.create(1L, 2L, paymentId, 15_000, null)));
    }
}
