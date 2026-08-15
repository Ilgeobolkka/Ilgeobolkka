package com.example.ilgeobolkka.library.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.ilgeobolkka.library.repository.LibraryEntryView;
import java.time.Instant;
import java.util.List;

public record LibraryEntryResponse(
        long bookId,
        String coverImagePath,
        String title,
        String category,
        int lastPageNumber,
        Instant rentedAt,
        Instant expiresAt,
        Boolean activeRental,
        boolean owned,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<LibraryRouteResponse> routes,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long currentRouteId) {

    public static LibraryEntryResponse from(
            LibraryEntryView entry,
            Instant now,
            List<LibraryRouteResponse> routes,
            Long currentRouteId) {
        if (entry.getLibraryEntryId() == null) {
            return new LibraryEntryResponse(
                    entry.getBookId(),
                    entry.getCoverImagePath(),
                    entry.getTitle(),
                    entry.getCategory(),
                    entry.getLastPageNumber(),
                    null,
                    null,
                    null,
                    false,
                    routes,
                    currentRouteId);
        }

        boolean owned = entry.getOwnershipId() != null;
        if (owned) {
            return new LibraryEntryResponse(
                    entry.getBookId(),
                    entry.getCoverImagePath(),
                    entry.getTitle(),
                    entry.getCategory(),
                    entry.getLastPageNumber(),
                    null,
                    null,
                    null,
                    true,
                    routes,
                    currentRouteId);
        }

        Instant rentedAt = entry.getRentedAt();
        Instant expiresAt = entry.getExpiresAt();
        boolean activeRental = !now.isBefore(rentedAt) && now.isBefore(expiresAt);
        return new LibraryEntryResponse(
                entry.getBookId(),
                entry.getCoverImagePath(),
                entry.getTitle(),
                entry.getCategory(),
                entry.getLastPageNumber(),
                rentedAt,
                expiresAt,
                activeRental,
                false,
                routes,
                currentRouteId);
    }
}
