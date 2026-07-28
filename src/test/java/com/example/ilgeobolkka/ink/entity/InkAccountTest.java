package com.example.ilgeobolkka.ink.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.ink.exception.InkBalanceOverflowException;
import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class InkAccountTest {

    @Test
    void 이용권_지급은_100잉크를_더하고_페이지_대여는_1잉크를_차감한다() {
        InkAccount inkAccount = InkAccount.create(1L);

        assertEquals(100, inkAccount.grantPurchaseInk());
        assertEquals(99, inkAccount.deductPageRentalInk());
        assertEquals(99, inkAccount.getBalance());
    }

    @Test
    void 잔액이_0이면_페이지_대여_잉크를_차감할_수_없다() {
        InkAccount inkAccount = InkAccount.create(1L);

        assertThrows(InsufficientInkException.class, inkAccount::deductPageRentalInk);
        assertEquals(0, inkAccount.getBalance());
    }

    @Test
    void 지급_후_잔액이_INT_범위를_넘으면_도메인_오류가_발생한다()
            throws ReflectiveOperationException {
        InkAccount inkAccount = InkAccount.create(1L);
        Field balanceField = InkAccount.class.getDeclaredField("balance");
        balanceField.setAccessible(true);
        balanceField.setInt(inkAccount, Integer.MAX_VALUE - 99);

        assertThrows(InkBalanceOverflowException.class, inkAccount::grantPurchaseInk);
        assertEquals(Integer.MAX_VALUE - 99, inkAccount.getBalance());
    }
}
