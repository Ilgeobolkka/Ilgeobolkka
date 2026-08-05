package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkLedgerType;
import java.time.Instant;

public interface InkLedgerEntryProjection {

    InkLedgerType getType();

    int getAmount();

    int getBalanceAfter();

    String getBookTitle();

    Integer getPageNumber();

    Instant getRentedAt();

    Instant getExpiresAt();

    Instant getOccurredAt();
}
