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
    private static final Instant PAID_AT = Instant.parse("2026-07-31T01:01:00Z");

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

    @Test
    void 결제_완료로_전이하면_완료_시각을_기록한다() {
        OwnershipPayment payment =
                OwnershipPayment.create(1L, 2L, UUID.randomUUID(), 15_000, CREATED_AT);

        payment.markPaid(PAID_AT);

        assertAll(
                () -> assertEquals(OwnershipPaymentStatus.PAID, payment.getStatus()),
                () -> assertEquals(PAID_AT, payment.getPaidAt()));
    }

    @Test
    void 결제_실패로_전이하면_완료_시각은_없다() {
        OwnershipPayment payment =
                OwnershipPayment.create(1L, 2L, UUID.randomUUID(), 15_000, CREATED_AT);

        payment.markFailed();

        assertAll(
                () -> assertEquals(OwnershipPaymentStatus.FAILED, payment.getStatus()),
                () -> assertNull(payment.getPaidAt()));
    }

    @Test
    void 최종_상태는_다시_변경할_수_없다() {
        OwnershipPayment paid =
                OwnershipPayment.create(1L, 2L, UUID.randomUUID(), 15_000, CREATED_AT);
        paid.markPaid(PAID_AT);
        OwnershipPayment failed =
                OwnershipPayment.create(1L, 2L, UUID.randomUUID(), 15_000, CREATED_AT);
        failed.markFailed();

        assertAll(
                () -> assertThrows(IllegalStateException.class, paid::markFailed),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> failed.markPaid(PAID_AT)));
    }
}
