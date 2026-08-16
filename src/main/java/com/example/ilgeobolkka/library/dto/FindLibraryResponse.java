package com.example.ilgeobolkka.library.dto;

import com.example.ilgeobolkka.library.repository.LibraryEntryView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FindLibraryResponse(List<LibraryEntryResponse> entries) {

    public static FindLibraryResponse from(
            List<LibraryEntryView> rows,
            Instant now,
            boolean aiRouteEnabled) {
        Map<Long, List<LibraryEntryView>> rowsByBook = new LinkedHashMap<>();
        for (LibraryEntryView row : rows) {
            rowsByBook.computeIfAbsent(row.getBookId(), ignored -> new ArrayList<>())
                    .add(row);
        }

        List<LibraryEntryResponse> entries = rowsByBook.values().stream()
                .map(bookRows -> {
                    LibraryEntryView first = bookRows.getFirst();
                    List<LibraryRouteResponse> routes = bookRows.stream()
                            .filter(row -> row.getRouteId() != null)
                            .map(LibraryRouteResponse::from)
                            .toList();
                    Long currentRouteId = bookRows.stream()
                            .map(LibraryEntryView::getCurrentRouteId)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    return LibraryEntryResponse.from(
                            first,
                            now,
                            aiRouteEnabled ? routes : null,
                            aiRouteEnabled ? currentRouteId : null);
                })
                .toList();
        return new FindLibraryResponse(entries);
    }
}
