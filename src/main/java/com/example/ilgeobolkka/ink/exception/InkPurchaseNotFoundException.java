package com.example.ilgeobolkka.ink.exception;

import java.util.UUID;

public class InkPurchaseNotFoundException extends RuntimeException {

    public InkPurchaseNotFoundException(UUID paymentId) {
        super("잉크 구매를 찾을 수 없습니다. paymentId=" + paymentId);
    }
}
