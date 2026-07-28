package com.example.ilgeobolkka.ink.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.ink.exception.InvalidInkLedgerException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InkLedgerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-28T10:00:00Z");

    @Test
    void 지급_원장은_100잉크와_구매_원인만_기록한다() {
        InkLedger ledger = InkLedger.grant(1L, 10L, 100, OCCURRED_AT);

        assertEquals(InkLedgerType.GRANT, ledger.getType());
        assertEquals(100, ledger.getAmount());
        assertEquals(100, ledger.getBalanceAfter());
        assertEquals(10L, ledger.getInkPurchaseId());
        assertNull(ledger.getPageRentalId());
        assertEquals(OCCURRED_AT, ledger.getOccurredAt());
    }

    @Test
    void 차감_원장은_1잉크와_대여_원인만_기록한다() {
        InkLedger ledger = InkLedger.deduct(1L, 20L, 99, OCCURRED_AT);

        assertEquals(InkLedgerType.DEDUCTION, ledger.getType());
        assertEquals(1, ledger.getAmount());
        assertEquals(99, ledger.getBalanceAfter());
        assertNull(ledger.getInkPurchaseId());
        assertEquals(20L, ledger.getPageRentalId());
        assertEquals(OCCURRED_AT, ledger.getOccurredAt());
    }

    @Test
    void 생성된_원장을_바꾸는_setter를_노출하지_않는다() {
        boolean hasSetter = Arrays.stream(InkLedger.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.startsWith("set"));

        assertFalse(hasSetter);
    }

    @Test
    void 음수_차감_후_잔액으로_원장을_생성할_수_없다() {
        InvalidInkLedgerException exception = assertThrows(
                InvalidInkLedgerException.class,
                () -> InkLedger.deduct(1L, 20L, -1, OCCURRED_AT));

        assertEquals("잉크 잔액은 0 이상이어야 합니다.", exception.getMessage());
    }

    @Test
    void 발생_시각이_없으면_원장을_생성할_수_없다() {
        InvalidInkLedgerException exception = assertThrows(
                InvalidInkLedgerException.class,
                () -> InkLedger.grant(1L, 10L, 100, null));

        assertEquals("잉크 변경 시각은 필수입니다.", exception.getMessage());
    }

    @Test
    void INV_010_잉크_계좌와_원장에는_만료_필드를_두지_않는다() {
        boolean hasExpirationField = Stream.concat(
                        Arrays.stream(InkAccount.class.getDeclaredFields()),
                        Arrays.stream(InkLedger.class.getDeclaredFields()))
                .map(Field::getName)
                .map(String::toLowerCase)
                .anyMatch(name -> name.contains("expire"));

        assertFalse(hasExpirationField);
    }
}
