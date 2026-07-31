package com.example.ilgeobolkka.ownership.repository;

import java.time.Instant;
import java.util.UUID;

public interface OwnershipPaymentEntryProjection {

    UUID getPaymentId();

    Long getBookId();

    String getBookTitle();

    int getAmountWon();

    Instant getPaidAt();
}
