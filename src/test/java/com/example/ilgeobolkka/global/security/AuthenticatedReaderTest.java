package com.example.ilgeobolkka.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AuthenticatedReaderTest {

    @Test
    void 양수_readerId만_노출한다() {
        AuthenticatedReader authenticatedReader = new AuthenticatedReader(1L);

        assertEquals(1L, authenticatedReader.readerId());
    }

    @Test
    void readerId가_0이면_생성할_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedReader(0L));
    }

    @Test
    void readerId가_음수이면_생성할_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedReader(-1L));
    }
}
