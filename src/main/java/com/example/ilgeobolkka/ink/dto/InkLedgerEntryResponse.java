package com.example.ilgeobolkka.ink.dto;

import com.example.ilgeobolkka.ink.entity.InkLedgerType;
import com.example.ilgeobolkka.ink.repository.InkLedgerEntryQuery;
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

    public static InkLedgerEntryResponse from(InkLedgerEntryQuery entry) {
        return new InkLedgerEntryResponse(
                entry.type(),
                entry.amount(),
                entry.balanceAfter(),
                entry.bookTitle(),
                entry.pageNumber(),
                entry.rentedAt(),
                entry.expiresAt(),
                entry.occurredAt());
    }
}
