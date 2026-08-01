package com.example.ilgeobolkka.library.dto;

import com.example.ilgeobolkka.library.repository.LibraryEntryView;
import java.time.Instant;

public record LibraryEntryResponse(
        long bookId,
        String coverImagePath,
        String title,
        String category,
        int lastPageNumber,
        Instant rentedAt,
        Instant expiresAt,
        Boolean activeRental,
        boolean owned) {

    public static LibraryEntryResponse from(LibraryEntryView entry, Instant now) {
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
                    true);
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
                false);
    }
}
