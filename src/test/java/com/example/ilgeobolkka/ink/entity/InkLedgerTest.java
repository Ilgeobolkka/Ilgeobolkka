package com.example.ilgeobolkka.ink.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InkLedgerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T07:00:00Z");

    @Test
    void 지급_원장은_구매만_원인으로_기록한다() {
        InkLedger ledger = InkLedger.grant(1L, 2L, 100, OCCURRED_AT);

        assertAll(
                () -> assertEquals(1L, ledger.getReaderId()),
                () -> assertEquals(InkLedgerType.GRANT, ledger.getType()),
                () -> assertEquals(100, ledger.getAmount()),
                () -> assertEquals(100, ledger.getBalanceAfter()),
                () -> assertEquals(2L, ledger.getInkPurchaseId()),
                () -> assertNull(ledger.getPageRentalId()),
                () -> assertEquals(OCCURRED_AT, ledger.getOccurredAt()));
    }

    @Test
    void 차감_원장은_대여만_원인으로_기록한다() {
        InkLedger ledger = InkLedger.deduction(1L, 3L, 99, OCCURRED_AT);

        assertAll(
                () -> assertEquals(1L, ledger.getReaderId()),
                () -> assertEquals(InkLedgerType.DEDUCTION, ledger.getType()),
                () -> assertEquals(1, ledger.getAmount()),
                () -> assertEquals(99, ledger.getBalanceAfter()),
                () -> assertNull(ledger.getInkPurchaseId()),
                () -> assertEquals(3L, ledger.getPageRentalId()),
                () -> assertEquals(OCCURRED_AT, ledger.getOccurredAt()));
    }

    @Test
    void 반영_후_잔액이_음수인_원장은_생성할_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> InkLedger.deduction(1L, 3L, -1, OCCURRED_AT));
    }
}
