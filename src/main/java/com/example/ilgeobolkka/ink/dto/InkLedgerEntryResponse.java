package com.example.ilgeobolkka.ink.dto;

import com.example.ilgeobolkka.ink.entity.InkLedgerType;
import com.example.ilgeobolkka.ink.repository.InkLedgerEntryProjection;
import java.time.Instant;

public record InkLedgerEntryResponse(
        InkLedgerType type,
        int amount,
        int balanceAfter,
        String bookTitle,
        Integer pageNumber,
        Instant rentedAt,
        Instant expiresAt,
        Instant occurredAt) {

    public static InkLedgerEntryResponse from(InkLedgerEntryProjection entry) {
        return new InkLedgerEntryResponse(
                entry.getType(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getBookTitle(),
                entry.getPageNumber(),
                entry.getRentedAt(),
                entry.getExpiresAt(),
                entry.getOccurredAt());
    }
}
