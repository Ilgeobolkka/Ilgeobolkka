package com.example.ilgeobolkka.ink.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InkPurchaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-30T01:00:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-07-30T01:01:00Z");

    @Test
    void 잉크_구매를_준비하면_고정_상품과_PENDING_상태로_생성한다() {
        UUID paymentId = UUID.randomUUID();

        InkPurchase purchase = InkPurchase.create(1L, paymentId, CREATED_AT);

        assertAll(
                () -> assertEquals(1L, purchase.getReaderId()),
                () -> assertEquals(paymentId, purchase.getPaymentId()),
                () -> assertEquals(InkPurchaseStatus.PENDING, purchase.getStatus()),
                () -> assertEquals(1_000, purchase.getAmountWon()),
                () -> assertEquals(100, purchase.getGrantedInk()),
                () -> assertEquals(CREATED_AT, purchase.getCreatedAt()),
                () -> assertNull(purchase.getPaidAt()));
    }

    @Test
    void 결제_완료로_전이하면_완료_시각을_기록한다() {
        InkPurchase purchase = InkPurchase.create(1L, UUID.randomUUID(), CREATED_AT);

        purchase.markPaid(PAID_AT);

        assertAll(
                () -> assertEquals(InkPurchaseStatus.PAID, purchase.getStatus()),
                () -> assertEquals(PAID_AT, purchase.getPaidAt()));
    }

    @Test
    void 결제_실패로_전이하면_완료_시각은_없다() {
        InkPurchase purchase = InkPurchase.create(1L, UUID.randomUUID(), CREATED_AT);

        purchase.markFailed();

        assertAll(
                () -> assertEquals(InkPurchaseStatus.FAILED, purchase.getStatus()),
                () -> assertNull(purchase.getPaidAt()));
    }

    @Test
    void 최종_상태는_다시_변경할_수_없다() {
        InkPurchase paid = InkPurchase.create(1L, UUID.randomUUID(), CREATED_AT);
        paid.markPaid(PAID_AT);
        InkPurchase failed = InkPurchase.create(1L, UUID.randomUUID(), CREATED_AT);
        failed.markFailed();

        assertAll(
                () -> assertThrows(IllegalStateException.class, paid::markFailed),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> failed.markPaid(PAID_AT)));
    }
}
