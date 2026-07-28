package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkLedgerType;
import java.time.Instant;

public record InkLedgerEntryQuery(
        InkLedgerType type,
        int amount,
        int balanceAfter,
        String bookTitle,
        Integer pageNumber,
        Instant rentedAt,
        Instant expiresAt,
        Instant occurredAt) {
}
