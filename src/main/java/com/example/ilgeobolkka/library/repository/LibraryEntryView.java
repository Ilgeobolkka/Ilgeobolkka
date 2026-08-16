package com.example.ilgeobolkka.library.repository;

import java.time.Instant;

public interface LibraryEntryView {

    Long getLibraryEntryId();

    Long getBookId();

    String getCoverImagePath();

    String getTitle();

    String getCategory();

    int getLastPageNumber();

    Instant getRentedAt();

    Instant getExpiresAt();

    Long getOwnershipId();

    Long getRouteId();

    String getRoutePurpose();

    Long getCurrentRouteId();
}
