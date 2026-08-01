package com.example.ilgeobolkka.library.dto;

import com.example.ilgeobolkka.library.repository.LibraryEntryView;
import java.time.Instant;
import java.util.List;

public record FindLibraryResponse(List<LibraryEntryResponse> entries) {

    public static FindLibraryResponse from(List<LibraryEntryView> entries, Instant now) {
        return new FindLibraryResponse(entries.stream()
                .map(entry -> LibraryEntryResponse.from(entry, now))
                .toList());
    }
}
