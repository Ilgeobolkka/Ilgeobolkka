package com.example.ilgeobolkka.ink.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import org.junit.jupiter.api.Test;

class InkAccountTest {

    @Test
    void 새_계좌는_잔액이_0이다() {
        InkAccount account = InkAccount.create(1L);

        assertEquals(0, account.getBalance());
    }

    @Test
    void 잉크를_지급하면_잔액이_100_증가한다() {
        InkAccount account = InkAccount.create(1L);

        account.grant();

        assertEquals(100, account.getBalance());
    }

    @Test
    void 잉크를_차감하면_잔액이_1_감소한다() {
        InkAccount account = InkAccount.create(1L);
        account.grant();

        account.deduct();

        assertEquals(99, account.getBalance());
    }

    @Test
    void 잔액이_0이면_차감에_실패하고_잔액이_유지된다() {
        InkAccount account = InkAccount.create(1L);

        assertThrows(InsufficientInkException.class, account::deduct);
        assertEquals(0, account.getBalance());
    }
}
