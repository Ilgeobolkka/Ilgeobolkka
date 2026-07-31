package com.example.ilgeobolkka.ownership.dto;

import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentEntryProjection;
import java.time.Instant;

public record OwnershipPaymentHistoryEntryResponse(
        String paymentId,
        long bookId,
        String bookTitle,
        int amountWon,
        Instant paidAt,
        boolean owned) {

    public static OwnershipPaymentHistoryEntryResponse from(OwnershipPaymentEntryProjection entry) {
        return new OwnershipPaymentHistoryEntryResponse(
                entry.getPaymentId().toString(),
                entry.getBookId(),
                entry.getBookTitle(),
                entry.getAmountWon(),
                entry.getPaidAt(),
                true);
    }
}
